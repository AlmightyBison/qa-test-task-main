package com.example;

import lombok.RequiredArgsConstructor;
import org.apache.commons.cli.ParseException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CmdRunner implements CommandLineRunner {
    private final Client client;

    @Override
    public void run(String... args) throws ParseException, IOException {
        client.run(args);
    }
}
