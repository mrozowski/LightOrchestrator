package io.github.mrozowski.orchestration;

import java.util.Objects;

public final class Key<T> {

  private final String name;

  private Key(String name) {
    this.name = Objects.requireNonNull(name);
  }

  public static <T> Key<T> of(String name) {
    return new Key<>(name);
  }

  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return "Key[" + name + "]";
  }
}