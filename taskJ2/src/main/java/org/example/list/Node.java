package org.example.list;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Представляет узел двусвязного списка, хранящий строку и собственную блокировку,
 * что позволяет нитям синхронизироваться по отдельным узлам, а не по всему списку.
 *
 * <p>Содержимое ({@link #value}) неизменяемо — при перестановке меняются только
 * ссылки {@link #prev}/{@link #next}, но никогда не содержимое.
 *
 * <p>Ссылки {@code volatile}, чтобы чтение из другого потока не давало
 * «порванных» значений; изменяются они только под {@link #lock}.
 */
final class Node {

    final String value;
    volatile Node prev;
    volatile Node next;
    final ReentrantLock lock = new ReentrantLock();

    /**
     * Создаёт узел с заданным значением и пустыми ссылками.
     *
     * @param value значение узла
     */
    Node(String value) {
        this.value = value;
    }
}
