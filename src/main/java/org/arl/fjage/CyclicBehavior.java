/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * A behavior that is called cyclically. The {@link #action()} method of this behavior
 * is repeatedly called.
 *
 * @author Mandar Chitre
 */
public class CyclicBehavior extends Behavior {

  ////////// Private attributes

  private boolean quit = false;

  ////////// Interface methods

  /**
   * Creates a behavior that is executed cyclically.
   */
  public CyclicBehavior() {
  }

  /**
   * Creates a behavior that is executed cyclically.
   *
   * @param runnable Runnable to run.
   */
  public CyclicBehavior(Runnable runnable) {
    this();
    if (runnable != null) {
      this.action = param -> runnable.run();
    }
  }

  //////////// Overridden methods

  /**
   * This method returns false until stop() is called.
   *
   * @return false.
   * @see org.arl.fjage.Behavior#done()
   */
  @Override
  public final boolean done() {
    return quit;
  }

  @Override
  public final int getPriority() {
    return Integer.MAX_VALUE;
  }

  ////////// Interface methods

  /**
   * Terminates the behavior.
   */
  public final void stop() {
    quit = true;
  }
}
