package com.example;

public record Event(Status status, long timestamp) {
}

enum Status {
    STARTING,
    STOPPING,
    UP,
    DOWN,
    FAILED
}
