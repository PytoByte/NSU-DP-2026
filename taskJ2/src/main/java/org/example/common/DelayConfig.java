package org.example.common;

/**
 * Описывает регулируемые задержки алгоритма сортировки, чтобы между шагами и
 * внутри шага можно было делать паузы.
 *
 * @param betweenStepsMillis задержка между шагами (между попытками поменять местами две записи)
 * @param insideStepMillis   задержка внутри шага (например, перед сравнением/перестановкой)
 */
public record DelayConfig(long betweenStepsMillis, long insideStepMillis) {

    /**
     * Проверяет, что обе задержки неотрицательны.
     *
     * @throws IllegalArgumentException если хотя бы одна задержка отрицательна
     */
    public DelayConfig {
        if (betweenStepsMillis < 0 || insideStepMillis < 0) {
            throw new IllegalArgumentException("Задержки не могут быть отрицательными");
        }
    }

    /**
     * Приостанавливает текущую нить на задержку между шагами.
     *
     * @throws InterruptedException если нить прервали во время ожидания
     */
    public void sleepBetweenSteps() throws InterruptedException {
        if (betweenStepsMillis > 0) {
            Thread.sleep(betweenStepsMillis);
        }
    }

    /**
     * Приостанавливает текущую нить на задержку внутри шага.
     *
     * @throws InterruptedException если нить прервали во время ожидания
     */
    public void sleepInsideStep() throws InterruptedException {
        if (insideStepMillis > 0) {
            Thread.sleep(insideStepMillis);
        }
    }
}
