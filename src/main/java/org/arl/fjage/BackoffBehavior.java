package org.arl.fjage;

public class BackoffBehavior extends Behavior {

  ////////// Private attributes

  private long period;
  private long wakeupTime;
  private boolean quit;

  ////////// Interface methods

  /**
   * Creates a behavior that is executed after a specified backoff.
   *
   * @param millis backoff in milliseconds.
   */
  public BackoffBehavior(long millis) {
    period = millis;
    quit = false;
  }

  /**
   * Terminates the behavior.
   */
  public final void stop() {
    quit = true;
  }

  /**
   * This method is called from {@link #onExpiry()} if the backoff
   * is to be extended.
   *
   * @param millis backoff in milliseconds.
   */
  protected void backoff(long millis) {
    quit = false;
    period = millis;
  }

  ////////// Method to be overridden by subclass

  /**
   * This method is called when the specified backoff period expires. The method may be
   * overridden by a behavior.
   */
  public void onExpiry() {
    super.action();
  }

  ////////// Overridden methods

  /**
   * Computes the wakeup time for the first execution of this behavior.
   *
   * @see org.arl.fjage.Behavior#onStart()
   */
  @Override
  public void onStart() {
    wakeupTime = agent.currentTimeMillis() + period;
    block(period);
  }

  /**
   * This method calls {@link #onExpiry()} when the specified backoff period expires.
   *
   * @see org.arl.fjage.Behavior#action()
   */
  @Override
  public final void action() {
    if (quit) return;
    long t = agent.currentTimeMillis();
    long dt = wakeupTime - t;
    if (dt > 0) block(dt);
    else {
      quit = true;
      onExpiry();
      if (!quit) {
        wakeupTime += period;
        if (wakeupTime < t) wakeupTime = t + period;
      }
    }
  }

  /**
   * Returns true once {@link #stop()} is called, false otherwise.
   *
   * @return true once {@link #stop()} is called, false otherwise.
   * @see org.arl.fjage.Behavior#done()
   */
  @Override
  public final boolean done() {
    return quit;
  }

  /**
   * Resets the behavior, allowing it to be used again.
   *
   * @see org.arl.fjage.Behavior#reset()
   */
  @Override
  public void reset() {
    super.reset();
    quit = false;
  }

  /**
   * Creates a new BackoffBehavior which runs the specified Runnable every specified period.
   *
   * @param millis Backoff in milliseconds.
   * @param runnable Runnable to run.
   * @return BackoffBehavior
   */
  public static BackoffBehavior create(long millis, final Runnable runnable) {
    return new BackoffBehavior(millis) {

      @Override
      public void onExpiry() {
        runnable.run();
      }
    };
  }
}
