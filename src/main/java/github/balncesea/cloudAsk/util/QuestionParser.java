package github.balncesea.cloudAsk.util;

import java.util.Arrays;
import java.util.Optional;

public final class QuestionParser {
    private QuestionParser() {
    }

    public static Optional<ParsedQuestion> parse(String[] args) {
        if (args.length < 3) {
            return Optional.empty();
        }

        int separator = -1;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("|")) {
                separator = i;
                break;
            }
        }

        String answer;
        String question;
        if (separator >= 0) {
            if (separator == 1 || separator == args.length - 1) {
                return Optional.empty();
            }
            answer = String.join(" ", Arrays.copyOfRange(args, 1, separator));
            question = String.join(" ", Arrays.copyOfRange(args, separator + 1, args.length));
        } else {
            answer = args[1];
            question = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }
        if (answer.isBlank() || question.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedQuestion(answer, question));
    }

    public record ParsedQuestion(String answer, String question) {
    }
}
