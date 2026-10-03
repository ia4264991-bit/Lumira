package com.lumira.backend.sarah;

import com.lumira.backend.common.error.RateLimitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/** Atomic server-side request metering, one row per user and UTC calendar month. */
@Service
public class SarahUsageService {
    private final JdbcTemplate jdbc;
    private final int monthlyLimit;
    private final Clock clock;

    @Autowired
    public SarahUsageService(JdbcTemplate jdbc,
            @Value("${lumira.sarah.usage.monthly-limit:100}") int monthlyLimit) {
        this(jdbc, monthlyLimit, Clock.systemUTC());
    }

    SarahUsageService(JdbcTemplate jdbc, int monthlyLimit, Clock clock) {
        if (monthlyLimit < 1) throw new IllegalArgumentException("Sarah monthly usage limit must be positive");
        this.jdbc = jdbc;
        this.monthlyLimit = monthlyLimit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UsageSnapshot reserveRequest(UUID userId) {
        LocalDate periodStart = YearMonth.now(clock.withZone(ZoneOffset.UTC)).atDay(1);
        try {
            Integer used = jdbc.queryForObject("""
                    INSERT INTO sarah_usage(user_id, period_start, request_count)
                    SELECT ?, ?, 1 WHERE ? > 0
                    ON CONFLICT (user_id, period_start) DO UPDATE
                    SET request_count = sarah_usage.request_count + 1, updated_at = now()
                    WHERE sarah_usage.request_count < ?
                    RETURNING request_count
                    """, Integer.class, userId, periodStart, monthlyLimit, monthlyLimit);
            return new UsageSnapshot(used, monthlyLimit);
        } catch (EmptyResultDataAccessException exhausted) {
            throw new RateLimitException("Sarah's monthly request limit has been reached");
        }
    }
}
