package org.arl.fjage.test;

import org.arl.fjage.Message;

public abstract class AbstractBehaviorTest
    extends AbstractSimulatorTest {

  protected static class BehaviorTestMessage
      extends Message {

    private final String data;

    public BehaviorTestMessage(String data) {
      super();

      this.data = data;
    }

    public String getData() {
      return data;
    }
  }

  protected static class IntHolder {

    private int value = 0;

    public IntHolder() {
      super();
    }

    public IntHolder(int initialValue) {
      super();

      this.value = initialValue;
    }

    public int getValue() {
      return value;
    }

    public void setValue(int value) {
      this.value = value;
    }

    public void increment() {
      value++;
    }

    public void decrement() {
      value--;
    }
  }
}
