package org.example.list;

import org.example.common.BubbleSorter;
import org.example.common.DelayConfig;

import java.util.concurrent.atomic.AtomicInteger;

public final class ArrayListBubbleSorter extends BubbleSorter {

    private final SynchronizedArrayList list;

    public ArrayListBubbleSorter(
            SynchronizedArrayList list,
            AtomicInteger counter,
            DelayConfig delay
    ) {
        this(list, counter, delay, false);
    }

    public ArrayListBubbleSorter(
            SynchronizedArrayList list,
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
        int n = list.size();
        for (int i = 0; i + 1 < n; i++) {
            delay.sleepInsideStep();
            if (compareAndSwap(i)) {
                changed = true;
            }
            delay.sleepBetweenSteps();
        }
        return changed;
    }

    boolean compareAndSwap(int i) {
        synchronized (list) {
            if (i < 0 || i + 1 >= list.size()) {
                return false;
            }
            counter.incrementAndGet();
            if (list.get(i).compareTo(list.get(i + 1)) > 0) {
                list.swap(i, i + 1);
                return true;
            }
            return false;
        }
    }
}
