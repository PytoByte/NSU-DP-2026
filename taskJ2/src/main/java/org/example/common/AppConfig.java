package org.example.common;

public record AppConfig(
        ListType listType,
        int threads,
        DelayConfig delay,
        int maxLineLength,
        Integer benchmarkElements,
        Long benchmarkDurationMillis
) {

    public static final int DEFAULT_THREADS = 3;
    public static final long DEFAULT_BETWEEN_STEPS_MS = 0;
    public static final long DEFAULT_INSIDE_STEP_MS = 0;
    public static final int DEFAULT_MAX_LINE_LENGTH = 80;

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

    public boolean isBenchmark() {
        return benchmarkElements != null;
    }
}
