package org.example.app;

import org.example.common.AppConfig;
import org.example.common.BubbleSorter;
import org.example.common.ListType;
import org.example.list.ArrayListBubbleSorter;
import org.example.list.LinkedListBubbleSorter;
import org.example.list.LockingLinkedList;
import org.example.common.SyncStringList;
import org.example.list.SynchronizedArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class BenchmarkRunner {

    public record Report(
            ListType listType,
            int threads,
            int elements,
            int steps,
            boolean untilSorted,
            long timeMillis
    ) {
        @Override
        public String toString() {
            return String.format(
                    "%s | threads=%d | elements=%d | %s | steps=%d | %dms",
                    listType,
                    threads,
                    elements,
                    untilSorted ? "until-sorted" : "fixed-time",
                    steps,
                    timeMillis
            );
        }
    }

    public static Report run(AppConfig config) throws InterruptedException {
        int elements = config.benchmarkElements();
        boolean untilSorted = config.benchmarkDurationMillis() == null;

        AtomicInteger counter = new AtomicInteger();
        List<BubbleSorter> sorters = new ArrayList<>();

        if (config.listType() == ListType.LINKED) {
            LockingLinkedList linked = new LockingLinkedList();
            fillListWithRandomWords(linked, elements);
            for (int i = 0; i < config.threads(); i++) {
                sorters.add(new LinkedListBubbleSorter(linked, counter, config.delay(), untilSorted));
            }
        } else {
            SynchronizedArrayList array = new SynchronizedArrayList();
            fillListWithRandomWords(array, elements);
            for (int i = 0; i < config.threads(); i++) {
                sorters.add(new ArrayListBubbleSorter(array, counter, config.delay(), untilSorted));
            }
        }

        long startMillis = System.currentTimeMillis();
        try(SorterRunner sorterRunner = new SorterRunner(sorters)) {
            if (untilSorted) {
                sorterRunner.joinAll();
            } else {
                Thread.sleep(config.benchmarkDurationMillis());
            }
        }

        long timeMillis = System.currentTimeMillis() - startMillis;

        return new Report(
                config.listType(),
                config.threads(),
                elements,
                counter.get(),
                untilSorted,
                timeMillis
        );
    }

    private static void fillListWithRandomWords(SyncStringList list, int elements) {
        Random random = new Random(42);
        for (int i = 0; i < elements; i++) {
            list.addFirst(String.format("%s", generateRandomWord(random)));
        }
    }

    private static String generateRandomWord(Random random) {
        int length = 8;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) random.nextInt('A', 'z' + 1));
        }
        return sb.toString();
    }
}
