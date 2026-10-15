package org.example.list;

import org.example.common.AbstractBubbleSorter;
import org.example.common.DelayConfig;
import org.example.common.StepCounter;

/**
 * Сортирует библиотечный {@link SynchronizedArrayList} «бесконечным пузырьком»,
 * проходя список по индексам; перестановку выполняет сам список под своим
 * монитором, задержки сохранены.
 */
public final class ArrayListBubbleSorter extends AbstractBubbleSorter {

    private final SynchronizedArrayList list;

    /**
     * Создаёт бесконечный сортировщик библиотечного списка.
     *
     * @param list    сортируемый список
     * @param counter общий счётчик шагов
     * @param delay   задержки сортировки
     */
    public ArrayListBubbleSorter(SynchronizedArrayList list, StepCounter counter, DelayConfig delay) {
        this(list, counter, delay, false);
    }

    /**
     * Создаёт сортировщик библиотечного списка с выбором режима остановки.
     *
     * @param list           сортируемый список
     * @param counter        общий счётчик шагов
     * @param delay          задержки сортировки
     * @param stopWhenSorted останавливаться ли после полной сортировки (режим замера)
     */
    public ArrayListBubbleSorter(SynchronizedArrayList list, StepCounter counter, DelayConfig delay,
                                 boolean stopWhenSorted) {
        super(counter, delay, stopWhenSorted);
        this.list = list;
    }

    /**
     * Делает один проход по индексам, пытаясь переставить каждую пару соседей.
     *
     * @return {@code true}, если в проходе была хотя бы одна перестановка
     * @throws InterruptedException если нить прервали во время задержки
     */
    @Override
    protected boolean onePass() throws InterruptedException {
        boolean changed = false;
        int n = list.snapshotSize();
        for (int i = 0; i + 1 < n; i++) {
            delay.sleepInsideStep();
            counter.recordAttempt();
            if (list.compareAndSwap(i)) {
                counter.recordSwap();
                changed = true;
            }
            delay.sleepBetweenSteps();
        }
        return changed;
    }
}
