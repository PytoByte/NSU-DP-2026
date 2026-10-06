package org.example.server;

import java.nio.channels.SocketChannel;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

public record Response(
        String name,
        KeyPair keyPair,
        X509Certificate certificate,
        SocketChannel socketChannel,
        Throwable error          // null при успехе
) {
    public static Response success(String name, KeyPair kp, X509Certificate crt, SocketChannel ch) {
        return new Response(name, kp, crt, ch, null);
    }

    public static Response failure(String name, SocketChannel ch, Throwable err) {
        return new Response(name, null, null, ch, err);
    }

    public boolean isSuccess() { return error == null; }
}