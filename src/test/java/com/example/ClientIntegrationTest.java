package com.example;

import lombok.SneakyThrows;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

    @Test
    @Tag("T-004")
    @SneakyThrows
    void shouldShowExpectedStatusAndHistoryAfterServerRun() {
        // when
        client.run("up");
        outputStreamCaptor.reset();

        // then
        // Assumption: there should be "Status: FAILED" instead of "No events found"
        checkServerStatus("upOrNotFound", "0");

        // and
        String currentTimestamp = getCurrentTimestamp();
        assertTrue(checkHistoryLine(0, "STARTING", currentTimestamp), getOutput());
        assertTrue(checkHistoryLine(1, "UP", currentTimestamp) ||
                checkHistoryLine(1, "FAILED", currentTimestamp), getOutput());
    }

    private String getOutput() {
        return outputStreamCaptor.toString().trim();
    }

    private void checkServerStatus(String condition, String uptime)
            throws ParseException, IOException {
        client.run("status");

        switch (condition) {
            case "up":
                assertEquals("Status: UP\r\nUptime: " + uptime + " seconds",
                        getOutput(), "Got: " + getOutput());
            case "down":
                assertEquals("Status: DOWN",
                        getOutput(), "Got: " + getOutput());
            case "notFound":
                assertEquals("No events found",
                        getOutput(), "Got: " + getOutput());
            case "upOrNotFound":
                assertTrue(("Status: UP\r\nUptime: " + uptime + " seconds").equals(getOutput()) ||
                        "No events found".equals(getOutput()), "Got: " + getOutput());
        }
        outputStreamCaptor.reset();
    }

    private boolean checkHistoryLine(int linePos, String status, String timestamp)
            throws ParseException, IOException {
        client.run("history");

        String[] lines = getOutput().split("\n");

        boolean isContains = lines[linePos].contains("Status: " + status
                + ", Timestamp: " + timestamp);

        if (isContains) outputStreamCaptor.reset();

        return isContains;
    }

    private String getCurrentTimestamp() {
        long timestamp = System.currentTimeMillis();
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
        return String.valueOf(dateTime);
    }
}
