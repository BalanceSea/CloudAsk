package github.balncesea.cloudAsk.service;

import github.balncesea.cloudAsk.model.NetworkEvent;
import github.balncesea.cloudAsk.model.Question;
import github.balncesea.cloudAsk.network.NetworkBackend;
import github.balncesea.cloudAsk.util.AnswerNormalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class QuestionService {
    private static final int MAX_REMEMBERED_RESULTS = 256;

    private final JavaPlugin plugin;
    private final NetworkBackend backend;
    private final MessageService messages;
    private final RewardService rewards;
    private final String serverId;
    private final String instanceId = UUID.randomUUID().toString();
    private final boolean ignoreCase;
    private final boolean trimAnswers;
    private final boolean collapseSpaces;
    private final boolean cancelCorrectAnswerMessage;
    private final Set<String> completedQuestionIds = new LinkedHashSet<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile Question active;
    private BukkitTask expirationTask;

    public QuestionService(JavaPlugin plugin, NetworkBackend backend, MessageService messages, String serverId) {
        this.plugin = plugin;
        this.backend = backend;
        this.messages = messages;
        this.rewards = new RewardService(plugin);
        this.serverId = serverId;
        this.ignoreCase = plugin.getConfig().getBoolean("question.ignore-case", true);
        this.trimAnswers = plugin.getConfig().getBoolean("question.trim", true);
        this.collapseSpaces = plugin.getConfig().getBoolean("question.collapse-spaces", true);
        this.cancelCorrectAnswerMessage =
                plugin.getConfig().getBoolean("question.cancel-correct-answer-message", true);
    }

    public void start() {
        backend.start(this::acceptNetworkEvent);
        backend.currentQuestion().whenComplete((question, error) -> {
            if (error != null) {
                logBackendError("读取当前问题失败", error);
                return;
            }
            question.ifPresent(value -> runMain(() -> applyStarted(value)));
        });
    }

    public void ask(CommandSender sender, String answer, String questionText) {
        String askerUuid = sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE";
        publish(sender, answer, questionText, askerUuid, sender.getName(),
                plugin.getConfig().getStringList("rewards.commands"), true);
    }

    public void askAutomatically(
            String answer, String questionText, String askerName, List<String> rewardCommands) {
        publish(Bukkit.getConsoleSender(), answer, questionText, "AUTOMATIC", askerName, rewardCommands, false);
    }

    private void publish(
            CommandSender sender,
            String answer,
            String questionText,
            String askerUuid,
            String askerName,
            List<String> rewardCommands,
            boolean sendFeedback) {
        long now = System.currentTimeMillis();
        long timeoutSeconds = Math.max(5L, plugin.getConfig().getLong("question.timeout-seconds", 60L));
        String normalized = normalize(answer);
        if (normalized.isBlank()) {
            if (sendFeedback) {
                messages.send(sender, "usage");
            }
            return;
        }

        Question question = new Question(
                UUID.randomUUID().toString(), questionText, answer, normalized, askerUuid,
                askerName, serverId, rewardCommands, now, now + timeoutSeconds * 1000L);

        backend.startQuestion(question).whenComplete((result, error) -> runMain(() -> {
            if (error != null) {
                if (sendFeedback) {
                    messages.send(sender, "backend-error");
                }
                logBackendError("发布问题失败", error);
                return;
            }
            if (result == NetworkBackend.StartResult.STARTED) {
                applyStarted(question);
                if (sendFeedback) {
                    messages.send(sender, "started");
                }
            } else if (sendFeedback) {
                Question current = active;
                messages.send(sender, "busy", Map.of(
                        "question", current == null ? "（正在同步）" : current.text()));
            }
        }));
    }

    public boolean handleChatAnswer(Player player, String message) {
        Question current = active;
        if (current == null || current.expiresAt() <= System.currentTimeMillis()
                || !player.hasPermission("cloudask.answer")) {
            return false;
        }
        String normalized = normalize(message);
        if (!current.normalizedAnswer().equals(normalized)) {
            return false;
        }

        backend.submitAnswer(
                        current.id(), normalized, player.getUniqueId().toString(), player.getName(), serverId,
                        instanceId)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        runMain(() -> messages.send(player, "backend-error"));
                        logBackendError("提交答案失败", error);
                        return;
                    }
                    if (result == NetworkBackend.AnswerResult.WON) {
                        NetworkEvent event = NetworkEvent.answered(
                                current, player.getUniqueId().toString(), player.getName(), serverId, instanceId,
                                System.currentTimeMillis());
                        runMain(() -> applyAnswered(event));
                    }
                });
        return cancelCorrectAnswerMessage;
    }

    public void stop(CommandSender sender) {
        Question current = active;
        if (current == null) {
            messages.send(sender, "no-active");
            return;
        }
        backend.cancelQuestion(current.id()).whenComplete((cancelled, error) -> runMain(() -> {
            if (error != null) {
                messages.send(sender, "backend-error");
                logBackendError("停止问题失败", error);
            } else if (cancelled) {
                applyCancelled(NetworkEvent.cancelled(current));
                messages.send(sender, "stopped");
            } else {
                messages.send(sender, "no-active");
            }
        }));
    }

    public void status(CommandSender sender) {
        Question current = active;
        if (current == null || current.expiresAt() <= System.currentTimeMillis()) {
            messages.send(sender, "no-active");
            return;
        }
        long remaining = Math.max(0, (current.expiresAt() - System.currentTimeMillis() + 999) / 1000);
        messages.send(sender, "status", Map.of(
                "question", current.text(),
                "seconds", Long.toString(remaining),
                "server", current.sourceServer()));
    }

    private void acceptNetworkEvent(NetworkEvent event) {
        if (event == null || event.type() == null || event.question() == null) {
            return;
        }
        runMain(() -> {
            switch (event.type()) {
                case STARTED -> applyStarted(event.question());
                case ANSWERED -> applyAnswered(event);
                case CANCELLED -> applyCancelled(event);
            }
        });
    }

    private void applyStarted(Question question) {
        if (!running.get() || completedQuestionIds.contains(question.id())) {
            return;
        }
        Question current = active;
        if (current != null && current.id().equals(question.id())) {
            return;
        }
        active = question;
        scheduleExpiration(question);
        long seconds = Math.max(0, (question.expiresAt() - System.currentTimeMillis() + 999) / 1000);
        messages.broadcastQuestion(Map.of(
                "question", question.text(),
                "seconds", Long.toString(seconds),
                "server", question.sourceServer(),
                "asker", question.askerName()));
    }

    private void applyAnswered(NetworkEvent event) {
        Question question = event.question();
        if (!rememberCompleted(question.id())) {
            return;
        }
        clearActive(question.id());
        double elapsed = Math.max(0, event.occurredAt() - question.createdAt()) / 1000.0;
        messages.broadcast("answered", Map.of(
                "player", event.winnerName(),
                "answer", question.answer(),
                "question", question.text(),
                "server", event.winnerServer(),
                "elapsed", String.format(java.util.Locale.ROOT, "%.1f", elapsed)));
        if (instanceId.equals(event.winnerInstanceId())) {
            rewards.execute(event);
        }
    }

    private void applyCancelled(NetworkEvent event) {
        if (!rememberCompleted(event.question().id())) {
            return;
        }
        clearActive(event.question().id());
        messages.broadcast("cancelled", Map.of());
    }

    private void scheduleExpiration(Question question) {
        cancelExpirationTask();
        long delayMillis = Math.max(50L, question.expiresAt() - System.currentTimeMillis() + 500L);
        long delayTicks = Math.max(1L, (delayMillis + 49L) / 50L);
        expirationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Question current = active;
            if (current == null || !current.id().equals(question.id())) {
                return;
            }
            if (current.expiresAt() > System.currentTimeMillis()) {
                scheduleExpiration(current);
                return;
            }
            active = null;
            messages.broadcast("expired", Map.of(
                    "answer", current.answer(),
                    "question", current.text()));
        }, delayTicks);
    }

    private void clearActive(String questionId) {
        Question current = active;
        if (current != null && current.id().equals(questionId)) {
            active = null;
            cancelExpirationTask();
        }
    }

    private boolean rememberCompleted(String questionId) {
        if (!completedQuestionIds.add(questionId)) {
            return false;
        }
        if (completedQuestionIds.size() > MAX_REMEMBERED_RESULTS) {
            String oldest = completedQuestionIds.iterator().next();
            completedQuestionIds.remove(oldest);
        }
        return true;
    }

    private String normalize(String value) {
        return AnswerNormalizer.normalize(
                value,
                ignoreCase,
                trimAnswers,
                collapseSpaces);
    }

    private void runMain(Runnable runnable) {
        if (!running.get()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private void logBackendError(String message, Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        plugin.getLogger().log(Level.WARNING, message + ": " + cause.getMessage(), cause);
    }

    private void cancelExpirationTask() {
        if (expirationTask != null) {
            expirationTask.cancel();
            expirationTask = null;
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        cancelExpirationTask();
        backend.close();
    }

    public Optional<Question> activeQuestion() {
        return Optional.ofNullable(active);
    }
}
