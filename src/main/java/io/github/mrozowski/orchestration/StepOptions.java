package io.github.mrozowski.orchestration;

import java.util.Objects;
import java.util.function.BiFunction;

public final class StepOptions {

  private final RetryPolicy retryPolicy;
  private final BiFunction<Throwable, OrchestrationContext, FailureStrategy> failureHandler;

  private StepOptions(RetryPolicy retryPolicy,
                      BiFunction<Throwable, OrchestrationContext, FailureStrategy> failureHandler) {
    this.retryPolicy = retryPolicy;
    this.failureHandler = failureHandler;
  }

  public static StepOptions defaults() {
    return new StepOptions(
        RetryPolicy.none(),
        (ex, ctx) -> FailureStrategy.STOP
    );
  }

  public static StepOptions retry(int attempts) {
    return builder()
        .retryPolicy(RetryPolicy.fixed(attempts, java.time.Duration.ZERO))
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public RetryPolicy retryPolicy() {
    return retryPolicy;
  }

  public BiFunction<Throwable, OrchestrationContext, FailureStrategy> failureHandler() {
    return failureHandler;
  }

  public static final class Builder {

    private RetryPolicy retryPolicy = RetryPolicy.none();
    private BiFunction<Throwable, OrchestrationContext, FailureStrategy> failureHandler =
        (ex, ctx) -> FailureStrategy.STOP;

    public Builder retryPolicy(RetryPolicy retryPolicy) {
      this.retryPolicy = Objects.requireNonNull(retryPolicy);
      return this;
    }

    public Builder failureStrategy(FailureStrategy strategy) {
      this.failureHandler = (ex, ctx) -> strategy;
      return this;
    }

    public Builder failureHandler(
        BiFunction<Throwable, OrchestrationContext, FailureStrategy> handler) {
      this.failureHandler = Objects.requireNonNull(handler);
      return this;
    }

    public StepOptions build() {
      return new StepOptions(retryPolicy, failureHandler);
    }
  }
}