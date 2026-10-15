package org.example.common;

/**
 * Хранит всю конфигурацию запуска в одном неизменяемом объекте, чтобы её было
 * удобно передавать между точкой входа и режимами работы.
 *
 * @param listType                реализация списка
 * @param threads                 число дочерних нитей-сортировщиков
 * @param delay                   задержки сортировки
 * @param maxLineLength           порог, после которого вводимая строка режется
 * @param benchmarkElements       если задано — headless-режим замера с таким количеством записей
 * @param benchmarkDurationMillis длительность замера в headless-режиме
 */
public record AppConfig(
        ListType listType,
        int threads,
        DelayConfig delay,
        int maxLineLength,
        Integer benchmarkElements,
        Long benchmarkDurationMillis
) {

    public static final int DEFAULT_THREADS = 3;
    public static final long DEFAULT_BETWEEN_STEPS_MS = 1000;
    public static final long DEFAULT_INSIDE_STEP_MS = 0;
    public static final int DEFAULT_MAX_LINE_LENGTH = 80;

    /**
     * Создаёт конфигурацию со значениями по умолчанию (свой список, 3 нити,
     * задержка 1 c между шагами, интерактивный режим).
     *
     * @return конфигурация по умолчанию
     */
    public static AppConfig defaults() {
        return new AppConfig(
                ListType.LINKED,
                DEFAULT_THREADS,
                new DelayConfig(DEFAULT_BETWEEN_STEPS_MS, DEFAULT_INSIDE_STEP_MS),
                DEFAULT_MAX_LINE_LENGTH,
                null,
                null
        );
    }

    /**
     * Сообщает, запущено ли приложение в режиме замера.
     *
     * @return {@code true}, если задано количество записей для замера
     */
    public boolean isBenchmark() {
        return benchmarkElements != null;
    }
}
