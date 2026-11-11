package com.library.api.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FixedClockConfiguration {
    @Bean
    @Primary
    public Clock fixedLibraryClock() {
        return Clock.fixed(Instant.parse("2024-02-28T22:30:00Z"), ZoneId.of("Asia/Beirut"));
    }
}
