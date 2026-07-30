package github.balncesea.cloudAsk.model;

import java.util.List;

public record Question(
        String id,
        String text,
        String answer,
        String normalizedAnswer,
        String askerUuid,
        String askerName,
        String sourceServer,
        List<String> rewardCommands,
        long createdAt,
        long expiresAt) {

    public Question {
        rewardCommands = rewardCommands == null ? List.of() : List.copyOf(rewardCommands);
    }
}
