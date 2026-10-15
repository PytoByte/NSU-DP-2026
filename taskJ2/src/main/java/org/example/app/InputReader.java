package org.example.app;

import org.example.list.SyncStringList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Выполняет роль родительской нити: читает строки пользователя и кладёт их в
 * начало списка, а по пустой строке печатает текущее состояние.
 */
public final class InputReader {

    private final SyncStringList list;
    private final ListPrinter printer;
    private final int maxLineLength;

    /**
     * Создаёт читатель для заданного списка.
     *
     * @param list          список, в начало которого кладутся строки
     * @param printer       объект, печатающий состояние списка
     * @param maxLineLength порог, после которого строка режется (не менее 1)
     */
    public InputReader(SyncStringList list, ListPrinter printer, int maxLineLength) {
        this.list = list;
        this.printer = printer;
        this.maxLineLength = Math.max(1, maxLineLength);
    }

    /**
     * Читает входной поток построчно до конца ввода.
     *
     * @param in входной поток (обычно стандартный ввод)
     * @throws IOException если при чтении потока произошла ошибка ввода-вывода
     */
    public void run(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            handleLine(line);
        }
    }

    /**
     * Обрабатывает одну введённую строку: пустая — печатает список, иначе режет
     * строку и кладёт части в начало списка.
     *
     * @param line введённая строка
     */
    private void handleLine(String line) {
        if (line.isEmpty()) {
            printer.print(list);
            return;
        }
        // Части длинной строки вставляем с конца, чтобы в списке они читались
        // в исходном порядке (каждая вставка идёт в начало).
        List<String> parts = split(line);
        for (int i = parts.size() - 1; i >= 0; i--) {
            list.addFirst(parts.get(i));
        }
    }

    /**
     * Режет строку на части длиной не более {@link #maxLineLength}.
     *
     * @param line исходная строка
     * @return список частей (одна часть, если строка короткая)
     */
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
