package io.github.mrozowski.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class OrchestrationExecutor {

  private static final System.Logger log = System.getLogger(OrchestrationExecutor.class.getName());

  private OrchestrationExecutor() {
  }

  static OrchestrationResult execute(
      List<List<StepDefinition>> stepSequence,
      List<OrchestrationListener> listeners,
      OrchestrationContext context,
      ExecutorService executor) {

    Objects.requireNonNull(stepSequence);
    Objects.requireNonNull(listeners);
    Objects.requireNonNull(context);

    boolean shutdownExecutor = false;
    ExecutorService exec = executor;

    if (exec == null) {
      // If executor is not provided we create default one that must be shutdown once orchestration is finished
      exec = Executors.newVirtualThreadPerTaskExecutor();
      shutdownExecutor = true;
    }

    List<StepExecutionMetadata> executions = new ArrayList<>();
    boolean anyFailure = false;
    boolean stopped = false;

    try {
      for (List<StepDefinition> group : stepSequence) {

        GroupResult groupResult = executeGroup(group, listeners, context, exec);
        executions.addAll(groupResult.executions());

        if (groupResult.anyFailure()) {
          anyFailure = true;
        }

        if (groupResult.stopped()) {
          stopped = true;
          break;
        }
      }

    } finally {
      if (shutdownExecutor) {
        exec.shutdown();
      }
    }

    OrchestrationResult.Status status = getStatus(anyFailure, stopped);

    return new OrchestrationResult(status, context, executions);
  }

  private static GroupResult executeGroup(
      List<StepDefinition> group,
      List<OrchestrationListener> listeners,
      OrchestrationContext context,
      ExecutorService exec) {

    // Sequential step
    if (group.size() == 1) {
      StepResult result = executeSingleStep(group.getFirst(), listeners, context);
      return GroupResult.of(result);
    }

    // If group contains multiple steps process them in parallel
    return executeParallelSteps(group, listeners, context, exec);
  }

  private static GroupResult executeParallelSteps(List<StepDefinition> group, List<OrchestrationListener> listeners, OrchestrationContext context, ExecutorService exec) {
    List<Callable<StepResult>> tasks = new ArrayList<>(group.size());

    // List of steps to process in parallel
    for (StepDefinition step : group) {
      tasks.add(() -> executeSingleStep(step, listeners, context));
    }

    List<StepExecutionMetadata> stepExecutions = new ArrayList<>(group.size());
    boolean anyFailure = false;
    boolean stopped = false;

    try {
      List<Future<StepResult>> futures = exec.invokeAll(tasks);

      for (Future<StepResult> future : futures) {
        StepResult result = future.get();
        stepExecutions.add(result.execution());

        if (!result.success()) {
          anyFailure = true;
          if (result.stop()) {
            stopped = true;
          }
        }
      }

    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      anyFailure = true;
      stopped = true;
    } catch (ExecutionException ee) {
      anyFailure = true;
      stopped = true;
    }

    return new GroupResult(stepExecutions, anyFailure, stopped);
  }


  private static StepResult executeSingleStep(
      StepDefinition step,
      List<OrchestrationListener> listeners,
      OrchestrationContext context) {

    String stepName = step.name();
    StepOptions options = step.options();
    RetryPolicy retryPolicy = options.retryPolicy();

    int maxAttempts = retryPolicy.maxAttempts();

    Exception finalException = null;
    boolean success = false;
    boolean stop = false;
    int attempts = 0;

    notifyListeners(listeners, stepName, l -> l.beforeStep(stepName, context));
    Instant start = Instant.now();

    while (attempts < maxAttempts) {
      attempts++;
      try {
        Object result = step.body().apply(context);

        // if step produce result - add it to the context
        if (step.key() != null) {
          @SuppressWarnings("unchecked")
          Key<Object> key = (Key<Object>) step.key();
          context.put(key, result);
        }

        success = true;
        break;

      } catch (Exception ex) {
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
      notifyListeners(listeners, stepName, l -> l.afterStep(stepName, context));
    } else {
      final Exception e = finalException;
      notifyListeners(listeners, stepName, l -> l.onFailure(stepName, e, context));

      FailureStrategy strategy = options.failureHandler().apply(finalException, context);
      if (strategy == FailureStrategy.STOP) {
        stop = true;
      }
    }

    StepExecutionMetadata execution = new StepExecutionMetadata(
        stepName, start, end, success, success ? null : finalException, attempts);

    return new StepResult(execution, success, stop);
  }

  private static OrchestrationResult.Status getStatus(boolean anyFailure, boolean stopped) {
    if (!anyFailure) {
      return OrchestrationResult.Status.SUCCESS;
    } else if (stopped) {
      return OrchestrationResult.Status.FAILED;
    } else {
      return OrchestrationResult.Status.PARTIAL;
    }
  }

  private static void notifyListeners(List<OrchestrationListener> listeners, String stepName, Consumer<OrchestrationListener> action) {
    for (OrchestrationListener listener : listeners) {
      try {
        action.accept(listener);
      } catch (Exception ex) {
        log.log(System.Logger.Level.WARNING,
            "Listener {0} failed for step {1}: {2}",
            listener.getClass().getName(),
            stepName,
            ex.toString());
      }
    }
  }


  private static boolean isRetryable(Exception ex, RetryPolicy policy) {
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
      StepExecutionMetadata execution,
      boolean success,
      boolean stop) {
  }

  private record GroupResult(
      List<StepExecutionMetadata> executions,
      boolean anyFailure,
      boolean stopped) {

    static GroupResult of(StepResult result) {
      return new GroupResult(
          List.of(result.execution()),
          !result.success(),
          result.stop());
    }
  }
}