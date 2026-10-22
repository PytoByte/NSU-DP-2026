import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResponseParser {
    private static final Pattern MESSAGE = Pattern.compile(
            "\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);
    private static final Pattern SUCCESSORS = Pattern.compile(
            "\"successors\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    public static ServerResponse parse(String body) {
        String message = extractMessage(body);
        List<String> successors = extractSuccessors(body);
        return new ServerResponse(message, successors);
    }

    private static String extractMessage(String body) {
        Matcher matcher = MESSAGE.matcher(body);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No message in response: " + body);
        }
        return unescape(matcher.group(1));
    }

    private static List<String> extractSuccessors(String body) {
        Matcher array = SUCCESSORS.matcher(body);
        List<String> paths = new ArrayList<>();
        if (!array.find()) {
            return paths;
        }
        Matcher string = STRING.matcher(array.group(1));
        while (string.find()) {
            paths.add(unescape(string.group(1)));
        }
        return paths;
    }

    private static String unescape(String raw) {
        StringBuilder result = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current == '\\' && i + 1 < raw.length()) {
                i = appendEscaped(raw, i, result);
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static int appendEscaped(String raw, int backslashIndex, StringBuilder result) {
        char escaped = raw.charAt(backslashIndex + 1);
        switch (escaped) {
            case 'n' -> result.append('\n');
            case 't' -> result.append('\t');
            case 'r' -> result.append('\r');
            case 'b' -> result.append('\b');
            case 'f' -> result.append('\f');
            case 'u' -> {
                String hex = raw.substring(backslashIndex + 2, backslashIndex + 6);
                result.append((char) Integer.parseInt(hex, 16));
                return backslashIndex + 5;
            }
            default -> result.append(escaped);
        }
        return backslashIndex + 1;
    }
}
