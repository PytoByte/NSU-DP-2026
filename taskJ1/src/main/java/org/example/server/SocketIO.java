package org.example.server;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public final class SocketIO {

    private static final byte NAME_TERMINATOR = 0;

    public static String readName(SocketChannel channel, ByteBuffer buffer) throws IOException {
        int n = channel.read(buffer);
        if (n == -1) {
            throw new EOFException();
        }
        if (n == 0) {
            return null;
        }

        int zeroPos = indexOfTerminator(buffer);
        if (zeroPos < 0) {
            if (!buffer.hasRemaining()) {
                throw new IOException("Name exceeds " + buffer.capacity() + " bytes without terminator");
            }
            return null;
        }

        byte[] nameBytes = new byte[zeroPos];
        buffer.get(0, nameBytes);
        return new String(nameBytes, StandardCharsets.US_ASCII);
    }

    public static boolean tryWrite(SocketChannel channel, ByteBuffer buffer) throws IOException {
        channel.write(buffer);
        return !buffer.hasRemaining();
    }

    private static int indexOfTerminator(ByteBuffer buffer) {
        for (int i = 0; i < buffer.position(); i++) {
            if (buffer.get(i) == NAME_TERMINATOR) {
                return i;
            }
        }
        return -1;
    }
}