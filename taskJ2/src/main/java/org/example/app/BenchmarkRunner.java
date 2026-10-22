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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class BenchmarkRunner {

    public static final long DEFAULT_TIMEOUT_MS = 120_000;

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
        int elements = config.benchmarkElements() != null ? config.benchmarkElements() : 20;
        Long duration = config.benchmarkDurationMillis();
        boolean untilSorted = duration == null;
        long timeout = duration != null ? duration :
                config.delay().insideStepMillis() * config.delay().betweenStepsMillis()
                        * config.benchmarkElements();

        AtomicInteger counter = new AtomicInteger();
        DelayConfig delay = config.delay();
        List<BubbleSorter> sorters = new ArrayList<>();

        if (config.listType() == ListType.LINKED) {
            LockingLinkedList linked = new LockingLinkedList();
            fill(linked, elements);
            for (int i = 0; i < config.threads(); i++) {
                sorters.add(new LinkedListBubbleSorter(linked, counter, delay, untilSorted));
            }
        } else {
            SynchronizedArrayList array = new SynchronizedArrayList();
            fill(array, elements);
            for (int i = 0; i < config.threads(); i++) {
                sorters.add(new ArrayListBubbleSorter(array, counter, delay, untilSorted));
            }
        }

        long startMillis = System.currentTimeMillis();
        try(SorterRunner sorterRunner = new SorterRunner(sorters)) {
            if (untilSorted) {
                boolean finished = sorterRunner.joinAll(timeout);
                if (!finished) {
                    throw new IllegalStateException("Sort timeout: " + timeout + " ms");
                }
            } else {
                Thread.sleep(duration);
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

    private static void fill(SyncStringList list, int elements) {
        Random random = new Random(42);
        for (int i = 0; i < elements; i++) {
            list.addFirst(
                    String.format("%04d-%s",
                            random.nextInt(10_000),
                            randomLetters(random, 8)
                    )
            );
        }
    }

    private static String randomLetters(Random random, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }
}
