package org.example.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Реализует библиотечный список {@link java.util.ArrayList} в обёртке
 * {@link Collections#synchronizedList(List)}, чтобы сравнивать его поведение и
 * число шагов с собственной реализацией при тех же задержках.
 *
 * <p>Вставка в начало делается через {@code add(0, value)} (это O(n)).
 * Перестановка «по ссылкам» здесь невозможна, поэтому элементы двигает
 * {@link Collections#swap(List, int, int)} — он переставляет ссылки на строки,
 * а не копирует их содержимое. Сравнение и перестановка выполняются одной
 * атомарной операцией под монитором списка, иначе между чтением и свопом
 * список мог бы измениться.
 */
public final class SynchronizedArrayList implements SyncStringList {

    private final List<String> list = Collections.synchronizedList(new ArrayList<>());

    /**
     * Вставляет значение в начало списка.
     *
     * @param value вставляемое значение
     */
    @Override
    public void addFirst(String value) {
        list.add(0, value);
    }

    /**
     * Возвращает текущее количество записей.
     *
     * @return число записей в списке
     */
    @Override
    public int size() {
        return list.size();
    }

    /**
     * Возвращает итератор по снимку списка, снятому под его монитором.
     *
     * @return итератор по копии значений на момент вызова
     */
    @Override
    public Iterator<String> iterator() {
        synchronized (list) {
            return new ArrayList<>(list).iterator();
        }
    }

    // ------------------------------------------------------------------
    // Пакетный API для сортировщика
    // ------------------------------------------------------------------

    /**
     * Возвращает текущий размер списка.
     *
     * @return число записей
     */
    int snapshotSize() {
        return list.size();
    }

    /**
     * Атомарно сравнивает элементы с индексами {@code i} и {@code i + 1} и, если
     * они стоят не по порядку, меняет их местами.
     *
     * @param i индекс левого элемента пары
     * @return {@code true}, если перестановка выполнена; {@code false}, если
     *         элементы уже упорядочены или индекс вышел за границы
     */
    boolean compareAndSwap(int i) {
        synchronized (list) {
            if (i < 0 || i + 1 >= list.size()) {
                return false;
            }
            if (list.get(i).compareTo(list.get(i + 1)) > 0) {
                Collections.swap(list, i, i + 1);
                return true;
            }
            return false;
        }
    }
}
