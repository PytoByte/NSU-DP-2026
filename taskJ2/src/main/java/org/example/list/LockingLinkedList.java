package org.example.list;

import org.example.common.SyncStringList;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class LockingLinkedList implements SyncStringList {

    private final Node head = new Node();

    @Override
    public void addFirst(String value) {
        Node newNode = new Node(value);

        head.lock();
        try {
            newNode.setNext(head.getNext());
            head.setNext(newNode);
        } finally {
            head.unlock();
        }
    }

    @Override
    public Iterator<String> iterator() {
        List<String> values = new LinkedList<>();

        Node current = head;
        current.lock();
        try {
            while (current.getNext() != null) {
                Node next = current.getNext();
                next.lock();
                values.add(next.getValue());
                current.unlock();
                current = next;
            }
        } finally {
            values.add(current.getValue());
            current.unlock();
        }

        return values.iterator();
    }

    Node getHead() {
        return head;
    }
}
