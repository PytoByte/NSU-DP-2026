package org.example.server;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java Main <port> <workers> <issuerDN> " +
                    "[signingKeyPath] [signingKeyPassword]");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        int workers = Integer.parseInt(args[1]);
        String issuerDn = args[2];
        Path signingKeyPath = Path.of(args.length > 3 ? args[3] : "server.key");
        char[] signingKeyPassword = args.length > 4 ? args[4].toCharArray() : null;

        PrivateKey signingKey = SigningKeyLoader.load(signingKeyPath, signingKeyPassword);

        BlockingQueue<KeyGeneratorRequest> requestQueue = new LinkedBlockingQueue<>();
        ConcurrentHashMap<String, CompletableFuture<KeyMaterial>> cache = new ConcurrentHashMap<>();

        TCPServer server = new TCPServer(port, requestQueue);
        Thread receiverThread = new Thread(server, "tcp-server");
        receiverThread.setDaemon(true);
        receiverThread.start();

        List<KeyGenerator> generators = new ArrayList<>(workers);
        List<Thread> generatorThreads = new ArrayList<>(workers);

        for (int i = 0; i < workers; i++) {
            KeyGenerator generator = new KeyGenerator(
                    requestQueue, cache, signingKey, issuerDn);
            Thread t = new Thread(generator, "key-gen-" + i);
            t.setDaemon(true);
            t.start();
            generators.add(generator);
            generatorThreads.add(t);
        }

        System.out.printf("Server started: port=%d, workers=%d, issuer=%s%n",
                port, workers, issuerDn);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down...");
            try {
                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            generators.forEach(KeyGenerator::shutdown);
            generatorThreads.forEach(Thread::interrupt);
        }, "shutdown-hook"));
    }
}