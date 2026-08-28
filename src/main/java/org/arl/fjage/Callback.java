package org.arl.fjage;

/**
 * An interface representing a generic callback.
 */
@FunctionalInterface
public interface Callback {
  public void call(Object param);
}
