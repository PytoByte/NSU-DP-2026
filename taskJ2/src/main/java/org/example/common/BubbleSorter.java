package org.example.common;

/**
 * Задаёт контракт дочерней нити, сортирующей список «бесконечным пузырьком», и
 * позволяет корректно её останавливать.
 */
public interface BubbleSorter extends Runnable {

    /**
     * Попросить нить корректно завершиться.
     */
    void stop();

    /**
     * Сообщает, продолжает ли нить работу.
     *
     * @return {@code true}, если нить ещё не остановлена
     */
    boolean isRunning();
}
