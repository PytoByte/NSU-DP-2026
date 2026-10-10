package server;

import org.bouncycastle.asn1.x500.X500Name;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: <port> <workers> <issuerDN> [keyPath] [keyPassword]");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        int workers = Integer.parseInt(args[1]);
        X500Name issuer = new X500Name(args[2]);
        Path keyPath = Path.of(args.length > 3 ? args[3] : "server.key");
        char[] keyPassword = args.length > 4 ? args[4].toCharArray() : null;

        PrivateKey signingKey = SigningKeyLoader.load(keyPath, keyPassword);
        var requestQueue = new LinkedBlockingQueue<KeyRequest>();
        var cache = new ConcurrentHashMap<String, CompletableFuture<KeyResponse>>();

        var server = new TCPServer(port, requestQueue);
        var serverThread = new Thread(server, "tcp-server");
        serverThread.start();

        var generators = Executors.newFixedThreadPool(workers);
        for (int i = 0; i < workers; i++) {
            generators.submit(new KeyGenerator(requestQueue, cache, signingKey, issuer));
        }

        System.out.printf("Server started: port=%d, workers=%d, issuer=%s%n",
                port, workers, issuer);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (Exception ignored) {}
            generators.shutdownNow();
        }));

        serverThread.join();
    }
}