package io.github.mrozowski.orchestration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OrchestrationContext {

  private final Map<Key<?>, Object> store = new ConcurrentHashMap<>();

  public static OrchestrationContext empty() {
    return new OrchestrationContext();
  }

  public <T> void put(Key<T> key, T value) {
    store.put(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> T get(Key<T> key) {
    return (T) store.get(key);
  }

  public boolean contains(Key<?> key) {
    return store.containsKey(key);
  }

  public Map<Key<?>, Object> snapshot() {
    return Map.copyOf(store);
  }
}