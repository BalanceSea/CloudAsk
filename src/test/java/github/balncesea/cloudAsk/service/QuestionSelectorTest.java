package github.balncesea.cloudAsk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionSelectorTest {
    @Test
    void sequentialSelectionLoopsInConfigurationOrder() {
        var first = new QuestionSelector.Entry("q1", "a1", List.of("reward one"));
        var second = new QuestionSelector.Entry("q2", "a2", List.of("reward two"));
        QuestionSelector selector = new QuestionSelector(List.of(first, second), "sequential");

        assertEquals(first, selector.next());
        assertEquals(second, selector.next());
        assertEquals(first, selector.next());
    }

    @Test
    void randomSelectionAvoidsImmediateRepetition() {
        QuestionSelector selector = new QuestionSelector(List.of(
                new QuestionSelector.Entry("q1", "a1", List.of()),
                new QuestionSelector.Entry("q2", "a2", List.of()),
                new QuestionSelector.Entry("q3", "a3", List.of())), "random");

        var previous = selector.next();
        for (int i = 0; i < 50; i++) {
            var current = selector.next();
            assertNotEquals(previous, current);
            previous = current;
        }
    }
}
