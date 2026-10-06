package org.example.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.BlockingQueue;

/**
 * Берёт Response из очереди и отправляет клиенту приватный ключ и сертификат.
 * Сама запись — через SelectorLoop (non-blocking), поэтому медленный клиент
 * не блокирует воркер-генератор и не блокирует этот поток-диспетчер.
 */
public class TCPResponseSender implements Runnable, AutoCloseable {

    private final SelectorLoop selectorLoop;
    private final BlockingQueue<Response> responseQueue;
    private final Thread dispatcher;

    private volatile boolean running = true;

    public TCPResponseSender(BlockingQueue<Response> responseQueue) throws IOException {
        this.responseQueue = responseQueue;
        // только write-хендлер, accept и read нам не нужны
        this.selectorLoop = new SelectorLoop(null, null, this::onWritable);
        this.dispatcher = new Thread(this::dispatchLoop, "response-dispatcher");
        this.dispatcher.setDaemon(true);
    }

    // ---------- поток-диспетчер: очередь -> регистрация на запись ----------

    private void dispatchLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            Response response;
            try {
                response = responseQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            registerResponse(response);
        }
    }

    private void registerResponse(Response response) {
        SocketChannel channel = response.socketChannel();
        try {
            if (!response.isSuccess()) {
                // клиенту нечего отправлять — просто закрываем
                closeQuietly(channel);
                return;
            }

            ByteBuffer buffer = encode(response);
            selectorLoop.wakeup();                       // разбудить select()
            selectorLoop.register(channel, SelectionKey.OP_WRITE, buffer);
        } catch (IOException | CertificateEncodingException e) {
            closeQuietly(channel);
        }
    }

    // ---------- сериализация ответа ----------

    private ByteBuffer encode(Response response) throws IOException, CertificateEncodingException {
        byte[] keyBytes  = response.keyPair().getPrivate().getEncoded(); // PKCS#8 DER
        byte[] certBytes = response.certificate().getEncoded();          // X.509 DER

        ByteBuffer buf = ByteBuffer.allocate(4 + keyBytes.length + 4 + certBytes.length);
        buf.putInt(keyBytes.length).put(keyBytes);
        buf.putInt(certBytes.length).put(certBytes);
        buf.flip();
        return buf;
    }

    // ---------- запись в сокет ----------

    private void onWritable(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        try {
            channel.write(buffer);
            if (!buffer.hasRemaining()) {
                key.cancel();          // больше писать нечего
                channel.close();
            }
            // если hasRemaining() == true — ждём следующего OP_WRITE
        } catch (IOException e) {
            key.cancel();
            closeQuietly(channel);
        }
    }

    // ---------- жизненный цикл ----------

    @Override
    public void run() {
        dispatcher.start();
        selectorLoop.loop();           // блокирует текущий поток до close()
    }

    @Override
    public void close() throws IOException {
        running = false;
        dispatcher.interrupt();
        selectorLoop.wakeup();
        selectorLoop.close();
    }

    private static void closeQuietly(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}