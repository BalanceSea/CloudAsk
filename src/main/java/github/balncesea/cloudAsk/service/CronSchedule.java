package github.balncesea.cloudAsk.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.OptionalLong;

public final class CronSchedule {
    private final ExecutionTime executionTime;
    private final ZoneId zoneId;

    public CronSchedule(String expression, ZoneId zoneId) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron 表达式不能为空");
        }
        String normalized = expression.trim().replaceAll("\\s+", " ");
        int fields = normalized.split(" ").length;
        CronType type;
        if (fields == 5) {
            type = CronType.UNIX;
        } else if (fields == 6 || fields == 7) {
            type = CronType.QUARTZ;
        } else {
            throw new IllegalArgumentException("Cron 必须为 UNIX 5 段或 Quartz 6/7 段表达式");
        }

        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(type));
        Cron cron = parser.parse(normalized);
        cron.validate();
        this.executionTime = ExecutionTime.forCron(cron);
        this.zoneId = zoneId;
    }

    public OptionalLong nextAfter(long epochMillis) {
        ZonedDateTime reference = Instant.ofEpochMilli(epochMillis).atZone(zoneId);
        Optional<ZonedDateTime> next = executionTime.nextExecution(reference);
        return next.isPresent() ? OptionalLong.of(next.get().toInstant().toEpochMilli()) : OptionalLong.empty();
    }
}
