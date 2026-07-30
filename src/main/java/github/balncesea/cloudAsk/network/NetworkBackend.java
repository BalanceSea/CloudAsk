package github.balncesea.cloudAsk.network;

import github.balncesea.cloudAsk.model.NetworkEvent;
import github.balncesea.cloudAsk.model.Question;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface NetworkBackend extends AutoCloseable {
    void start(Consumer<NetworkEvent> eventHandler);

    CompletableFuture<StartResult> startQuestion(Question question);

    CompletableFuture<AnswerResult> submitAnswer(
            String questionId, String normalizedAnswer, String playerUuid, String playerName,
            String serverId, String instanceId);

    CompletableFuture<Boolean> cancelQuestion(String questionId);

    CompletableFuture<Optional<Question>> currentQuestion();

    @Override
    void close();

    enum StartResult {
        STARTED,
        BUSY
    }

    enum AnswerResult {
        WON,
        INCORRECT,
        ALREADY_ANSWERED,
        STALE
    }
}
