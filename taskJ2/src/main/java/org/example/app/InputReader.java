package org.example.app;

import org.example.common.SyncStringList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class InputReader {

    private final SyncStringList list;
    private final ListPrinter printer;
    private final int maxLineLength;

    public InputReader(SyncStringList list, ListPrinter printer, int maxLineLength) {
        this.list = list;
        this.printer = printer;
        this.maxLineLength = Math.max(1, maxLineLength);
    }

    public void run(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            handleLine(line);
        }
    }

    private void handleLine(String line) {
        if (line.isEmpty()) {
            printer.print(list);
            return;
        }

        List<String> parts = split(line);
        for (int i = parts.size() - 1; i >= 0; i--) {
            list.addFirst(parts.get(i));
        }
    }

    private List<String> split(String line) {
        if (line.length() <= maxLineLength) {
            return List.of(line);
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < line.length(); i += maxLineLength) {
            parts.add(line.substring(i, Math.min(line.length(), i + maxLineLength)));
        }
        return parts;
    }
}
