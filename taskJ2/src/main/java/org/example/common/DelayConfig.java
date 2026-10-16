package org.example.common;

public record DelayConfig(
        long betweenStepsMillis,
        long insideStepMillis
) {

    public DelayConfig {
        if (betweenStepsMillis < 0 || insideStepMillis < 0) {
            throw new IllegalArgumentException("Задержки не могут быть отрицательными");
        }
    }

    public void sleepBetweenSteps() throws InterruptedException {
        if (betweenStepsMillis > 0) {
            Thread.sleep(betweenStepsMillis);
        }
    }

    public void sleepInsideStep() throws InterruptedException {
        if (insideStepMillis > 0) {
            Thread.sleep(insideStepMillis);
        }
    }
}
