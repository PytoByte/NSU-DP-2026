package server;

public record KeyRequest(
        String keysRequest,
        KeyResponseCallback callback
) {}