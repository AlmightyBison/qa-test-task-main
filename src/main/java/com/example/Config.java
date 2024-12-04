package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class Config {
    static final String EVENTS_JSON_FILENAME = "events.json";

    @Bean
    public File eventsFile() {
        return new File(EVENTS_JSON_FILENAME);
    }
}
