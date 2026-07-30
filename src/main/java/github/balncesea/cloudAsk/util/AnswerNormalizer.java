package github.balncesea.cloudAsk.util;

import java.util.Locale;

public final class AnswerNormalizer {
    private AnswerNormalizer() {
    }

    public static String normalize(
            String input, boolean ignoreCase, boolean trim, boolean collapseSpaces) {
        String result = input == null ? "" : input;
        if (trim) {
            result = result.trim();
        }
        if (collapseSpaces) {
            result = result.replaceAll("\\s+", " ");
        }
        if (ignoreCase) {
            result = result.toLowerCase(Locale.ROOT);
        }
        return result;
    }
}
