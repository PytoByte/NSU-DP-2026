package org.example.common;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Считает шаги алгоритма сортировки; разделяется всеми дочерними нитями, чтобы
 * получить суммарную статистику для отчёта.
 *
 * <p>Под шагом понимается попытка поменять местами два соседних элемента.
 * Отдельно считаются реально выполненные перестановки.
 */
public final class StepCounter {

    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong swaps = new AtomicLong();

    /**
     * Зафиксировать шаг алгоритма (попытку перестановки).
     */
    public void recordAttempt() {
        attempts.incrementAndGet();
    }

    /**
     * Зафиксировать реально выполненную перестановку.
     */
    public void recordSwap() {
        swaps.incrementAndGet();
    }

    /**
     * Возвращает суммарное число шагов всех нитей.
     *
     * @return количество зафиксированных попыток перестановки
     */
    public long attempts() {
        return attempts.get();
    }

    /**
     * Возвращает суммарное число выполненных перестановок.
     *
     * @return количество реальных перестановок
     */
    public long swaps() {
        return swaps.get();
    }

    /**
     * Сбрасывает оба счётчика в ноль.
     */
    public void reset() {
        attempts.set(0);
        swaps.set(0);
    }

    /**
     * Формирует краткую строку со значениями счётчиков.
     *
     * @return строку вида {@code "steps(attempts)=..., swaps=..."}
     */
    public String summary() {
        return "steps(attempts)=" + attempts() + ", swaps=" + swaps();
    }
}
