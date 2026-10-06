package org.example.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

public class TCPRequestReceiver implements Runnable, AutoCloseable {

    private static final int MAX_NAME_BYTES = 1024;
    private static final byte NAME_TERMINATOR = 0;

    private final SelectorLoop selectorLoop;
    private final ServerSocketChannel serverChannel;
    private final BlockingQueue<Request> requestQueue;

    public TCPRequestReceiver(int port, BlockingQueue<Request> requestQueue) throws IOException {
        this.requestQueue = requestQueue;

        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(port));
        this.serverChannel.configureBlocking(false);

        this.selectorLoop = new SelectorLoop(this::onAccept, this::onRead, null);
        this.selectorLoop.register(serverChannel, SelectionKey.OP_ACCEPT);
    }

    private void onAccept(SelectionKey key) {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        try {
            SocketChannel client;
            while ((client = server.accept()) != null) {
                client.configureBlocking(false);
                selectorLoop.register(
                        client,
                        SelectionKey.OP_READ,
                        ByteBuffer.allocate(MAX_NAME_BYTES)   // attachment = буфер имени
                );
            }
        } catch (IOException e) {
            // логгер; один неудачный accept не должен валить цикл
        }
    }

    private void onRead(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();

        try {
            int n = client.read(buf);
            if (n == -1) {
                client.close();
                return;
            }
            if (n == 0) {
                return;
            }

            int zeroPos = -1;
            for (int i = 0; i < buf.position(); i++) {
                if (buf.get(i) == NAME_TERMINATOR) {
                    zeroPos = i;
                    break;
                }
            }

            if (zeroPos < 0) {
                if (buf.position() == buf.capacity()) {
                    client.close();
                }
                return;
            }

            byte[] nameBytes = new byte[zeroPos];
            buf.get(0, nameBytes);
            String name = new String(nameBytes, StandardCharsets.US_ASCII);
            
            key.interestOps(0);

            requestQueue.put(new Request(name, client));

        } catch (IOException e) {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void run() {
        selectorLoop.loop();
    }

    @Override
    public void close() throws IOException {
        serverChannel.close();
        selectorLoop.close();
    }
}