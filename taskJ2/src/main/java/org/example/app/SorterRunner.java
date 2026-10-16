package org.example.app;

import org.example.common.BubbleSorter;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public final class SorterRunner implements Closeable {

    private final List<Thread> threads;

    public SorterRunner(List<? extends BubbleSorter> sorters) {
        threads = new ArrayList<>(sorters.size());
        for (BubbleSorter sorter : sorters) {
            Thread thread = new Thread(sorter);
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }
    }

    public boolean joinAll(long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        for (Thread thread : threads) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            thread.join(remaining);
        }
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        for (Thread thread : threads) {
            thread.interrupt();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
