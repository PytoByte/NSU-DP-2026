package org.example.server;

public record KeyGeneratorRequest(
        String keysRequest,
        KeyGeneratorResponseCallback callback
) {}