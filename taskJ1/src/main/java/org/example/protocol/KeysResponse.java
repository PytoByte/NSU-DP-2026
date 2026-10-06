package org.example.protocol;

public record KeysResponse(
        byte[] key1,
        byte[] key2
) {}
