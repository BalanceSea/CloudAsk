package github.balncesea.cloudAsk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnswerNormalizerTest {
    @Test
    void normalizesCaseSpacesAndTrim() {
        assertEquals("hello world", AnswerNormalizer.normalize("  HeLLo   WORLD ", true, true, true));
    }

    @Test
    void canKeepCaseAndWhitespace() {
        assertEquals("  A  B ", AnswerNormalizer.normalize("  A  B ", false, false, false));
    }
}
