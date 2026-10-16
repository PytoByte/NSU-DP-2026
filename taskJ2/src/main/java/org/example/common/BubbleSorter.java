package org.example.common;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Реализует общий «двигатель» бесконечного пузырька: пока нить не остановлена,
 * она раз за разом выполняет полный проход по списку, оставляя конкретный способ
 * обхода подклассу.
 *
 * <p>Обычно ({@code stopWhenSorted == false}) алгоритм бесконечен. В режиме
 * замера нить сама останавливается после прохода, в котором не потребовалось
 * ни одной перестановки — значит, список упорядочен.
 */
public abstract class BubbleSorter implements Runnable {

    protected final AtomicInteger counter;
    protected final DelayConfig delay;
    private final boolean stopWhenSorted;

    protected BubbleSorter(AtomicInteger counter, DelayConfig delay, boolean stopWhenSorted) {
        this.counter = counter;
        this.delay = delay;
        this.stopWhenSorted = stopWhenSorted;
    }

    @Override
    public final void run() {
        while (!Thread.currentThread().isInterrupted()) {
            boolean changed;
            try {
                changed = onePass();
            } catch (InterruptedException e) {
                return;
            }
            if (stopWhenSorted && !changed) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected abstract boolean onePass() throws InterruptedException;
}
