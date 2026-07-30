package github.balncesea.cloudAsk.model;

public record NetworkEvent(
        Type type,
        Question question,
        String winnerUuid,
        String winnerName,
        String winnerServer,
        String winnerInstanceId,
        long occurredAt) {

    public static NetworkEvent started(Question question) {
        return new NetworkEvent(Type.STARTED, question, null, null, null, null, question.createdAt());
    }

    public static NetworkEvent answered(
            Question question, String winnerUuid, String winnerName, String winnerServer,
            String winnerInstanceId, long answeredAt) {
        return new NetworkEvent(
                Type.ANSWERED, question, winnerUuid, winnerName, winnerServer, winnerInstanceId, answeredAt);
    }

    public static NetworkEvent cancelled(Question question) {
        return new NetworkEvent(Type.CANCELLED, question, null, null, null, null, System.currentTimeMillis());
    }

    public enum Type {
        STARTED,
        ANSWERED,
        CANCELLED
    }
}
