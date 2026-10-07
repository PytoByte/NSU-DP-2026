package server;

public record KeyGeneratorRequest(
        String keysRequest,
        KeyGeneratorResponseCallback callback
) {}