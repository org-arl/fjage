/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * A behavior that is executed only once. The {@link #action()} method of this
 * behavior is called only once.
 *
 * @author Mandar Chitre
 */
public class OneShotBehavior extends Behavior {

  //////////// Overridden methods

  /**
   * This method always returns true, since this behavior is called only once.
   *
   * @return true.
   * @see org.arl.fjage.Behavior#done()
   */
  @Override
  public final boolean done() {
    return true;
  }

  /**
   * Creates a new OneShotBehavior which runs the specified Runnable once.
   *
   * @param runnable Runnable to run.
   * @return OneShotBehavior
   */
  public static OneShotBehavior create(final Runnable runnable) {
    return new OneShotBehavior() {

      @Override
      public void action() {
        runnable.run();
      }
    };
  }
}
