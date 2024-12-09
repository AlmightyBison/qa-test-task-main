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
import static org.junit.jupiter.api.Assertions.*;

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
    void shouldPrintNoEventsWhenUserRunsStatusWithEmptyEventsFile() throws ParseException, IOException {
        // When
        client.run("status");

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-002")
    @SneakyThrows
    void shouldPrintNoEventsWhenUserRunsHistoryWithEmptyEventsFile() throws ParseException, IOException {
        // When
        client.run("history");

        // Then
        assertEquals("No events found", getOutput());
    }

    @Test
    @Tag("T-003")
    @SneakyThrows
    void shouldWriteTwoEventsWhenUserRunsServer() throws ParseException, IOException {
        // When
        client.run("up");

        // Then
        assertTrue("Starting...\r\nStatus: UP".equals(getOutput()) ||
                "Starting...\r\nStatus: FAILED".equals(getOutput()), getOutput());
    }

    @Test
    @Tag("T-003a")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterFailedRun() throws ParseException, IOException {
        Event startingEvent = new Event(Status.STARTING, System.currentTimeMillis());
        Event upEvent = new Event(Status.FAILED, System.currentTimeMillis());

        writeEventToFile(List.of(startingEvent, upEvent));
        outputStreamCaptor.reset();

        client.run("status");

        assertTrue(getOutput().contains("No events found"));
    }

    @Test
    @Tag("T-003b")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterFailedDown() throws ParseException, IOException {
        Event startingEvent = new Event(Status.STOPPING, System.currentTimeMillis());
        Event upEvent = new Event(Status.FAILED, System.currentTimeMillis());

        writeEventToFile(List.of(startingEvent, upEvent));
        outputStreamCaptor.reset();

        client.run("status");

        assertTrue(getOutput().contains("No events found"));
    }

    @Test
    @Tag("T-004")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerRun() throws ParseException, IOException {
        // When
        serverIsStarted();
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("up", "0");
    }

    @Test
    @Tag("T-005")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerRun() throws ParseException, IOException {
        // When
        serverIsStarted();
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
    void shouldPrintAlreadyUpWhenServerIsAlreadyRun() throws ParseException, IOException {
        // When
        serverIsStarted();

        // Then
        checkAlreadyUpStatus();
    }

    @Test
    @Tag("T-007")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerRunTwice() throws ParseException, IOException {
        // Given
        serverIsStarted();

        // When
        checkAlreadyUpStatus();
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("up", "0");
    }

    @Test
    @Tag("T-008")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerRunTwice() throws ParseException, IOException {
        // Given
        serverIsStarted();

        //When
        checkAlreadyUpStatus();
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
    void shouldPrintExpectedUptimeWhenServerRun() throws ParseException, IOException, InterruptedException {
        // When
        serverIsStarted();

        // Then
        checkServerUptimeFor(3);
    }

    @Test
    @Tag("T-010")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerRunForSomeTime() throws ParseException, IOException, InterruptedException {
        // Given
        serverIsStarted();

        // When
        checkServerUptimeFor(4);

        // And
        // Server is working for extra 1 sec
        sleep(1000);

        // Then
        checkServerStatus("up", "5");
    }

    @Test
    @Tag("T-011")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerRunForSomeTime() throws ParseException, IOException, InterruptedException {
        // Given
        String currentTimestamp = getCurrentTimestamp();
        serverIsStarted();

        // When
        checkServerUptimeFor(3);

        // And
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
    void shouldWriteTwoEventsWhenUserDownServer() throws ParseException, IOException {
        // Given
        serverIsStarted();

        // When
        client.run("down");

        // Then
        assertTrue("Stopping...\r\nStatus: DOWN".equals(getOutput()) ||
                "Stopping...\r\nStatus: FAILED".equals(getOutput()), getOutput());
    }

    @Test
    @Tag("T-013")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerDown() throws ParseException, IOException {
        // Given
        serverIsStarted();

        // When
        serverIsShutDown();
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("down", "0");
    }

    @Test
    @Tag("T-014")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerDown() throws ParseException, IOException {
        // Given
        serverIsStarted();

        // When
        String currentTimestamp = getCurrentTimestamp();
        serverIsShutDown();
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
    void shouldPrintAlreadyDownWithEmptyEventsFile() throws ParseException, IOException {
        // Assumption of the behavior
        checkAlreadyDownStatus();
    }

    @Test
    @Tag("T-016")
    @SneakyThrows
    void shouldPrintAlreadyDownWhenServerIsAlreadyDown() throws ParseException, IOException {
        // Given
        serverIsStarted();

        // When
        serverIsShutDown();

        // Then
        checkAlreadyDownStatus();
    }

    @Test
    @Tag("T-017")
    @SneakyThrows
    void shouldPrintExpectedStatusAfterServerAlreadyDown() throws ParseException, IOException {
        // Given
        serverIsStarted();

        // When
        serverIsShutDown();

        // And
        checkAlreadyDownStatus();
        outputStreamCaptor.reset();

        // Then
        checkServerStatus("down", "0");
    }

    @Test
    @Tag("T-018")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerAlreadyDown() throws ParseException, IOException {
        // Given
        serverIsStarted();

        // When
        String currentTimestamp = getCurrentTimestamp();
        serverIsShutDown();

        // And
        checkAlreadyDownStatus();
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
    void shouldResetUptimeAfterServerDownAndRunAgain() throws ParseException, IOException, InterruptedException {
        // Given
        serverIsStarted();
        checkServerUptimeFor(3);

        // When
        serverIsShutDown();
        serverIsStarted();

        // And
        checkServerStatus("up", "0");

        // Then
        checkServerUptimeFor(2);
    }

    @Test
    @Tag("T-020")
    @SneakyThrows
    void shouldPrintExpectedHistoryAfterServerDownAndRunAgain() throws ParseException, IOException, InterruptedException {
        // Given
        serverIsStarted();
        checkServerUptimeFor(3);

        // When
        serverIsShutDown();

        // And
        String currentTimestamp = getCurrentTimestamp();
        serverIsStarted();
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
    void shouldPrintUnknownCommandWhenUserUseInvalidCommand(String command) throws ParseException, IOException {
        // When
        client.run(command);

        // Then
        assertEquals(("Unknown command: " + command), getOutput());
    }

    @Test
    @Tag("T-022")
    @SneakyThrows
    void shouldNotSaveNewStatusAfterInvalidCommand() throws ParseException, IOException {
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
    void shouldNotSaveNewHistoryAfterInvalidCommand() throws ParseException, IOException {
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
    void shouldPrintCommandOptionsWhenUserUseEmptyCommand() throws ParseException, IOException {
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
    void shouldNotSaveNewStatusAfterEmptyCommand() throws ParseException, IOException {
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
    void shouldNotSaveNewHistoryAfterEmptyCommand() throws ParseException, IOException {
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
    void shouldPrintHistoryInValidRangeFromTo() throws ParseException, IOException {
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
    void shouldPrintHistoryInValidRangeFrom() throws ParseException, IOException {
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
    void shouldPrintHistoryInValidRangeTo() throws ParseException, IOException {
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
    void shouldNotPrintHistoryInInvalidRangeFromTo() throws ParseException, IOException {
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
    void shouldNotPrintHistoryInInvalidRangeFrom() throws ParseException, IOException {
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
    void shouldNotPrintHistoryInInvalidRangeTo() throws ParseException, IOException {
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
    void shouldPrintErrorForHistoryWithWrongDateFormat(String date) throws IOException {
        // Given
        generateEventsInJsonFile();

        // When
        String[] args = {
                "history",
                "--from", date,
                "--to", "date"
        };
        Exception exception = assertThrows(java.time.format.DateTimeParseException.class, () -> client.run(args));

        // Then
        assertTrue(exception.getMessage().contains("Text '" + date + "' could not be parsed"), exception.getMessage());
    }

    @Test
    @Tag("T-034")
    @SneakyThrows
    void shouldPrintErrorForHistoryWithNoDate() throws IOException {
        // Given
        generateEventsInJsonFile();

        // When
        String[] args = {
                "history",
                "--from",
                "--to",
        };
        Exception exception = assertThrows(
                org.apache.commons.cli.MissingArgumentException.class, () -> client.run(args));

        // Then
        assertTrue(exception.getMessage().contains("Missing argument for option: f"), exception.getMessage());
    }

    @Test
    @Tag("T-035")
    @SneakyThrows
    void shouldPrintHistoryInAscOrder() throws ParseException, IOException {
        // Given
        generateEventsInJsonFile();

        // When
        String[] args = {
                "history",
                "--sort", "asc",
        };
        client.run(args);

        //Then
        List<LocalDateTime> timestamps = getTimestampsFromOutput(getOutput());

        for (int i = 0; i < timestamps.size() - 1; i++) {
            assertTrue(timestamps.get(i).isBefore(timestamps.get(i + 1)) ||
                    timestamps.get(i).isEqual(timestamps.get(i + 1)), timestamps.toString());
        }
    }

    @Test
    @Tag("T-036")
    @SneakyThrows
    void shouldPrintHistoryInDescOrder() throws ParseException, IOException {
        // Given
        generateEventsInJsonFile();

        // When
        String[] args = {
                "history",
                "--sort", "desc",
        };
        client.run(args);

        //Then
        List<LocalDateTime> timestamps = getTimestampsFromOutput(getOutput());

        for (int i = 0; i < timestamps.size() - 1; i++) {
            assertTrue(timestamps.get(i).isAfter(timestamps.get(i + 1)) ||
                    timestamps.get(i).isEqual(timestamps.get(i + 1)), timestamps.toString());
        }
    }

    @ParameterizedTest
    @Tag("T-037")
    @ValueSource(strings = {"STARTING", "UP", "STOPPING", "DOWN", "FAILED"})
    @SneakyThrows
    void shouldPrintHistoryWithSpecificStatus(String status) throws ParseException, IOException {
        // Given
        generateEventsInJsonFile();

        // When
        String[] args = {
                "history",
                "--status", status,
        };
        client.run(args);

        //Then
        for (int i = 0; i < getOutputLength(); i++) {
            assertTrue(getOutputLine(i).contains(status),
                    "Status: " + status + "\n" + getOutput());
        }
    }

    @Test
    @Tag("T-038")
    @SneakyThrows
    void shouldPrintErrorForHistoryWithInvalidStatus() throws IOException {
        // Given
        generateEventsInJsonFile();

        // When
        String[] args = {
                "history",
                "--status", "STATUS",
        };
        Exception exception = assertThrows(java.lang.IllegalArgumentException.class, () -> client.run(args));

        // Then
        assertTrue(exception.getMessage().contains("No enum constant com.example.Status."), exception.getMessage());
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

        boolean isContains = lines[linePos].contains("Status: " + status + ", Timestamp: " + timestamp);

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

    private void serverIsStarted() throws IOException {
        Event startingEvent = new Event(Status.STARTING, System.currentTimeMillis());
        Event upEvent = new Event(Status.UP, System.currentTimeMillis());

        writeEventToFile(List.of(startingEvent, upEvent));
    }

    private void checkAlreadyUpStatus() throws ParseException, IOException {
        // When
        client.run("up");

        // Then
        assertEquals("Already UP", getOutput());
    }

    private void checkServerUptimeFor(int sec)
            throws ParseException, IOException, InterruptedException {
        // When
        // Server is working for X sec
        sleep(sec * 1000L);

        // Then
        checkServerStatus("up", String.valueOf(sec));
    }

    private void serverIsShutDown() throws IOException {
        Event stoppingEvent = new Event(Status.STOPPING, System.currentTimeMillis());
        Event downEvent = new Event(Status.DOWN, System.currentTimeMillis());

        writeEventToFile(List.of(stoppingEvent, downEvent));
    }

    private void checkAlreadyDownStatus() throws ParseException, IOException {
        // When
        client.run("down");

        // Then
        assertEquals("Already DOWN", getOutput());
    }

    private void generateEventsInJsonFile() throws IOException {
        ArrayList<Status> arrStatuses = getArrStatuses();
        List<Event> events = new ArrayList<Event>();
        int dayDeviationFrom = 1;
        int dayDeviationTo = 3;

        for (int i = dayDeviationFrom; i < dayDeviationTo; i++) {
            for (int j = 0; j < arrStatuses.size(); j++) {
                events.add(new Event(arrStatuses.get(j), getCurrentDateMinusDaysAndMinutes(i, j)));
                events.add(new Event(arrStatuses.get(j), getCurrentDatePlusDaysAndMinutes(i, j)));
            }
        }
        writeEventToFile(events);
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

    private void writeEventToFile(List<Event> events) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File eventsFile = new File(tempDir, "events.json");

        if (eventsFile.createNewFile()) {
            objectMapper.writeValue(eventsFile, Collections.emptyList());
        }
        List<Event> oldEvents = objectMapper.readValue(eventsFile, new TypeReference<>() {});
        oldEvents.addAll(events);
        objectMapper.writeValue(eventsFile, oldEvents);
    }

    private List<LocalDateTime> getTimestampsFromOutput(String output) {
        List<LocalDateTime> timestamps = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            String[] parts = line.split("Timestamp: ");
            timestamps.add(LocalDateTime.parse(parts[1].trim()));
        }
        return timestamps;
    }
}
