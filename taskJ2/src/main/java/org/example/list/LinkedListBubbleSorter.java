package org.example.list;

import org.example.common.BubbleSorter;
import org.example.common.DelayConfig;

import java.util.concurrent.atomic.AtomicInteger;

public final class LinkedListBubbleSorter extends BubbleSorter {

    private final LockingLinkedList list;

    public LinkedListBubbleSorter(
            LockingLinkedList list,
            AtomicInteger counter,
            DelayConfig delay
    ) {
        this(list, counter, delay, false);
    }

    public LinkedListBubbleSorter(
            LockingLinkedList list,
            AtomicInteger counter,
            DelayConfig delay,
            boolean stopWhenSorted
    ) {
        super(counter, delay, stopWhenSorted);
        this.list = list;
    }

    @Override
    protected boolean onePass() throws InterruptedException {
        boolean changed = false;
        Node current = list.getHead();

        while (current.getNext() != null) {
            sortFollowingPair(current);
            current = current.getNext();
            delay.sleepBetweenSteps();
        }

        return changed;
    }

    public void sortFollowingPair(Node before) throws InterruptedException {
        before.lock();
        try {
            Node a = before.getNext();
            a.lock();
            try {
                Node b = a.getNext();
                b.lock();
                try {
                    if (a.getValue().compareTo(b.getValue()) > 0) {
                        before.setNext(b);
                        b.setNext(a);
                        a.setNext(b.getNext());
                    }
                    delay.sleepInsideStep();
                    counter.incrementAndGet();
                } finally {
                    b.unlock();
                }
            } finally {
                a.unlock();
            }
        } catch (NullPointerException ignored) {

        } finally {
            before.unlock();
        }
    }
}
