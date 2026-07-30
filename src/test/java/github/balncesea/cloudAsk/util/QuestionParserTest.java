package github.balncesea.cloudAsk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestionParserTest {
    @Test
    void parsesSimpleQuestion() {
        var parsed = QuestionParser.parse(new String[] {"ask", "北京", "中国的首都是什么"});
        assertTrue(parsed.isPresent());
        assertEquals("北京", parsed.get().answer());
        assertEquals("中国的首都是什么", parsed.get().question());
    }

    @Test
    void parsesMultiWordAnswerWithSeparator() {
        var parsed = QuestionParser.parse(new String[] {"ask", "New", "York", "|", "问题"});
        assertTrue(parsed.isPresent());
        assertEquals("New York", parsed.get().answer());
    }
}
