package github.balncesea.cloudAsk.network;

import github.balncesea.cloudAsk.model.NetworkEvent;
import github.balncesea.cloudAsk.model.Question;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class LocalBackend implements NetworkBackend {
    private Question active;
    private Consumer<NetworkEvent> eventHandler = event -> { };

    @Override
    public synchronized void start(Consumer<NetworkEvent> eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    public synchronized CompletableFuture<StartResult> startQuestion(Question question) {
        clearExpired();
        if (active != null) {
            return CompletableFuture.completedFuture(StartResult.BUSY);
        }
        active = question;
        eventHandler.accept(NetworkEvent.started(question));
        return CompletableFuture.completedFuture(StartResult.STARTED);
    }

    @Override
    public synchronized CompletableFuture<AnswerResult> submitAnswer(
            String questionId, String normalizedAnswer, String playerUuid, String playerName,
            String serverId, String instanceId) {
        clearExpired();
        if (active == null || !active.id().equals(questionId)) {
            return CompletableFuture.completedFuture(AnswerResult.STALE);
        }
        if (!active.normalizedAnswer().equals(normalizedAnswer)) {
            return CompletableFuture.completedFuture(AnswerResult.INCORRECT);
        }

        Question answeredQuestion = active;
        active = null;
        eventHandler.accept(NetworkEvent.answered(
                answeredQuestion, playerUuid, playerName, serverId, instanceId, System.currentTimeMillis()));
        return CompletableFuture.completedFuture(AnswerResult.WON);
    }

    @Override
    public synchronized CompletableFuture<Boolean> cancelQuestion(String questionId) {
        clearExpired();
        if (active == null || !active.id().equals(questionId)) {
            return CompletableFuture.completedFuture(false);
        }
        Question cancelledQuestion = active;
        active = null;
        eventHandler.accept(NetworkEvent.cancelled(cancelledQuestion));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Optional<Question>> currentQuestion() {
        clearExpired();
        return CompletableFuture.completedFuture(Optional.ofNullable(active));
    }

    private void clearExpired() {
        if (active != null && active.expiresAt() <= System.currentTimeMillis()) {
            active = null;
        }
    }

    @Override
    public void close() {
        // No external resources.
    }
}
