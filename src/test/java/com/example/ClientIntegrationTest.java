package com.example;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {Client.class, ClientIntegrationTest.TestConfig.class})
public class ClientIntegrationTest {
    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @Autowired
    Client client;

    @TempDir
    static File tempDir;

    static class TestConfig {
        @Bean
        public File eventsFile() {
            return new File(tempDir, "events.json");
        }
    }

    @BeforeEach
    public void setUp() {
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserRunsServer() {
        // when
        client.run("up");

        // then
        assertTrue("""
                Starting...
                Status: UP""".equals(getOutput()) || """
                Starting...
                Status: FAILED""".equals(getOutput()));
    }

    @Test
    @SneakyThrows
    void shouldPrintNoEventsWhenUserRunsStatus() {
        // when
        client.run("status");

        // then
        assertEquals("No events found", getOutput());
    }

    private String getOutput() {
        return outputStreamCaptor.toString().trim();
    }
}
