package io.github.mrozowski.orchestration;

import java.time.Instant;

public record StepExecution(
    String stepName,
    Instant startTime,
    Instant endTime,
    boolean success,
    Throwable exception,
    int attempts
) {}
