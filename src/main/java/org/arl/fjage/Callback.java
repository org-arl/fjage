package org.arl.fjage;

/**
 * An interface representing a generic callback.
 */
@FunctionalInterface
public interface Callback {
  void call(Object param);
}
