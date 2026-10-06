package org.example.server;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    public static void main(String[] args) throws Exception {
        Config cfg = Config.parse(args);

        // Регистрация BC происходит в static-блоке KeyGenerator при загрузке класса.
        // До этого момента провайдер может быть null, поэтому сначала создаём один
        // воркер (или просто загружаем класс), потом смотрим.
        System.out.println("BC provider before load: " + Security.getProvider("BC"));

        PrivateKey signingKey = loadSigningKey(
                cfg.keystorePath(), cfg.keystorePassword(),
                cfg.keyAlias(),    cfg.keyPassword());

        // Общие структуры
        BlockingQueue<Request>  requestQueue  = new LinkedBlockingQueue<>();
        BlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();
        ConcurrentHashMap<String, CompletableFuture<KeyMaterial>> cache = new ConcurrentHashMap<>();

        // 1. Приём запросов (один селектор-поток внутри receiver.run())
        TCPRequestReceiver receiver = new TCPRequestReceiver(cfg.port(), requestQueue);
        Thread receiverThread = new Thread(receiver, "tcp-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();

        // 2. Пул воркеров-генераторов
        List<KeyGenerator> generators = new ArrayList<>(cfg.workers());
        List<Thread>      genThreads = new ArrayList<>(cfg.workers());
        for (int i = 0; i < cfg.workers(); i++) {
            KeyGenerator g = new KeyGenerator(
                    requestQueue, responseQueue, cache, signingKey, cfg.issuerDn());
            Thread t = new Thread(g, "key-generator-" + i);
            t.setDaemon(true);
            t.start();
            generators.add(g);
            genThreads.add(t);
        }

        System.out.println("BC provider after load:  " + Security.getProvider("BC"));

        // 3. Отправка ответов (sender.run() внутри поднимает ещё поток-диспетчер)
        TCPResponseSender sender = new TCPResponseSender(responseQueue);
        Thread senderThread = new Thread(sender, "tcp-sender");
        senderThread.setDaemon(true);
        senderThread.start();

        System.out.printf("Server started: port=%d, workers=%d, issuer=%s%n",
                cfg.port(), cfg.workers(), cfg.issuerDn());

        // 4. Корректное завершение по Ctrl+C / kill
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down...");
            try { receiver.close(); } catch (Exception ignored) {}
            try { sender.close();   } catch (Exception ignored) {}
            generators.forEach(KeyGenerator::shutdown);
            // take() не просыпается от running=false — будим явным interrupt
            genThreads.forEach(Thread::interrupt);
        }, "shutdown-hook"));
    }

    private static PrivateKey loadSigningKey(String path,
                                             String storePass,
                                             String alias,
                                             String keyPass) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            ks.load(in, storePass.toCharArray());
        }
        return (PrivateKey) ks.getKey(alias, keyPass.toCharArray());
    }

    // ---------- конфиг ----------

    record Config(
            int port,
            int workers,
            String issuerDn,
            String keystorePath,
            String keystorePassword,
            String keyAlias,
            String keyPassword
    ) {
        static Config parse(String[] args) {
            int port = 5555;
            int workers = Runtime.getRuntime().availableProcessors();
            String issuerDn = "CN=KeyServer, O=Example, C=RU";
            String keystorePath = "server.p12";
            String keystorePassword = "changeit";
            String keyAlias = "server";
            String keyPassword = "changeit";

            for (int i = 0; i < args.length - 1; i += 2) {
                String k = args[i];
                String v = args[i + 1];
                switch (k) {
                    case "--port"              -> port = Integer.parseInt(v);
                    case "--workers"           -> workers = Integer.parseInt(v);
                    case "--issuer"            -> issuerDn = v;
                    case "--keystore"          -> keystorePath = v;
                    case "--keystore-password" -> keystorePassword = v;
                    case "--key-alias"         -> keyAlias = v;
                    case "--key-password"      -> keyPassword = v;
                    default -> throw new IllegalArgumentException("unknown arg: " + k);
                }
            }
            if (workers < 1) {
                throw new IllegalArgumentException("--workers must be >= 1");
            }
            return new Config(port, workers, issuerDn,
                    keystorePath, keystorePassword, keyAlias, keyPassword);
        }
    }
}