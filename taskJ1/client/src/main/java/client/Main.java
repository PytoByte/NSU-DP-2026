package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: <host> <port> <name> <outputBase> [--delay seconds] [--abort]");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String name = args[2];
        Path outputBase = Path.of(args[3]);

        int delaySeconds = 0;
        boolean abort = false;

        for (int i = 4; i < args.length; i++) {
            switch (args[i]) {
                case "--delay" -> delaySeconds = Integer.parseInt(args[++i]);
                case "--abort" -> abort = true;
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(1);
                }
            }
        }

        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port))) {
            sendName(channel, name);
            System.out.println("Request sent for name: " + name);

            if (abort) {
                System.out.println("Aborting without reading response (client crash simulation).");
                return;
            }

            if (delaySeconds > 0) {
                System.out.println("Sleeping " + delaySeconds + "s before reading response...");
                Thread.sleep(delaySeconds * 1000L);
            }

            byte[] keyBytes = readBlob(channel);
            byte[] certBytes = readBlob(channel);

            Path keyFile = withSuffix(outputBase, ".key");
            Path certFile = withSuffix(outputBase, ".crt");

            Files.write(keyFile, keyBytes);
            Files.write(certFile, certBytes);

            System.out.println("Saved: " + keyFile);
            System.out.println("Saved: " + certFile);
        }
    }

    private static void sendName(SocketChannel channel, String name) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer out = ByteBuffer.allocate(nameBytes.length + 1);
        out.put(nameBytes).put((byte) 0).flip();
        while (out.hasRemaining()) {
            channel.write(out);
        }
    }

    /** Читает один блок: [4 байта длины][данные]. */
    private static byte[] readBlob(SocketChannel channel) throws IOException {
        int length = ByteBuffer.wrap(readFully(channel, Integer.BYTES)).getInt();
        return readFully(channel, length);
    }

    private static byte[] readFully(SocketChannel channel, int n) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(n);
        while (buf.hasRemaining()) {
            int read = channel.read(buf);
            if (read == -1) {
                throw new IOException("Connection closed before full response was read");
            }
        }
        return buf.array();
    }

    private static Path withSuffix(Path base, String suffix) {
        return base.resolveSibling(base.getFileName().toString() + suffix);
    }
}