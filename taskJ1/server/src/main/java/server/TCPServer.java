package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
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

        ServerSocketChannel ch = ServerSocketChannel.open();
        try {
            ch.bind(new InetSocketAddress(port));
            ch.configureBlocking(false);
            this.selectorLoop = new SelectorLoop(this::onAccept, this::onRead, this::onWritable);
            this.selectorLoop.register(ch, SelectionKey.OP_ACCEPT);
            this.serverChannel = ch;
        } catch (IOException | RuntimeException e) {
            try {
                ch.close();
            } catch (IOException ignored) {}
            throw e;
        }
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
                System.out.println("New client " + client.getRemoteAddress().toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onRead(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        try {
            System.out.println("Reading name from " + client.getRemoteAddress().toString());
            String name = SocketIO.readName(client, buffer);
            if (name == null) {
                return;
            }
            System.out.println("Name for " + client.getRemoteAddress().toString() + " is " + name);

            key.interestOps(0);

            requestQueue.put(new KeyGeneratorRequest(
                    name,
                    new KeyGeneratorResponseCallback(key, selectorLoop, () -> closeKey(key))
            ));

        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
            closeKey(key);
        }
    }

    private void onWritable(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        try {
            System.out.println("Answering to " + client.getRemoteAddress().toString());
            if (SocketIO.tryWrite(client, buffer)) {
                closeKey(key);
            }
        } catch (CancelledKeyException e) {
            closeKey(key);
        } catch (IOException e) {
            e.printStackTrace();
            closeKey(key);
        }
    }

    private void closeKey(SelectionKey key) {
        try {
            System.out.println("Closing connection");
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