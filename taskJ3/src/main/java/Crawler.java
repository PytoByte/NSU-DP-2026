import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class Crawler {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_PARALLEL_REQUESTS = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 200;

    private final HttpClient client;
    private final URI baseUri;
    private final Gson gson = new Gson();
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final Semaphore requestsLimit = new Semaphore(MAX_PARALLEL_REQUESTS);
    private final AtomicInteger pending = new AtomicInteger();
    private final Object idle = new Object();

    public Crawler(HttpClient client, String baseUrl) {
        this.client = client;
        this.baseUri = URI.create(baseUrl);
    }

    public List<String> crawl(String root) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            start(root, executor);
            awaitIdle();
        }
        return List.copyOf(messages);
    }

    private void start(String path, ExecutorService executor) {
        if (!visited.add(path)) {
            return;
        }
        pending.incrementAndGet();
        executor.execute(() -> scan(path, executor));
    }

    private void scan(String path, ExecutorService executor) {
        try {
            ServerResponse response = gson.fromJson(fetchWithRetry(path), ServerResponse.class);
            messages.add(response.message());
            response.successors().forEach(child -> start(child, executor));
        } catch (Exception e) {
            System.err.println("Failed to fetch " + path + ": " + e.getMessage());
        } finally {
            finishOne();
        }
    }

    private String fetchWithRetry(String path) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return fetch(path);
            } catch (IOException e) {
                lastFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }
            }
        }
        throw lastFailure;
    }

    private String fetch(String path) throws IOException, InterruptedException {
        requestsLimit.acquire();
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return response.body();
        } finally {
            requestsLimit.release();
        }
    }

    private void awaitIdle() {
        synchronized (idle) {
            while (pending.get() > 0) {
                try {
                    idle.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void finishOne() {
        if (pending.decrementAndGet() == 0) {
            synchronized (idle) {
                idle.notifyAll();
            }
        }
    }
}
