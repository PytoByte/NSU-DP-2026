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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Выполняет headless-прогон, который наполняет список фиксированным набором
 * строк и измеряет суммарное число шагов сортировки — это нужно для
 * воспроизводимого сравнения реализаций в отчёте.
 *
 * <p>Два сценария:
 * <ul>
 *   <li>без {@code --duration} — нити сами останавливаются, когда список
 *       упорядочен; возвращается полное число шагов до сортировки;</li>
 *   <li>с {@code --duration=MS} — нити работают фиксированное время (бесконечный
 *       пузырёк), возвращается число шагов за это окно.</li>
 * </ul>
 */
public final class BenchmarkRunner {

    public static final long DEFAULT_TIMEOUT_MS = 120_000;

    /**
     * Хранит результат одного замера.
     *
     * @param listType    проверявшаяся реализация списка
     * @param threads     число нитей
     * @param elements    число записей
     * @param attempts    суммарное число шагов
     * @param swaps       суммарное число перестановок
     * @param untilSorted выполнялся ли сценарий «до полной сортировки»
     * @param wallMillis  затраченное «настенное» время в миллисекундах
     */
    public record Report(ListType listType, int threads, int elements, long attempts, long swaps,
                         boolean untilSorted, long wallMillis) {

        /**
         * Формирует однострочное представление результата.
         *
         * @return читаемая строка с параметрами и метриками
         */
        public String asLine() {
            return String.format(
                    "%-8s | threads=%d | elements=%-4d | %s | steps=%-8d | swaps=%-6d | %dms",
                    listType, threads, elements,
                    untilSorted ? "until-sorted" : "fixed-time",
                    attempts, swaps, wallMillis);
        }
    }

    private BenchmarkRunner() {
    }

    /**
     * Выполняет замер по конфигурации и возвращает его результат.
     *
     * @param config конфигурация запуска (тип списка, число нитей, задержки, параметры замера)
     * @return результат замера
     * @throws InterruptedException если ожидание нитей было прервано
     * @throws IllegalStateException если сортировка не завершилась за отведённый таймаут
     */
    public static Report run(AppConfig config) throws InterruptedException {
        int elements = config.benchmarkElements() != null ? config.benchmarkElements() : 20;
        Long duration = config.benchmarkDurationMillis();
        boolean untilSorted = duration == null;
        long timeout = duration != null ? duration : Long.getLong("bench.timeout", 30_000L);

        StepCounter counter = new StepCounter();
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

        long start = System.currentTimeMillis();
        List<Thread> threads = SorterRunner.start(sorters, "bench");
        if (untilSorted) {
            boolean finished = joinAll(threads, timeout);
            if (!finished) {
                dumpAliveThreads(threads);
                throw new IllegalStateException("Сортировка не завершилась за " + timeout + " мс");
            }
            SorterRunner.shutdown(sorters, threads);
        } else {
            Thread.sleep(duration);
            SorterRunner.shutdown(sorters, threads);
        }
        long wall = System.currentTimeMillis() - start;

        return new Report(config.listType(), config.threads(), elements,
                counter.attempts(), counter.swaps(), untilSorted, wall);
    }

    /**
     * Ожидает завершения всех нитей в пределах общего таймаута.
     *
     * @param threads нити, которых нужно дождаться
     * @param timeout общий лимит ожидания в миллисекундах
     * @return {@code true}, если все нити завершились за отведённое время
     * @throws InterruptedException если ожидание было прервано
     */
    private static boolean joinAll(List<Thread> threads, long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        for (Thread thread : threads) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            thread.join(remaining);
        }
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Печатает стеки живых нитей — диагностика при незавершившейся сортировке.
     *
     * @param threads проверяемые нити
     */
    private static void dumpAliveThreads(List<Thread> threads) {
        System.err.println("=== Зависшие нити ===");
        for (Thread thread : threads) {
            if (!thread.isAlive()) {
                continue;
            }
            System.err.println("Нить " + thread.getName() + " state=" + thread.getState());
            for (StackTraceElement element : thread.getStackTrace()) {
                System.err.println("    at " + element);
            }
        }
    }

    /**
     * Наполняет список заданным числом случайных строк с фиксированным seed.
     *
     * @param list     наполняемый список
     * @param elements число записей
     */
    private static void fill(SyncStringList list, int elements) {
        Random random = new Random(42); // фиксированный seed -> воспроизводимость
        for (int i = 0; i < elements; i++) {
            list.addFirst(String.format("%04d-%s", random.nextInt(10_000), randomLetters(random, 8)));
        }
    }

    /**
     * Генерирует строку из случайных строчных латинских букв.
     *
     * @param random генератор случайных чисел
     * @param length длина строки
     * @return случайная строка заданной длины
     */
    private static String randomLetters(Random random, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }
}
