package org.example.server;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.function.Consumer;

public class SelectorLoop implements AutoCloseable {
    private final Selector selector;
    private final Consumer<SelectionKey> acceptHandler;
    private final Consumer<SelectionKey> readHandler;
    private final Consumer<SelectionKey> writeHandler;
    private boolean running = true;

    public SelectorLoop(
            Consumer<SelectionKey> acceptHandler,
            Consumer<SelectionKey> readHandler,
            Consumer<SelectionKey> writeHandler
    ) throws IOException {
        this.selector = Selector.open();
        this.acceptHandler = acceptHandler != null ? acceptHandler : key -> {};
        this.readHandler = readHandler != null ? readHandler : key -> {};
        this.writeHandler = writeHandler != null ? writeHandler : key -> {};
    }

    public SelectionKey register(SelectableChannel channel, int ops) throws IOException {
        channel.configureBlocking(false);
        return channel.register(selector, ops);
    }

    public SelectionKey register(SelectableChannel channel, int ops, Object attachment) throws IOException {
        channel.configureBlocking(false);
        return channel.register(selector, ops, attachment);
    }

    public void wakeup() {
        selector.wakeup();
    }

    public void loop() {
        while (running) {
            selectWithExceptionsHandling();
        }
    }

    private void selectWithExceptionsHandling() {
        try {
            select();
        } catch (IOException e) {
            running = false;
            e.printStackTrace();
        } catch (Exception e) {
            running = false;
            throw new RuntimeException(e);
        }
    }

    private void select() throws IOException {
        selector.select();

        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            it.remove();
            if (!key.isValid()) {
                continue;
            }

            if (key.isAcceptable()) {
                acceptHandler.accept(key);
            }
            if (key.isReadable()) {
                readHandler.accept(key);
            }
            if (key.isWritable()) {
                writeHandler.accept(key);
            }
        }
    }

    @Override
    public void close() throws IOException {
        selector.close();
    }
}