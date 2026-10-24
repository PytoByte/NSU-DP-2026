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

    public void joinAll() throws InterruptedException {
        for (Thread thread : threads) {
            thread.join();
        }
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
