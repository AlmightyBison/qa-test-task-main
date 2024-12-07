package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

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
        // When
        serverRunUp();

        // Then
        alreadyUp();
    }

    @Test
    @Tag("T-007")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerRunTwice() {
        // Given
        serverRunUp();

        // When
        alreadyUp();
        outputStreamCaptor.reset();

        // Then
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
        // When
        serverRunUp();

        // Then
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

    @Test
    @Tag("T-015")
    @SneakyThrows
    void shouldPrintAlreadyDownWithEmptyEventsFile() {
        // Assumption of the behavior
        alreadyDown();
    }

    @Test
    @Tag("T-016")
    @SneakyThrows
    void shouldPrintAlreadyDownWhenServerIsAlreadyDown() {
        // Given
        serverRunUp();

        // When
        serverRunDown();

        // Then
        alreadyDown();
    }

    @Test
    @Tag("T-017")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerAlreadyDown() {
        // Given
        serverRunUp();

        // When
        serverRunDown();
        alreadyDown();
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("down", "0");
    }

    @Test
    @Tag("T-018")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerAlreadyDown() {
        // Given
        serverRunUp();

        // When
        String currentTimestamp = getCurrentTimestamp();
        serverRunDown();
        alreadyDown();
        outputStreamCaptor.reset();

        // Then
        assertTrue(checkHistoryLine(2, "STOPPING", currentTimestamp), getOutput());
        assertTrue(checkHistoryLine(3, "DOWN", currentTimestamp) ||
                checkHistoryLine(3, "FAILED", currentTimestamp), getOutput());

        // And
        checkHistoryLength(4);
    }

    @Test
    @Tag("T-019")
    @SneakyThrows
    void shouldResetUptimeAfterServerDownAndRunAgain() {
        // Given
        serverRunUp();
        uptimeFor(3);

        // When
        serverRunDown();
        serverRunUp();

        // And
        checkServerStatus("up", "0");

        // Then
        uptimeFor(2);
    }

    @Test
    @Tag("T-020")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerDownAndRunAgain() {
        // Given
        serverRunUp();
        uptimeFor(3);

        // When
        serverRunDown();

        // And
        String currentTimestamp = getCurrentTimestamp();
        client.run("up");
        outputStreamCaptor.reset();

        // And
        assertTrue(checkHistoryLine(4, "STARTING", currentTimestamp), getOutput());
        assertTrue(checkHistoryLine(5, "UP", currentTimestamp) ||
                checkHistoryLine(5, "FAILED", currentTimestamp), getOutput());

        // Then
        checkHistoryLength(6);
    }

    @ParameterizedTest
    @Tag("T-021")
    @ValueSource(strings = {"restart", "d0wn", "1", "UP", "StAtUS"})
    @SneakyThrows
    void shouldPrintUnknownCommandWhenUserUseInvalidCommand(String command) {
        // When
        client.run(command);

        // Then
        assertEquals(("Unknown command: " + command), getOutput());
    }

    @Test
    @Tag("T-022")
    @SneakyThrows
    void shouldNotSaveNewStatusAfterInvalidCommand() {
        // Given
        client.run("command");
        outputStreamCaptor.reset();

        // When
        client.run("status");

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-023")
    @SneakyThrows
    void shouldNotSaveNewHistoryAfterInvalidCommand() {
        // Given
        client.run("command");
        outputStreamCaptor.reset();

        // When
        client.run("history");

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-024")
    @SneakyThrows
    void shouldPrintCommandOptionsWhenUserUseEmptyCommand() {
        // When
        client.run();

        // Then
        assertTrue(getOutput().contains("Usage: vpn-client <command> [options]"));
        assertTrue(getOutput().contains("status"));
        assertTrue(getOutput().contains("up"));
        assertTrue(getOutput().contains("down"));
        assertTrue(getOutput().contains("history"));
    }

    @Test
    @Tag("T-025")
    @SneakyThrows
    void shouldNotSaveNewStatusAfterEmptyCommand() {
        // Given
        client.run();
        outputStreamCaptor.reset();

        // When
        client.run("status");

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-026")
    @SneakyThrows
    void shouldNotSaveNewHistoryAfterEmptyCommand() {
        // Given
        client.run();
        outputStreamCaptor.reset();

        // When
        client.run("history");

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-027")
    @SneakyThrows
    void shouldPrintHistoryInValidRangeFromTo() {
        // Given
        generateEventsInJsonFile();

        // When
        String fromDate = LocalDate.now().minusDays(2).toString();
        String toDate = LocalDate.now().minusDays(1).toString();

        String[] args = {
                "history",
                "--from", fromDate,
                "--to", toDate
        };
        client.run(args);

        //Then
        for (int i = 0; i < getOutputLength(); i++) {
            assertTrue(getOutputLine(i).contains(fromDate), getOutput());
        }
    }

    @Test
    @Tag("T-028")
    @SneakyThrows
    void shouldPrintHistoryInValidRangeFrom() {
        // Given
        generateEventsInJsonFile();

        // When
        String fromDate = LocalDate.now().toString();
        String daysOne = LocalDate.now().plusDays(1).toString();
        String daysTwo = LocalDate.now().plusDays(2).toString();

        String[] args = {
                "history",
                "--from", fromDate,
        };
        client.run(args);

        // Then
        for (int i = 0; i < getOutputLength(); i++) {
            if (i < 6) {
                assertTrue(getOutputLine(i).contains(daysOne), getOutput());
            } else {
                assertTrue(getOutputLine(i).contains(daysTwo), getOutput());
            }
        }
    }

    @Test
    @Tag("T-029")
    @SneakyThrows
    void shouldPrintHistoryInValidRangeTo() {
        // Given
        generateEventsInJsonFile();

        // When
        String toDate = LocalDate.now().toString();
        String daysOne = LocalDate.now().minusDays(1).toString();
        String daysTwo = LocalDate.now().minusDays(2).toString();

        String[] args = {
                "history",
                "--to", toDate,
        };
        client.run(args);

        // Then
        for (int i = 0; i < getOutputLength(); i++) {
            if (i < 6) {
                assertTrue(getOutputLine(i).contains(daysOne), getOutput());
            } else {
                assertTrue(getOutputLine(i).contains(daysTwo), getOutput());
            }
        }
    }

    @Test
    @Tag("T-030")
    @SneakyThrows
    void shouldNotPrintHistoryInInvalidRangeFromTo() {
        // Given
        generateEventsInJsonFile();

        // When
        String fromDate = LocalDate.now().minusDays(20).toString();
        String toDate = LocalDate.now().minusDays(18).toString();

        String[] args = {
                "history",
                "--from", fromDate,
                "--to", toDate
        };
        client.run(args);

        //Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-031")
    @SneakyThrows
    void shouldNotPrintHistoryInInvalidRangeFrom() {
        // Given
        generateEventsInJsonFile();

        // When
        String fromDate = LocalDate.now().plusDays(10).toString();

        String[] args = {
                "history",
                "--from", fromDate,
        };
        client.run(args);

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-032")
    @SneakyThrows
    void shouldNotPrintHistoryInInvalidRangeTo() {
        // Given
        generateEventsInJsonFile();

        // When
        String toDate = LocalDate.now().minusDays(10).toString();

        String[] args = {
                "history",
                "--to", toDate,
        };
        client.run(args);

        // Then
        assertEquals("No events found", getOutput());
    }

    @ParameterizedTest
    @Tag("T-033")
    @ValueSource(strings = {"2024-12-99", "2024-24-24", "aaa", "07-12-2024", "20241207"})
    @SneakyThrows
    void shouldPrintErrorForHistoryWithWrongDateFormat(String date) {
        // Given
        generateEventsInJsonFile();

        // When
        String[] args = {
                "history",
                "--from", date,
                "--to", "date"
        };
        try {
            client.run(args);

            // Then
        } catch (Exception e) {
            assertTrue(e.toString().contains("Text '" + date + "' could not be parsed"));
        }
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

    private int getOutputLength() {
        String[] lines = getOutput().split("\n");
        return lines.length;
    }


    private String getOutputLine(int linePos) {
        String[] lines = getOutput().split("\n");
        return lines[linePos];
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

    private void serverRunDown() throws ParseException, IOException {
        // When
        client.run("down");
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("down", "0");
    }

    private void alreadyDown() throws ParseException, IOException {
        // When
        client.run("down");

        // Then
        assertEquals("Already DOWN", getOutput());
    }

    private void generateEventsInJsonFile() throws IOException, InterruptedException {
        ArrayList<Status> arrStatuses = getArrStatuses();

        for (int i = 1; i < 3; i++) {
            for (int j = 0; j < arrStatuses.size(); j++) {
                writeEventToFile(new Event(arrStatuses.get(j), getCurrentDateMinusDaysAndMinutes(i, j)));
                writeEventToFile(new Event(arrStatuses.get(j), getCurrentDatePlusDaysAndMinutes(i, j)));
                sleep(100);
            }
        }
    }

    public ArrayList<Status> getArrStatuses() {
        return new ArrayList<>
                (List.of(Status.STARTING, Status.UP, Status.STOPPING, Status.FAILED, Status.STOPPING, Status.DOWN));
    }

    private long getCurrentDateMinusDaysAndMinutes(int days, int minutes) {
        return LocalDateTime.now().minusDays(days).plusMinutes(minutes).toEpochSecond(ZoneOffset.UTC) * 1000;
    }

    private long getCurrentDatePlusDaysAndMinutes(int days, int minutes) {
        return LocalDateTime.now().plusDays(days).plusMinutes(minutes).toEpochSecond(ZoneOffset.UTC) * 1000;
    }

    private void writeEventToFile(Event event) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File eventsFile = new File(tempDir, "events.json");

        if (eventsFile.createNewFile()) {
            objectMapper.writeValue(eventsFile, Collections.emptyList());
        }
        List<Event> events = objectMapper.readValue(eventsFile, new TypeReference<>() {
        });
        events.add(event);
        objectMapper.writeValue(eventsFile, events);
    }
}
