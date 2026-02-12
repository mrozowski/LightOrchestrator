package io.github.mrozowski.orchestration;

import java.time.Duration;
import java.util.Set;

public final class RetryPolicy {

  private final int maxAttempts;
  private final Set<Class<? extends Throwable>> retryOn;
  private final Duration backoff;

  private RetryPolicy(int maxAttempts,
                      Set<Class<? extends Throwable>> retryOn,
                      Duration backoff) {
    this.maxAttempts = maxAttempts;
    this.retryOn = retryOn;
    this.backoff = backoff;
  }

  public static RetryPolicy none() {
    return new RetryPolicy(1, Set.of(), Duration.ZERO);
  }

  public static RetryPolicy fixed(int maxAttempts, Duration backoff) {
    return new RetryPolicy(maxAttempts, Set.of(), backoff);
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public Set<Class<? extends Throwable>> retryOn() {
    return retryOn;
  }

  public Duration backoff() {
    return backoff;
  }
}