import java.net.http.HttpClient;
import java.time.Duration;

public class Main {
    private static final String HOST = "http://localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String ROOT = "/";

    public static void main(String[] args) {
        String baseUrl = buildBaseUrl(args);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        new Spider(client, baseUrl).crawl(ROOT)
                .stream()
                .sorted()
                .forEach(System.out::println);
    }

    private static String buildBaseUrl(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        return HOST + ":" + port;
    }
}
