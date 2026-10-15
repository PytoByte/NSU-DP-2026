package org.example.list;

import org.example.common.AbstractBubbleSorter;
import org.example.common.DelayConfig;
import org.example.common.StepCounter;

/**
 * Сортирует собственный связный список «бесконечным пузырьком», проходя его по
 * ссылкам от головы к хвосту и переставляя соседей через
 * {@link LockingLinkedList#swapAdjacent}.
 */
public final class LinkedListBubbleSorter extends AbstractBubbleSorter {

    private final LockingLinkedList list;

    /**
     * Создаёт бесконечный сортировщик связного списка.
     *
     * @param list    сортируемый список
     * @param counter общий счётчик шагов
     * @param delay   задержки сортировки
     */
    public LinkedListBubbleSorter(LockingLinkedList list, StepCounter counter, DelayConfig delay) {
        this(list, counter, delay, false);
    }

    /**
     * Создаёт сортировщик связного списка с выбором режима остановки.
     *
     * @param list           сортируемый список
     * @param counter        общий счётчик шагов
     * @param delay          задержки сортировки
     * @param stopWhenSorted останавливаться ли после полной сортировки (режим замера)
     */
    public LinkedListBubbleSorter(LockingLinkedList list, StepCounter counter, DelayConfig delay,
                                  boolean stopWhenSorted) {
        super(counter, delay, stopWhenSorted);
        this.list = list;
    }

    /**
     * Делает один проход по списку, пытаясь переставить каждую пару соседей.
     *
     * @return {@code true}, если в проходе была хотя бы одна перестановка
     * @throws InterruptedException если нить прервали во время задержки
     */
    @Override
    protected boolean onePass() throws InterruptedException {
        boolean changed = false;
        Node a = list.headNode();

        while (a != null) {
            Node b = a.next;
            if (b == null) {
                break;
            }

            delay.sleepInsideStep();
            counter.recordAttempt();
            if (a.value.compareTo(b.value) > 0 && list.swapAdjacent(a, b)) {
                counter.recordSwap();
                changed = true;
                // После свопа a сдвинулся на место b, то есть уже стоит в
                // начале следующей пары — оставляем a как есть.
            } else {
                // Следующая пара начинается со следующего элемента. Ссылку
                // читаем заново: параллельный своп мог изменить соседа.
                a = a.next;
            }
            delay.sleepBetweenSteps();
        }
        return changed;
    }
}
