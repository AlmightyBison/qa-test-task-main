package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.cli.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Client {
    private static final String STATUS_COMMAND = "status";
    private static final String UP_COMMAND = "up";
    private static final String DOWN_COMMAND = "down";
    private static final String HISTORY_COMMAND = "history";

    private final static Map<String, Options> COMMANDS = Map.of(
            STATUS_COMMAND, new Options(),
            UP_COMMAND, new Options(),
            DOWN_COMMAND, new Options(),
            HISTORY_COMMAND, new Options()
                    .addOption("f", "from", true, "From")
                    .addOption("t", "to", true, "To")
                    .addOption("s", "sort", true, "Sort")
                    .addOption("S", "status", true, "Status")
    );

    private final File eventsFile;

    public void run(String... args) throws ParseException, IOException {
        CommandLineParser parser = new DefaultParser();

        if (args.length == 0) {
            System.out.println("Usage: vpn-client <command> [options]");
            System.out.println("Commands:");
            COMMANDS.keySet().forEach(command -> System.out.println("  " + command));
            return;
        }

        String command = args[0];

        if (!COMMANDS.containsKey(command)) {
            System.out.println("Unknown command: " + command);
            return;
        }

        Options options = COMMANDS.get(command);
        CommandLine commandLine = parser.parse(options, args);

        switch (command) {
            case STATUS_COMMAND -> {
                Optional<Event> latestNotFailedEvent = getLatestNotFailedEvent();

                if (latestNotFailedEvent.isEmpty()) {
                    System.out.println("No events found");
                } else {
                    System.out.println("Status: " + latestNotFailedEvent.get().status());

                    if (latestNotFailedEvent.get().status() == Status.UP) {
                        long uptime = (System.currentTimeMillis() - latestNotFailedEvent.get().timestamp()) / 1000;
                        System.out.println("Uptime: " + uptime + " seconds");
                    }
                }
            }
            case UP_COMMAND -> {
                Optional<Event> latestNotFailedEvent = getLatestNotFailedEvent();

                if (latestNotFailedEvent.isPresent() && latestNotFailedEvent.get().status() == Status.UP) {
                    System.out.println("Already UP");
                } else {
                    Status randomResult = new Random().nextBoolean() ? Status.UP : Status.FAILED;

                    System.out.println("Starting...");
                    writeEventToFile(new Event(Status.STARTING, System.currentTimeMillis()));

                    System.out.println("Status: " + randomResult.name());
                    writeEventToFile(new Event(randomResult, System.currentTimeMillis()));
                }
            }
            case DOWN_COMMAND -> {
                Optional<Event> latestNotFailedEvent = getLatestNotFailedEvent();

                if (latestNotFailedEvent.isPresent() && latestNotFailedEvent.get().status() == Status.DOWN) {
                    System.out.println("Already DOWN");
                } else {
                    Status randomResult = new Random().nextBoolean() ? Status.DOWN : Status.FAILED;

                    System.out.println("Stopping...");
                    writeEventToFile(new Event(Status.STOPPING, System.currentTimeMillis()));

                    System.out.println("Status: " + randomResult.name());
                    writeEventToFile(new Event(randomResult, System.currentTimeMillis()));
                }
            }
            case HISTORY_COMMAND -> {
                String stringFrom = commandLine.getOptionValue("from");
                long from = stringFrom != null ? LocalDate.parse(stringFrom)
                        .toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000 : -1;

                String stringTo = commandLine.getOptionValue("to");
                long to = stringTo != null ? LocalDate.parse(stringTo)
                        .toEpochSecond(LocalTime.of(0, 0), ZoneOffset.UTC) * 1000 : -1;

                String sort = commandLine.getOptionValue("sort");
                String status = commandLine.getOptionValue("status");

                List<Event> events = filterEvents(from, to, sort, status);

                if (events.isEmpty()) {
                    System.out.println("No events found");
                } else {
                    events.forEach(event -> {
                        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(event.timestamp() / 1000, 0, ZoneOffset.UTC);
                        System.out.println("Status: " + event.status() + ", Timestamp: " + dateTime);
                    });
                }
            }
        }
    }

    private Optional<Event> getLatestNotFailedEvent() throws IOException {
        Optional<Event> latestEvent = getLatestEvent();

        if (latestEvent.isEmpty()) {
            return Optional.empty();
        } else {
            if (latestEvent.get().status() == Status.FAILED) {
                return getLatestEventByStatus(Status.UP, Status.DOWN);
            } else {
                return latestEvent;
            }
        }
    }

    private void writeEventToFile(Event event) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        if (eventsFile.createNewFile()) {
            objectMapper.writeValue(eventsFile, Collections.emptyList());
        }

        List<Event> events = objectMapper.readValue(eventsFile, new TypeReference<>() {
        });
        events.add(event);
        objectMapper.writeValue(eventsFile, events);
    }

    private Optional<Event> getLatestEventByStatus(Status... status) throws IOException {
        return readAllEvents().stream()
                .filter(event -> {
                    for (Status s : status) {
                        if (event.status() == s) {
                            return true;
                        }
                    }
                    return false;
                })
                .reduce((first, second) -> second)
                .or(Optional::empty);
    }

    private Optional<Event> getLatestEvent() throws IOException {
        return readAllEvents().stream()
                .reduce((first, second) -> second)
                .or(Optional::empty);
    }

    private List<Event> filterEvents(long from, long to, String sort, String status) throws IOException {
        List<Event> events = readAllEvents();

        return events.stream()
                .filter(event -> {
                    if (from != -1 && event.timestamp() < from) {
                        return false;
                    }
                    if (to != -1 && event.timestamp() > to) {
                        return false;
                    }
                    return status == null || event.status().equals(Status.valueOf(status));
                })
                .sorted((event1, event2) -> {
                    if (sort != null) {
                        if (sort.equals("asc")) {
                            return Long.compare(event1.timestamp(), event2.timestamp());
                        } else if (sort.equals("desc")) {
                            return Long.compare(event2.timestamp(), event1.timestamp());
                        }
                    }
                    return 0;
                })
                .collect(Collectors.toList());
    }

    private List<Event> readAllEvents() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        if (eventsFile.createNewFile()) {
            objectMapper.writeValue(eventsFile, Collections.emptyList());
        }

        return objectMapper.readValue(eventsFile, new TypeReference<>() {
        });
    }
}
