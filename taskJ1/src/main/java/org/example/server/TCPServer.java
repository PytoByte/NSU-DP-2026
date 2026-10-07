package org.example.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;

public class TCPServer implements Runnable, AutoCloseable {

    private static final int MAX_NAME_BYTES = 1024;

    private final SelectorLoop selectorLoop;
    private final ServerSocketChannel serverChannel;
    private final BlockingQueue<KeyGeneratorRequest> requestQueue;

    public TCPServer(int port, BlockingQueue<KeyGeneratorRequest> requestQueue) throws IOException {
        this.requestQueue = requestQueue;

        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(port));
        this.serverChannel.configureBlocking(false);

        this.selectorLoop = new SelectorLoop(this::onAccept, this::onRead, this::onWritable);
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
                        ByteBuffer.allocate(MAX_NAME_BYTES)
                );
            }
        } catch (IOException e) {
            // логгер
        }
    }

    private void onRead(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        try {
            String name = SocketIO.readName(client, buffer);
            if (name == null) {
                return;
            }

            key.interestOps(0);
            requestQueue.put(new KeyGeneratorRequest(name, new KeyGeneratorResponseCallback(
                    key, selectorLoop, () -> closeKey(key)
            )));

        } catch (IOException e) {
            closeKey(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeKey(key);
        }
    }

    private void onWritable(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        try {
            if (SocketIO.tryWrite(client, buffer)) {
                closeKey(key);
            }
        } catch (IOException e) {
            closeKey(key);
        }
    }

    private void closeKey(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
        key.cancel();
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