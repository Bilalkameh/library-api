package com.library.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LibraryTimeConfiguration {
    @Bean
    public Clock libraryClock() {
        return Clock.systemDefaultZone();
    }
}
