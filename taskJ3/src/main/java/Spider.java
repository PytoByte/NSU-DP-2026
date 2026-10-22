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
import java.util.concurrent.atomic.AtomicInteger;

public class Spider {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final String baseUrl;
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger pending = new AtomicInteger();
    private final Object idle = new Object();

    public Spider(HttpClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    public List<String> crawl(String root) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            submit(root, executor);
            awaitIdle();
        }
        return List.copyOf(messages);
    }

    private void submit(String path, ExecutorService executor) {
        if (!visited.add(path)) {
            return;
        }
        pending.incrementAndGet();
        executor.execute(() -> visit(path, executor));
    }

    private void visit(String path, ExecutorService executor) {
        try {
            ServerResponse response = ResponseParser.parse(fetch(path));
            messages.add(response.message());
            response.successors().forEach(successor -> submit(successor, executor));
        } catch (Exception e) {
            System.err.println("Failed to fetch " + path + ": " + e.getMessage());
        } finally {
            finishOne();
        }
    }

    private String fetch(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(resolve(path)))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String resolve(String path) {
        return path.startsWith("/") ? baseUrl + path : baseUrl + "/" + path;
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
