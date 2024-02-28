package com.google.solutions.jitaccess.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Lazily initializes a value on first access.
 * @param <T>
 */
public class Lazy<T> { // TODO: remove?
  private final @NotNull Supplier<T> initialize;
  private final AtomicReference<T> value;

  public Lazy(@NotNull Supplier<T> initialize) {
    this.value = new AtomicReference<>();
    this.initialize = initialize;
  }

  /**
   * Get the value. Causes the initialize supplier to
   * be called on first use.
   */
  public T get() {
    if (value.get() == null) {
      synchronized (value) {
        if (value.get() == null) {
          value.set(this.initialize.get());
        }
      }
    }

    return value.get();
  }
}
