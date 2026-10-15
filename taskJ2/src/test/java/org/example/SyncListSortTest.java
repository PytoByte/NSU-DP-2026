package org.example;

import org.example.app.InputReader;
import org.example.app.ListPrinter;
import org.example.app.SorterRunner;
import org.example.common.BubbleSorter;
import org.example.common.DelayConfig;
import org.example.common.ListType;
import org.example.common.StepCounter;
import org.example.list.ArrayListBubbleSorter;
import org.example.list.LinkedListBubbleSorter;
import org.example.list.LockingLinkedList;
import org.example.list.SyncStringList;
import org.example.list.SynchronizedArrayList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет базовые свойства обеих реализаций списка: вставку в начало,
 * согласованный обход, полную сортировку и обработку ввода.
 */
class SyncListSortTest {

    private static final DelayConfig NO_DELAY = new DelayConfig(0, 0);

    /** Проверяет, что обе реализации вставляют в начало и обходятся в этом порядке. */
    @Test
    void prependsAndIterates() {
        for (SyncStringList list : List.of(new LockingLinkedList(), new SynchronizedArrayList())) {
            list.addFirst("b");
            list.addFirst("a");
            list.addFirst("c");

            assertEquals(3, list.size());
            List<String> values = new ArrayList<>();
            for (String value : list) {
                values.add(value);
            }
            assertEquals(List.of("c", "a", "b"), values);
        }
    }

    /** Проверяет, что собственный список полностью сортируется одной и несколькими нитями. */
    @Test
    void ownListSortsFully() throws InterruptedException {
        assertSortsFully(ListType.LINKED, 1);
        assertSortsFully(ListType.LINKED, 3);
    }

    /** Проверяет, что библиотечный список полностью сортируется одной и несколькими нитями. */
    @Test
    void libraryListSortsFully() throws InterruptedException {
        assertSortsFully(ListType.LIBRARY, 1);
        assertSortsFully(ListType.LIBRARY, 3);
    }

    /** Проверяет, что строка длиннее 80 символов режется на части не длиннее порога. */
    @Test
    void splitsLinesLongerThanMax() throws Exception {
        LockingLinkedList list = new LockingLinkedList();
        String longLine = "x".repeat(200);

        run(list, longLine + "\n");

        assertEquals(3, list.size());
        for (String value : list) {
            assertTrue(value.length() <= 80, "часть строки длиннее 80 символов");
        }
    }

    /** Проверяет, что пустая строка печатает текущее состояние списка. */
    @Test
    void emptyLinePrintsCurrentState() throws Exception {
        LockingLinkedList list = new LockingLinkedList();
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            run(list, "hello\n\n");
        } finally {
            System.setOut(original);
        }
        String output = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("hello"), "пустая строка должна печатать список");
    }

    // ------------------------------------------------------------------

    /**
     * Прогоняет через {@link InputReader} заданный ввод поверх списка.
     *
     * @param list  список, в который читается ввод
     * @param input содержимое входного потока
     * @throws Exception если чтение ввода завершилось ошибкой
     */
    private static void run(SyncStringList list, String input) throws Exception {
        new InputReader(list, new ListPrinter(), 80)
                .run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Наполняет список случайными строками и проверяет, что заданное число нитей
     * доводит его до полного порядка.
     *
     * @param type    проверяемая реализация списка
     * @param threads число дочерних нитей
     * @throws InterruptedException если ожидание нитей было прервано
     */
    private static void assertSortsFully(ListType type, int threads) throws InterruptedException {
        StepCounter counter = new StepCounter();
        List<BubbleSorter> sorters = new ArrayList<>();
        SyncStringList list;

        if (type == ListType.LINKED) {
            LockingLinkedList linked = new LockingLinkedList();
            list = linked;
            for (int i = 0; i < threads; i++) {
                sorters.add(new LinkedListBubbleSorter(linked, counter, NO_DELAY, true));
            }
        } else {
            SynchronizedArrayList array = new SynchronizedArrayList();
            list = array;
            for (int i = 0; i < threads; i++) {
                sorters.add(new ArrayListBubbleSorter(array, counter, NO_DELAY, true));
            }
        }

        Random random = new Random(123);
        for (int i = 0; i < 40; i++) {
            list.addFirst(String.format("%03d", random.nextInt(1000)));
        }

        List<Thread> worker = SorterRunner.start(sorters, "test");
        for (Thread thread : worker) {
            thread.join(20_000);
        }
        for (Thread thread : worker) {
            assertFalse(thread.isAlive(), "нить не завершилась: " + thread.getName());
        }

        assertEquals(40, list.size());
        assertTrue(isSorted(list), "список не отсортирован: " + snapshot(list));
    }

    /**
     * Проверяет, что список упорядочен лексикографически.
     *
     * @param list проверяемый список
     * @return {@code true}, если значения не убывают
     */
    private static boolean isSorted(SyncStringList list) {
        String previous = null;
        for (String value : list) {
            if (previous != null && previous.compareTo(value) > 0) {
                return false;
            }
            previous = value;
        }
        return true;
    }

    /**
     * Копирует значения списка в обычный список для диагностики.
     *
     * @param list проверяемый список
     * @return список значений на момент вызова
     */
    private static List<String> snapshot(SyncStringList list) {
        List<String> values = new ArrayList<>();
        for (String value : list) {
            values.add(value);
        }
        return values;
    }
}
