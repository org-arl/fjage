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
 * @version $Revision: 8878 $ $Date: 2012-04-17 22:39:10 +0800 (Tue, 17 Apr 2012) $
 */
public abstract class OneShotBehavior extends Behavior {

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

}

