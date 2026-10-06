package org.example.server;

import org.example.protocol.KeysRequest;

import java.nio.channels.SocketChannel;

public record Request(
        KeysRequest keysRequest,
        SocketChannel socketChannel
) {}
