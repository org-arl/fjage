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

  //////////// Private attributes

  private boolean quit = false;

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

  /**
   * Terminates the behavior.
   */
  public final void stop() {
    quit = true;
  }

}
