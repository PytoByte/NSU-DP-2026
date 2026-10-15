package org.example.app;

import org.example.common.BubbleSorter;

import java.util.ArrayList;
import java.util.List;

/**
 * Содержит вспомогательные операции запуска и остановки дочерних нитей-сортировщиков,
 * чтобы не дублировать их в точке входа и в режиме замера.
 */
public final class SorterRunner {

    private SorterRunner() {
    }

    /**
     * Запускает каждую сортировщик-задачу в отдельной демонической нити.
     *
     * @param sorters    сортировщики-задачи
     * @param namePrefix префикс имени нитей
     * @return список запущенных нитей в том же порядке
     */
    public static List<Thread> start(List<? extends BubbleSorter> sorters, String namePrefix) {
        List<Thread> threads = new ArrayList<>(sorters.size());
        for (int i = 0; i < sorters.size(); i++) {
            Thread thread = new Thread(sorters.get(i), namePrefix + "-" + (i + 1));
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }
        return threads;
    }

    /**
     * Просит все нити остановиться и дожидается их завершения.
     *
     * @param sorters сортировщики-задачи
     * @param threads соответствующие им нити
     * @throws InterruptedException если текущую нить прервали во время ожидания
     */
    public static void shutdown(List<? extends BubbleSorter> sorters, List<Thread> threads)
            throws InterruptedException {
        for (BubbleSorter sorter : sorters) {
            sorter.stop();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }
}
