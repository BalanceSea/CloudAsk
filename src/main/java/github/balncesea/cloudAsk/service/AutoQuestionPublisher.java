package github.balncesea.cloudAsk.service;

import github.balncesea.cloudAsk.CloudAsk;
import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

public final class AutoQuestionPublisher {
    private final CloudAsk plugin;
    private BukkitTask task;
    private QuestionSelector selector;
    private CronSchedule cronSchedule;
    private boolean cronMode;
    private long nextAttemptAt;
    private long intervalMillis;
    private long busyRetryMillis;
    private String askerName;

    public AutoQuestionPublisher(CloudAsk plugin) {
        this.plugin = plugin;
    }

    public void start() {
        File file = new File(plugin.getDataFolder(), "automatic.yml");
        if (!file.exists()) {
            plugin.saveResource("automatic.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.getBoolean("enabled", false)) {
            return;
        }

        List<QuestionSelector.Entry> questions = loadQuestions(config);
        if (questions.isEmpty()) {
            plugin.getLogger().warning("已启用自动问答，但 automatic.yml 的 questions 中没有有效题目。");
            return;
        }

        selector = new QuestionSelector(questions, config.getString("order", "random"));
        busyRetryMillis = Math.max(1L, config.getLong("schedule.retry-when-busy-seconds", 10L)) * 1000L;
        askerName = config.getString("asker-name", "自动问答");
        if (askerName == null || askerName.isBlank()) {
            askerName = "自动问答";
        }

        String mode = config.getString("schedule.mode", "interval");
        cronMode = mode != null && mode.toLowerCase(Locale.ROOT).equals("cron");
        if (cronMode) {
            if (!configureCron(config)) {
                return;
            }
        } else {
            if (mode != null && !mode.equalsIgnoreCase("interval")) {
                plugin.getLogger().warning("未知自动调度模式 '" + mode + "'，已使用 interval。");
            }
            long initialDelay = Math.max(1L, config.getLong("schedule.initial-delay-seconds", 30L));
            intervalMillis = Math.max(5L, config.getLong("schedule.interval-seconds", 300L)) * 1000L;
            nextAttemptAt = System.currentTimeMillis() + initialDelay * 1000L;
        }

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("自动问答已启用，模式: " + (cronMode ? "cron" : "interval")
                + "，已加载 " + questions.size() + " 道题目。");
    }

    private boolean configureCron(YamlConfiguration config) {
        String expression = config.getString("schedule.cron", "0 0/5 * * * ?");
        String timezone = config.getString("schedule.timezone", "Asia/Shanghai");
        try {
            cronSchedule = new CronSchedule(expression, ZoneId.of(timezone));
            OptionalLong next = cronSchedule.nextAfter(System.currentTimeMillis());
            if (next.isEmpty()) {
                plugin.getLogger().severe("Cron 表达式没有下一次执行时间，自动问答未启动: " + expression);
                return false;
            }
            nextAttemptAt = next.getAsLong();
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "automatic.yml 中的 Cron 表达式或时区无效，自动问答未启动: " + ex.getMessage(), ex);
            return false;
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }
        if (plugin.questions().activeQuestion().isPresent()) {
            nextAttemptAt = now + busyRetryMillis;
            return;
        }

        QuestionSelector.Entry selected = selector.next();
        plugin.questions().askAutomatically(
                selected.answer(), selected.question(), askerName, selected.rewardCommands());
        if (cronMode) {
            OptionalLong next = cronSchedule.nextAfter(now);
            nextAttemptAt = next.orElse(Long.MAX_VALUE);
        } else {
            nextAttemptAt = now + intervalMillis;
        }
    }

    private List<QuestionSelector.Entry> loadQuestions(YamlConfiguration config) {
        List<QuestionSelector.Entry> result = new ArrayList<>();
        for (Map<?, ?> item : config.getMapList("questions")) {
            Object questionValue = item.get("question");
            Object answerValue = item.get("answer");
            if (questionValue == null || answerValue == null) {
                continue;
            }
            String question = questionValue.toString().trim();
            String answer = answerValue.toString().trim();
            if (!question.isBlank() && !answer.isBlank()) {
                result.add(new QuestionSelector.Entry(question, answer, readRewardCommands(item.get("rewards"))));
            }
        }
        return result;
    }

    private List<String> readRewardCommands(Object configuredRewards) {
        if (!(configuredRewards instanceof List<?> values)) {
            return List.of();
        }
        List<String> commands = new ArrayList<>();
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                commands.add(value.toString().trim());
            }
        }
        return commands;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
