package io.github.mrozowski.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

final class OrchestrationExecutor {

  private OrchestrationExecutor() {}

  static OrchestrationResult execute(
      List<List<StepDefinition>> stepSequence,
      List<OrchestrationListener> listeners,
      OrchestrationContext context,
      ExecutorService executor) {

    Objects.requireNonNull(stepSequence);
    Objects.requireNonNull(listeners);
    Objects.requireNonNull(context);

    ExecutorService exec =
        executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();

    List<StepExecution> executions = new ArrayList<>();
    boolean anyFailure = false;
    boolean stopped = false;

    outer:
    for (List<StepDefinition> group : stepSequence) {

      if (group.size() == 1) {
        // ---- Sequential ----
        StepResult result =
            executeSingleStep(group.get(0), listeners, context);

        executions.add(result.execution);

        if (!result.success) {
          anyFailure = true;
          if (result.stop) {
            stopped = true;
            break;
          }
        }

      } else {
        // ---- Parallel ----
        List<Callable<StepResult>> tasks = new ArrayList<>();

        for (StepDefinition step : group) {
          tasks.add(() -> executeSingleStep(step, listeners, context));
        }

        try {
          List<Future<StepResult>> futures = exec.invokeAll(tasks);

          for (Future<StepResult> future : futures) {
            StepResult result = future.get();

            executions.add(result.execution);

            if (!result.success) {
              anyFailure = true;
              if (result.stop) {
                stopped = true;
              }
            }
          }

          if (stopped) {
            break;
          }

        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          stopped = true;
          anyFailure = true;
          break;
        } catch (ExecutionException ee) {
          stopped = true;
          anyFailure = true;
          break;
        }
      }
    }

    OrchestrationResult.Status status;

    if (!anyFailure) {
      status = OrchestrationResult.Status.SUCCESS;
    } else if (stopped) {
      status = OrchestrationResult.Status.FAILED;
    } else {
      status = OrchestrationResult.Status.PARTIAL;
    }

    return new OrchestrationResult(status, context, executions);
  }

  private static StepResult executeSingleStep(
      StepDefinition step,
      List<OrchestrationListener> listeners,
      OrchestrationContext context) {

    String stepName = step.name();
    StepOptions options = step.options();
    RetryPolicy retryPolicy = options.retryPolicy();

    Instant start = Instant.now();
    Throwable finalException = null;
    boolean success = false;
    boolean stop = false;
    int attempts = 0;

    for (OrchestrationListener listener : listeners) {
      try {
        listener.beforeStep(stepName, context);
      } catch (Throwable ignored) {}
    }

    int maxAttempts = retryPolicy.maxAttempts();

    while (attempts < maxAttempts) {
      attempts++;
      try {
        Object result = step.body().apply(context);

        if (step.key() != null) {
          @SuppressWarnings("unchecked")
          Key<Object> key = (Key<Object>) step.key();
          context.put(key, result);
        }

        success = true;
        break;

      } catch (Throwable ex) {
        finalException = ex;

        boolean retryable = isRetryable(ex, retryPolicy);
        if (attempts >= maxAttempts || !retryable) {
          break;
        }

        Duration backoff = retryPolicy.backoff();
        if (!backoff.isZero()) {
          try {
            Thread.sleep(backoff.toMillis());
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            finalException = ie;
            break;
          }
        }
      }
    }

    Instant end = Instant.now();

    if (success) {
      for (OrchestrationListener listener : listeners) {
        try {
          listener.afterStep(stepName, context);
        } catch (Throwable ignored) {}
      }
    } else {
      for (OrchestrationListener listener : listeners) {
        try {
          listener.onFailure(stepName, finalException, context);
        } catch (Throwable ignored) {}
      }

      FailureStrategy strategy =
          options.failureHandler().apply(finalException, context);

      if (strategy == FailureStrategy.STOP) {
        stop = true;
      }
    }

    StepExecution execution =
        new StepExecution(stepName, start, end, success,
            success ? null : finalException, attempts);

    return new StepResult(execution, success, stop);
  }

  private static boolean isRetryable(Throwable ex, RetryPolicy policy) {
    Set<Class<? extends Throwable>> retryOn = policy.retryOn();

    if (retryOn.isEmpty()) {
      return true;
    }

    for (Class<? extends Throwable> type : retryOn) {
      if (type.isAssignableFrom(ex.getClass())) {
        return true;
      }
    }
    return false;
  }

  private record StepResult(
      StepExecution execution,
      boolean success,
      boolean stop) {}
}