package org.example.app;

import org.example.common.AppConfig;
import org.example.common.BubbleSorter;
import org.example.common.DelayConfig;
import org.example.common.ListType;
import org.example.common.StepCounter;
import org.example.list.ArrayListBubbleSorter;
import org.example.list.LinkedListBubbleSorter;
import org.example.list.LockingLinkedList;
import org.example.list.SyncStringList;
import org.example.list.SynchronizedArrayList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Является точкой входа: собирает список и дочерние нити по конфигурации и
 * запускает либо интерактивный режим, либо режим замера.
 *
 * <p>Интерактивный режим: главный поток читает строки, дочерние нити сортируют
 * список бесконечным пузырьком. Завершение — по концу ввода (EOF/Ctrl+D/Ctrl+Z),
 * после чего печатается итоговое количество шагов.
 */
public final class Main {

    /**
     * Точка входа в приложение.
     *
     * @param args аргументы командной строки (см. README)
     * @throws Exception если запуск или чтение ввода завершились ошибкой
     */
    public static void main(String[] args) throws Exception {
        AppConfig config = parseArgs(args);
        if (config.isBenchmark()) {
            System.out.println(BenchmarkRunner.run(config).asLine());
            return;
        }
        runInteractive(config, System.in);
    }

    /**
     * Запускает интерактивный режим: поднимает сортировщики и читает ввод главным
     * потоком до конца потока, затем останавливает нити и печатает метрики.
     *
     * @param config конфигурация запуска
     * @param in     входной поток с командами пользователя
     * @throws Exception если чтение ввода завершилось ошибкой
     */
    public static void runInteractive(AppConfig config, InputStream in) throws Exception {
        StepCounter counter = new StepCounter();
        List<BubbleSorter> sorters = new ArrayList<>();
        SyncStringList list;

        if (config.listType() == ListType.LINKED) {
            LockingLinkedList linked = new LockingLinkedList();
            list = linked;
            for (int i = 0; i < config.threads(); i++) {
                sorters.add(new LinkedListBubbleSorter(linked, counter, config.delay()));
            }
        } else {
            SynchronizedArrayList array = new SynchronizedArrayList();
            list = array;
            for (int i = 0; i < config.threads(); i++) {
                sorters.add(new ArrayListBubbleSorter(array, counter, config.delay()));
            }
        }

        System.out.printf(
                "Список: %s | нитей: %d | задержки: между шагами=%dмс, внутри шага=%dмс%n",
                config.listType(), config.threads(),
                config.delay().betweenStepsMillis(), config.delay().insideStepMillis());
        System.out.println("Введите строку (пустая строка — показать список, Ctrl+D/Ctrl+Z — выход):");

        List<Thread> threads = SorterRunner.start(sorters, "sorter");
        try {
            new InputReader(list, new ListPrinter(), config.maxLineLength()).run(in);
        } finally {
            SorterRunner.shutdown(sorters, threads);
        }
        System.out.println("Итого: " + counter.summary());
    }

    /**
     * Разбирает аргументы командной строки в конфигурацию, подставляя значения по
     * умолчанию для пропущенных параметров.
     *
     * @param args аргументы вида {@code --key=value}
     * @return готовая конфигурация запуска
     * @throws IllegalArgumentException если значение аргумента некорректно
     */
    private static AppConfig parseArgs(String[] args) {
        AppConfig defaults = AppConfig.defaults();
        ListType type = defaults.listType();
        int threads = defaults.threads();
        long between = defaults.delay().betweenStepsMillis();
        long inside = defaults.delay().insideStepMillis();
        int maxLine = defaults.maxLineLength();
        Integer benchmark = null;
        Long duration = null;

        for (String arg : args) {
            String[] kv = arg.split("=", 2);
            String key = kv[0].replaceFirst("^--", "");
            String value = kv.length > 1 ? kv[1] : "";
            switch (key) {
                case "type", "list" -> type = ListType.fromName(value);
                case "threads" -> threads = Integer.parseInt(value);
                case "between" -> between = Long.parseLong(value);
                case "inside" -> inside = Long.parseLong(value);
                case "max-line", "maxLine" -> maxLine = Integer.parseInt(value);
                case "benchmark" -> benchmark = Integer.parseInt(value);
                case "duration" -> duration = Long.parseLong(value);
                default -> throw new IllegalArgumentException("Неизвестный аргумент: " + arg);
            }
        }

        return new AppConfig(type, threads, new DelayConfig(between, inside), maxLine, benchmark, duration);
    }
}
