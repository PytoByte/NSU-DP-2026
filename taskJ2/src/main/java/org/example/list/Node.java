package org.example.list;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class Node {
    private final Lock lock = new ReentrantLock();
    private boolean head = false;
    private final String value;
    private volatile Node next;

    Node() {
        head = true;
        value = null;
    }


    Node(String value) {
        this.value = value;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public String getValue() {
        return value;
    }

    public Node getNext() {
        return next;
    }

    public void setNext(Node next) {
        this.next = next;
    }
}
