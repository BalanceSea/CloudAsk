package github.balncesea.cloudAsk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CronScheduleTest {
    @Test
    void supportsUnixFiveFieldExpressions() {
        CronSchedule schedule = new CronSchedule("*/5 * * * *", ZoneId.of("UTC"));
        long reference = Instant.parse("2026-07-30T10:02:30Z").toEpochMilli();
        assertEquals(Instant.parse("2026-07-30T10:05:00Z").toEpochMilli(), schedule.nextAfter(reference).orElseThrow());
    }

    @Test
    void supportsQuartzExpressionsWithSeconds() {
        CronSchedule schedule = new CronSchedule("15 0/5 * * * ?", ZoneId.of("UTC"));
        long reference = Instant.parse("2026-07-30T10:02:30Z").toEpochMilli();
        assertEquals(Instant.parse("2026-07-30T10:05:15Z").toEpochMilli(), schedule.nextAfter(reference).orElseThrow());
    }

    @Test
    void rejectsUnsupportedFieldCounts() {
        assertThrows(IllegalArgumentException.class, () -> new CronSchedule("* * *", ZoneId.of("UTC")));
    }
}
