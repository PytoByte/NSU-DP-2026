package org.example.app;

import org.example.list.SyncStringList;

/**
 * Печатает состояние списка стандартным циклом for-each, как требует задание
 * ({@link SyncStringList} реализует {@link Iterable}).
 */
public final class ListPrinter {

    /**
     * Печатает размер и все записи списка.
     *
     * @param list список для печати
     */
    public void print(SyncStringList list) {
        System.out.println("--- список, записей: " + list.size() + " ---");
        for (String value : list) {
            System.out.println(value);
        }
        System.out.println("--- конец списка ---");
    }
}
