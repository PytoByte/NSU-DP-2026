package org.example.list;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Реализует собственный двусвязный список с поузловыми блокировками, чтобы нити
 * могли переставлять записи переустановкой ссылок без блокировки всего списка.
 *
 * <p>Инварианты:
 * <ul>
 *   <li>весь список во время сортировки не блокируется — блокировки только поузловые;</li>
 *   <li>при захвате нескольких узлов порядок всегда «от головы к хвосту», а захват
 *       идёт с таймаутом — это исключает взаимные блокировки;</li>
 *   <li>перестановка меняет только ссылки, содержимое строк не трогается;</li>
 *   <li>{@link #iterator()} отдаёт согласованный снимок, поэтому for-each не
 *       «рвётся» при параллельных перестановках.</li>
 * </ul>
 */
public final class LockingLinkedList implements SyncStringList {

    private static final long LOCK_TIMEOUT_MILLIS = 5;

    private volatile Node head;
    private final AtomicInteger size = new AtomicInteger();

    /**
     * Вставляет значение новым узлом в начало списка.
     *
     * @param value вставляемое значение
     */
    @Override
    public void addFirst(String value) {
        Node node = new Node(value);
        // Новый узел становится головой, то есть он «ближе к голове», чем current head,
        // поэтому захватываем его первым — порядок остаётся «от головы к хвосту».
        node.lock.lock();
        try {
            Node currentHead = head;
            node.next = currentHead;
            if (currentHead != null) {
                currentHead.lock.lock();
                try {
                    currentHead.prev = node;
                } finally {
                    currentHead.lock.unlock();
                }
            }
            head = node;
            size.incrementAndGet();
        } finally {
            node.lock.unlock();
        }
    }

    /**
     * Возвращает текущее количество записей.
     *
     * @return число записей в списке
     */
    @Override
    public int size() {
        return size.get();
    }

    /**
     * Возвращает итератор по согласованному снимку списка, пригодный для for-each.
     *
     * @return итератор по копии значений на момент вызова
     */
    @Override
    public Iterator<String> iterator() {
        return snapshot().iterator();
    }

    /**
     * Строит согласованный снимок значений, читая каждый узел под своим локом и
     * сверяя длину с {@link #size()}.
     *
     * @return список значений на момент построения снимка
     */
    private List<String> snapshot() {
        for (int attempt = 0; attempt < 128; attempt++) {
            int expected = size.get();
            List<String> values = new ArrayList<>(Math.max(expected, 4));
            Node cur = head;
            while (cur != null) {
                Node node = cur;
                Node next;
                node.lock.lock();
                try {
                    values.add(node.value);
                    next = node.next;
                } finally {
                    node.lock.unlock();
                }
                cur = next;
            }
            if (values.size() == expected) {
                return values;
            }
        }
        // Практически недостижимо: под постоянной вставкой просто берём то, что есть.
        List<String> values = new ArrayList<>();
        Node cur = head;
        while (cur != null) {
            values.add(cur.value);
            cur = cur.next;
        }
        return values;
    }

    // ------------------------------------------------------------------
    // Пакетный API для сортировщика
    // ------------------------------------------------------------------

    /**
     * Возвращает текущую голову списка.
     *
     * @return узел-голова или {@code null}, если список пуст
     */
    Node headNode() {
        return head;
    }

    /**
     * Переставляет соседние узлы {@code a} и {@code b} ({@code b == a.next}),
     * переустанавливая ссылки. Узлы вместе с их соседями захватываются в порядке
     * «от головы к хвосту» и с таймаутом, после чего состояние перепроверяется.
     *
     * <p>Если пару уже переставил или разъединил другой поток
     * ({@code a.next != b}), перестановка пропускается.
     *
     * @param a левый узел пары
     * @param b правый узел пары ({@code b == a.next})
     * @return {@code true}, если перестановка реально выполнена
     */
    boolean swapAdjacent(Node a, Node b) {
        while (true) {
            if (a.next != b) {
                return false;
            }
            Node before = a.prev;
            Node after = b.next;

            List<Node> locked = new ArrayList<>(4);
            addLock(locked, before);
            addLock(locked, a);
            addLock(locked, b);
            addLock(locked, after);

            // Захватываем с таймаутом: порядок «от головы к хвосту» берётся из
            // прочитанных ссылок и при гонке может оказаться несогласованным,
            // поэтому блокировка не должна ждать вечно — иначе возможна
            // взаимная блокировка. Таймаут её гарантированно разрывает.
            int acquired = tryLockAll(locked);
            if (acquired < locked.size()) {
                unlockFrom(locked, acquired);
                Thread.yield();
                continue;
            }

            boolean retry;
            try {
                if (a.next != b) {
                    return false;
                }
                retry = a.prev != before || b.next != after;
                if (!retry) {
                    if (before != null) {
                        before.next = b;
                    } else {
                        head = b;
                    }
                    if (after != null) {
                        after.prev = a;
                    }
                    b.prev = before;
                    a.prev = b;
                    b.next = a;
                    a.next = after;
                    return true;
                }
            } finally {
                unlockAll(locked);
            }
            // Соседи изменились, но пара всё ещё на месте — пробуем снова.
            Thread.yield();
        }
    }

    /**
     * Добавляет узел в список захвата, если он не {@code null} и ещё не добавлен.
     *
     * @param locked список узлов для захвата
     * @param node   добавляемый узел (может быть {@code null})
     */
    private static void addLock(List<Node> locked, Node node) {
        if (node != null && !containsIdentity(locked, node)) {
            locked.add(node);
        }
    }

    /**
     * Проверяет наличие узла в списке по ссылке (сравнение идентичности).
     *
     * @param nodes  список узлов
     * @param target искомый узел
     * @return {@code true}, если узел уже присутствует
     */
    private static boolean containsIdentity(List<Node> nodes, Node target) {
        for (Node node : nodes) {
            if (node == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * Захватывает узлы по порядку с таймаутом.
     *
     * @param nodes узлы в порядке захвата
     * @return число реально захваченных узлов
     */
    private static int tryLockAll(List<Node> nodes) {
        int acquired = 0;
        for (Node node : nodes) {
            try {
                if (node.lock.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    acquired++;
                } else {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return acquired;
    }

    /**
     * Освобождает первые {@code acquired} захваченных узлов в обратном порядке.
     *
     * @param nodes    узлы в порядке захвата
     * @param acquired число захваченных узлов
     */
    private static void unlockFrom(List<Node> nodes, int acquired) {
        for (int i = acquired - 1; i >= 0; i--) {
            nodes.get(i).lock.unlock();
        }
    }

    /**
     * Освобождает все узлы в обратном порядке захвата.
     *
     * @param nodes захваченные узлы
     */
    private static void unlockAll(List<Node> nodes) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            nodes.get(i).lock.unlock();
        }
    }
}
