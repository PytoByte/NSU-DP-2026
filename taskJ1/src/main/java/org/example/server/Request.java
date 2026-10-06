package org.example.server;

import java.nio.channels.SocketChannel;

public record Request(
        String keysRequest,
        SocketChannel socketChannel
) {}
