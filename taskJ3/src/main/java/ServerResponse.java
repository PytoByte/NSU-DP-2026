import java.util.List;

public record ServerResponse(String message, List<String> successors) {
}
