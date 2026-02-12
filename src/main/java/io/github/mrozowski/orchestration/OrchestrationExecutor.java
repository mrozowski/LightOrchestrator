package io.github.mrozowski.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class OrchestrationExecutor {

  private OrchestrationExecutor() {}

  static OrchestrationResult execute(
      List<StepDefinition> steps, List<OrchestrationListener> listeners, OrchestrationContext context) {

    Objects.requireNonNull(steps);
    Objects.requireNonNull(listeners);
    Objects.requireNonNull(context);

    List<StepExecution> executions = new ArrayList<>();
    boolean anyFailure = false;
    boolean stopped = false;

    for (StepDefinition step : steps) {

      String stepName = step.name();
      StepOptions options = step.options();
      RetryPolicy retryPolicy = options.retryPolicy();

      Instant start = Instant.now();
      Throwable finalException = null;
      boolean success = false;
      int attempts = 0;

      // ---- beforeStep ----
      for (OrchestrationListener listener : listeners) {
        try {
          listener.beforeStep(stepName, context);
        } catch (Throwable listenerEx) {
          System.err.println("Listener beforeStep failed: " + listenerEx.getMessage());
          listenerEx.printStackTrace();
        }
      }

      // ---- retry loop ----
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

        // ---- afterStep (only on success) ----
        for (OrchestrationListener listener : listeners) {
          try {
            listener.afterStep(stepName, context);
          } catch (Throwable listenerEx) {
            // TODO: Check for proper logging in the library
            System.err.println("Listener afterStep failed: " + listenerEx.getMessage());
            listenerEx.printStackTrace();
          }
        }

      } else {

        anyFailure = true;

        // ---- onFailure (on failure only) ----
        for (OrchestrationListener listener : listeners) {
          try {
            listener.onFailure(stepName, finalException, context);
          } catch (Throwable listenerEx) {
            System.err.println("Listener onFailure failed: " + listenerEx.getMessage());
            listenerEx.printStackTrace();
          }
        }

        FailureStrategy strategy =
            options.failureHandler().apply(finalException, context);

        if (strategy == FailureStrategy.STOP) {
          stopped = true;
        }
      }

      executions.add(new StepExecution(
          stepName,
          start,
          end,
          success,
          success ? null : finalException,
          attempts
      ));

      if (!success && stopped) {
        break;
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
}