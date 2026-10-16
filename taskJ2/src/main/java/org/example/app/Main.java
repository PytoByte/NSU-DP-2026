package org.example.app;

import org.example.common.AppConfig;
import org.example.common.BubbleSorter;
import org.example.common.DelayConfig;
import org.example.common.ListType;
import org.example.list.ArrayListBubbleSorter;
import org.example.list.LinkedListBubbleSorter;
import org.example.list.LockingLinkedList;
import org.example.common.SyncStringList;
import org.example.list.SynchronizedArrayList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Main {

    public static void main(String[] args) throws Exception {
        AppConfig config = parseArgs(args);
        if (config.isBenchmark()) {
            System.out.println(BenchmarkRunner.run(config));
            return;
        }
        runInteractive(config, System.in);
    }

    public static void runInteractive(AppConfig config, InputStream in) throws Exception {
        AtomicInteger counter = new AtomicInteger();
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
                "List type: %s | Sorters: %d | Delays: between=%dms, inside=%dms%n",
                config.listType(), config.threads(),
                config.delay().betweenStepsMillis(), config.delay().insideStepMillis());
        System.out.println("Enter string (empty - show list content, Ctrl+D/Ctrl+Z - exit):");

        try(SorterRunner ignored = new SorterRunner(sorters)) {
            new InputReader(list, new ListPrinter(), config.maxLineLength()).run(in);
        }
        System.out.println("Sort steps count: " + counter);
    }

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

        return new AppConfig(
                type,
                threads,
                new DelayConfig(between, inside),
                maxLine,
                benchmark,
                duration
        );
    }
}
