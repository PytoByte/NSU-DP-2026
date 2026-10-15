package org.example.common;

/**
 * Реализует общий «двигатель» бесконечного пузырька: пока нить не остановлена,
 * она раз за разом выполняет полный проход по списку, оставляя конкретный способ
 * обхода подклассу.
 *
 * <p>Обычно ({@code stopWhenSorted == false}) алгоритм бесконечен. В режиме
 * замера нить сама останавливается после прохода, в котором не потребовалось
 * ни одной перестановки — значит, список упорядочен.
 */
public abstract class AbstractBubbleSorter implements BubbleSorter {

    protected final StepCounter counter;
    protected final DelayConfig delay;
    private final boolean stopWhenSorted;
    private volatile boolean running = true;

    /**
     * Создаёт сортировщик с общими для всех нитей счётчиком и задержками.
     *
     * @param counter        общий счётчик шагов
     * @param delay          задержки сортировки
     * @param stopWhenSorted останавливаться ли самой нити после полной сортировки
     */
    protected AbstractBubbleSorter(StepCounter counter, DelayConfig delay, boolean stopWhenSorted) {
        this.counter = counter;
        this.delay = delay;
        this.stopWhenSorted = stopWhenSorted;
    }

    /**
     * Выполняет проходы по списку, пока нить не остановлена.
     */
    @Override
    public final void run() {
        while (running) {
            boolean changed;
            try {
                changed = onePass();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (stopWhenSorted && !changed) {
                running = false;
            }
        }
    }

    /**
     * Делает один полный проход по списку, задерживаясь внутри и между шагами.
     *
     * @return {@code true}, если в проходе была хотя бы одна перестановка
     * @throws InterruptedException если нить прервали во время задержки
     */
    protected abstract boolean onePass() throws InterruptedException;

    /**
     * Просит нить завершиться после текущего прохода.
     */
    @Override
    public void stop() {
        running = false;
    }

    /**
     * Сообщает, продолжает ли нить работу.
     *
     * @return {@code true}, если нить ещё не остановлена
     */
    @Override
    public boolean isRunning() {
        return running;
    }
}
