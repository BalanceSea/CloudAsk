package github.balncesea.cloudAsk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionTest {
    @Test
    void keepsAnImmutablePerQuestionRewardList() {
        List<String> configuredRewards = new ArrayList<>(List.of("give {player} diamond 2"));
        Question question = new Question(
                "id", "问题", "答案", "答案", "uuid", "asker", "server",
                configuredRewards, 1000L, 2000L);

        configuredRewards.add("eco give {player} 100");

        assertEquals(List.of("give {player} diamond 2"), question.rewardCommands());
        assertThrows(UnsupportedOperationException.class,
                () -> question.rewardCommands().add("another reward"));
    }
}
