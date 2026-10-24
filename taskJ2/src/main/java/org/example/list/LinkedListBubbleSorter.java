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

        while (true) {
            Node next = current.getNext();
            if (next == null || next.getNext() == null) {
                break;
            }

            if (sortFollowingPair(current)) {
                changed = true;
            }

            current = current.getNext();
            if (current == null) {
                break;
            }
            delay.sleepBetweenSteps();
        }

        return changed;
    }

    public boolean sortFollowingPair(Node before) throws InterruptedException {
        before.lock();
        try {
            Node a = before.getNext();
            if (a == null) {
                return false;
            }
            a.lock();
            try {
                Node b = a.getNext();
                if (b == null) {
                    return false;
                }
                b.lock();
                try {
                    delay.sleepInsideStep();
                    counter.incrementAndGet();
                    if (a.getValue().compareTo(b.getValue()) > 0) {
                        Node after = b.getNext();
                        before.setNext(b);
                        b.setNext(a);
                        a.setNext(after);
                        return true;
                    }
                    return false;
                } finally {
                    b.unlock();
                }
            } finally {
                a.unlock();
            }
        } finally {
            before.unlock();
        }
    }
}
