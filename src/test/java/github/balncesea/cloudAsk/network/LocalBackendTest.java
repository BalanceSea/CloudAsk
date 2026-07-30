package github.balncesea.cloudAsk.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import github.balncesea.cloudAsk.model.Question;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocalBackendTest {
    @Test
    void concurrentCorrectAnswersProduceOneWinner() {
        LocalBackend backend = new LocalBackend();
        AtomicInteger answeredEvents = new AtomicInteger();
        backend.start(event -> {
            if (event.type() == github.balncesea.cloudAsk.model.NetworkEvent.Type.ANSWERED) {
                answeredEvents.incrementAndGet();
            }
        });
        Question question = question();
        assertEquals(NetworkBackend.StartResult.STARTED, backend.startQuestion(question).join());

        List<CompletableFuture<NetworkBackend.AnswerResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            int number = i;
            attempts.add(CompletableFuture.supplyAsync(() -> backend.submitAnswer(
                    question.id(), "42", "uuid-" + number, "player-" + number, "server", "instance").join()));
        }
        CompletableFuture.allOf(attempts.toArray(CompletableFuture[]::new)).join();

        long winners = attempts.stream().filter(future -> future.join() == NetworkBackend.AnswerResult.WON).count();
        assertEquals(1, winners);
        assertEquals(1, answeredEvents.get());
    }

    @Test
    void rejectsSecondQuestionWhileOneIsActive() {
        LocalBackend backend = new LocalBackend();
        backend.start(event -> { });
        assertEquals(NetworkBackend.StartResult.STARTED, backend.startQuestion(question()).join());
        assertEquals(NetworkBackend.StartResult.BUSY, backend.startQuestion(question()).join());
    }

    private static Question question() {
        long now = System.currentTimeMillis();
        return new Question("question-id", "六乘七等于多少？", "42", "42", "uuid", "asker", "server",
                List.of("give {player} diamond 2"), now,
                now + 60_000);
    }
}
