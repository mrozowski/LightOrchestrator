# LightOrchestrator

A lightweight orchestration pipeline for Java 21.

Allows developer to focus on business logic and delegate execution order, retry, failure handling, and execution reporting to a minimal framework.

---

## Features

- Sequential step execution
- Functional style
- Type-safe shared context
- Per-step retry configuration
- Per-step failure strategy (STOP / CONTINUE)
- Execution metadata for observability
- Listener hooks for logging and metrics

---

## Basic Usage

Define typed keys for step outputs:

```java
Key<String> greetingKey = Key.of("greeting");
Key<Integer> lengthKey = Key.of("length");
```

Build and execute a pipeline:
```java
var orchestrator = Orchestrator.builder()
    .step(greetingKey, ctx -> "hello")
    .step(lengthKey, ctx -> {
        String greeting = ctx.get(greetingKey);
        return greeting.length();
    })
    .build();

OrchestrationResult result = orchestrator.execute();
```

Returned values are automatically stored in the context under their associated Key.

---

## Retry Per Step
Retry is configured per step only.
```java

var orchestrator = Orchestrator.builder()
    .step("retry-step", 
        () -> doWork(), 
        StepOptions.retry(3, Duration.ofSeconds(3)))
    .build();
```

## Failure Strategy

Stop or continue execution after failure:

```java
var orchestrator = Orchestrator.builder()
    .step("step-name", 
        () -> doWork(),
        StepOptions.failure(FailureStrategy.STOP))
    .build();
```
By default, the `STOP` strategy is applied.

If a step fails, orchestration stops processing and all the remaining steps are not executed.
If a step failure is not critical and the remaining steps should be run, configure the `CONTINUE` strategy:

```java
StepOptions.failure(FailureStrategy.CONTINUE)
```

You can also use a functional handler:
```java
StepOptions.onFailure((ex, ctx) -> {
    log.error("Failure", ex);
    return FailureStrategy.CONTINUE;
});
```

---

## Observability & Listeners

Attach a listener for standardized logging and metrics:

```java
var orchestrator = Orchestrator.builder()
    .step("test-step", Test::stepLogic)
    .listener(new OrchProcessingListener())
    .build()
    .execute();
```

Example listener:
```java
public class OrchProcessingListener implements OrchestrationListener {

  private static final Logger log =
      LoggerFactory.getLogger(OrchProcessingListener.class);

  @Override
  public void beforeStep(String name, OrchestrationContext ctx) {
    log.info("Step '{}' started", name);
  }

  @Override
  public void afterStep(String name, OrchestrationContext ctx, StepExecutionMetadata metadata) {
    log.info("Step '{}' finished | success={} | duration={}ms", name, metadata.success(), metadata.processingTime().toMillis());
  }

  @Override
  public void onFailure(String name, Throwable ex, OrchestrationContext ctx, StepExecutionMetadata metadata) {
    log.error("Step '{}' failed after {}ms", name, metadata.processingTime().toMillis(), ex);
  }
}

```