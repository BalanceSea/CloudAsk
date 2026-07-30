package github.balncesea.cloudAsk.service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class QuestionSelector {
    private final List<Entry> questions;
    private final boolean sequential;
    private int nextIndex;
    private int previousIndex = -1;

    public QuestionSelector(List<Entry> questions, String order) {
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("questions cannot be empty");
        }
        this.questions = List.copyOf(questions);
        this.sequential = "sequential".equals(order == null ? "" : order.toLowerCase(Locale.ROOT));
    }

    public Entry next() {
        int selected;
        if (sequential) {
            selected = nextIndex;
            nextIndex = (nextIndex + 1) % questions.size();
        } else if (questions.size() == 1) {
            selected = 0;
        } else {
            do {
                selected = ThreadLocalRandom.current().nextInt(questions.size());
            } while (selected == previousIndex);
        }
        previousIndex = selected;
        return questions.get(selected);
    }

    public record Entry(String question, String answer, List<String> rewardCommands) {
        public Entry {
            rewardCommands = rewardCommands == null ? List.of() : List.copyOf(rewardCommands);
        }
    }
}
