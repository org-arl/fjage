/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * A behavior that never ends. The {@link #action()} method of this behavior
 * is repeatedly called.
 *
 * @author Mandar Chitre
 */
public class CyclicBehavior extends Behavior {

  //////////// Constructors

  public CyclicBehavior() {
    // default constructor
  }

  /**
   * Constructor which creates a behavior with an action defined using a closure.
   * Usually applicable to Groovy agents.
   *
   * @param closure closure to use for action.
   */
  public CyclicBehavior(Runnable closure) {
    setActionClosure(closure);
  }

  //////////// Overridden methods

  /**
   * This method always returns false, since this behavior never terminates.
   *
   * @return false.
   * @see org.arl.fjage.Behavior#done()
   */
  @Override
  public final boolean done() {
    return false;
  }

}

