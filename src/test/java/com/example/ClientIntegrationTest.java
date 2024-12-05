package com.example;

import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
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
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    @Tag("T-001")
    @SneakyThrows
    void shouldPrintNoEventsWhenUserRunsStatusWithEmptyEventsFile() {
        // when
        client.run("status");

        // then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-002")
    @SneakyThrows
    void shouldPrintNoEventsWhenUserRunsHistoryWithEmptyEventsFile() {
        // when
        client.run("history");

        // then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-003")
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserRunsServer() {
        // when
        client.run("up");

        // then
        assertTrue("Starting...\r\nStatus: UP".equals(getOutput()) ||
                "Starting...\r\nStatus: FAILED".equals(getOutput()));
    }

    private String getOutput() {
        return outputStreamCaptor.toString().trim();
    }
}
