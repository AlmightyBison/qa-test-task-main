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

import static java.lang.Thread.sleep;
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
        // When
        client.run("status");

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-002")
    @SneakyThrows
    void shouldPrintNoEventsWhenUserRunsHistoryWithEmptyEventsFile() {
        // When
        client.run("history");

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-003")
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserRunsServer() {
        // When
        client.run("up");

        // Then
        assertTrue("Starting...\r\nStatus: UP".equals(getOutput()) ||
                "Starting...\r\nStatus: FAILED".equals(getOutput()), getOutput());
    }

    @Test
    @Tag("T-004")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerRun() {
        // When
        client.run("up");
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("up", "0");
    }

    @Test
    @Tag("T-005")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerRun() {
        // When
        client.run("up");
        outputStreamCaptor.reset();

        // Then
        String currentTimestamp = getCurrentTimestamp();
        assertTrue(checkHistoryLine(0, "STARTING", currentTimestamp), getOutput());
        assertTrue(checkHistoryLine(1, "UP", currentTimestamp) ||
                checkHistoryLine(1, "FAILED", currentTimestamp), getOutput());

        // And
        checkHistoryLength(2);
    }

    @Test
    @Tag("T-006")
    @SneakyThrows
    void shouldPrintAlreadyUpWhenServerIsAlreadyRun() {
        serverRunUp();
        alreadyUp();
    }

    @Test
    @Tag("T-007")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerRunTwice() {
        // Given
        serverRunUp();
        alreadyUp();
        outputStreamCaptor.reset();

        // When
        checkServerStatus("up", "0");
    }

    @Test
    @Tag("T-008")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerRunTwice() {
        // Given
        serverRunUp();
        alreadyUp();
        outputStreamCaptor.reset();

        // Then
        String currentTimestamp = getCurrentTimestamp();
        assertTrue(checkHistoryLine(0, "STARTING", currentTimestamp), getOutput());
        assertTrue(checkHistoryLine(1, "UP", currentTimestamp), getOutput());

        // And
        checkHistoryLength(2);
    }

    @Test
    @Tag("T-009")
    @SneakyThrows
    void shouldPrintExpectedUptimeWhenServerRun() {
        serverRunUp();
        uptimeFor(3);
    }

    @Test
    @Tag("T-010")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerRunForSomeTime() {
        // Given
        serverRunUp();
        uptimeFor(4);

        // When
        // Server is working for extra 1 sec
        sleep(1000);

        // Then
        checkServerStatus("up", "5");
    }

    @Test
    @Tag("T-011")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerRunForSomeTime() {
        // Given
        String currentTimestamp = getCurrentTimestamp();
        serverRunUp();
        uptimeFor(3);

        // When
        // Server is working for extra 1 sec
        sleep(1000);

        // Then
        assertTrue(checkHistoryLine(0, "STARTING", currentTimestamp), getOutput());
        assertTrue(checkHistoryLine(1, "UP", currentTimestamp), getOutput());

        // And
        checkHistoryLength(2);
    }

    @Test
    @Tag("T-012")
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserDownServer() {
        // Given
        serverRunUp();

        // When
        client.run("down");

        // Then
        assertTrue("Stopping...\r\nStatus: DOWN".equals(getOutput()) ||
                "Stopping...\r\nStatus: FAILED".equals(getOutput()), getOutput());
    }

    @Test
    @Tag("T-013")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerDown() {
        // Given
        serverRunUp();

        // When
        client.run("down");
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("down", "0");
    }

    @Test
    @Tag("T-014")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerDown() {
        // Given
        serverRunUp();

        // When
        String currentTimestamp = getCurrentTimestamp();
        client.run("down");
        outputStreamCaptor.reset();

        // Then
        assertTrue(checkHistoryLine(2, "STOPPING", currentTimestamp), getOutput());
        assertTrue(checkHistoryLine(3, "DOWN", currentTimestamp) ||
                checkHistoryLine(3, "FAILED", currentTimestamp), getOutput());

        // And
        checkHistoryLength(4);
    }

    private String getOutput() {
        return outputStreamCaptor.toString().trim();
    }

    private void checkServerStatus(String condition, String uptime)
            throws ParseException, IOException {
        client.run("status");

        switch (condition) {
            case "up" -> assertEquals("Status: UP\r\nUptime: " + uptime
                    + " seconds", getOutput());
            case "down" -> assertEquals("Status: DOWN", getOutput());
            case "notFound" -> assertEquals("No events found", getOutput());
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

    private void checkHistoryLength(int length) throws ParseException, IOException {
        client.run("history");

        String[] lines = getOutput().split("\n");
        assertEquals(length, lines.length, "Current history length is: " + lines.length);
    }

    private String getCurrentTimestamp() {
        long timestamp = System.currentTimeMillis();
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
        return String.valueOf(dateTime);
    }

    private void serverRunUp() throws ParseException, IOException {
        // When
        client.run("up");
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("up", "0");
    }

    private void alreadyUp() throws ParseException, IOException {
        // When
        client.run("up");

        // Then
        assertEquals("Already UP", getOutput());
    }

    private void uptimeFor(int sec)
            throws ParseException, IOException, InterruptedException {
        // When
        // Server is working for X sec
        sleep(sec * 1000L);

        // Then
        checkServerStatus("up", String.valueOf(sec));
    }
}
