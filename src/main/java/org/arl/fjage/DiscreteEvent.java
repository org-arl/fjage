/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.TimerTask;

/**
 * Internal class representing a discrete event for simulation.
 *
 * @author  Mandar Chitre
 */
class DiscreteEvent {

  /////////// Attributes

  long time;             // time of the event
  TimerTask task;        // task to be executed when time is reached
  boolean passive;       // passive tasks are ones which do not wake up any agent

  /////////// Constructors for convenience

  DiscreteEvent(long time, TimerTask task) {
    this.time = time;
    this.task = task;
    passive = false;
  }

  DiscreteEvent(long time, TimerTask task, boolean passive) {
    this.time = time;
    this.task = task;
    this.passive = passive;
  }
  
  //////////// For display
  
  public String toString() {
    return (passive?"PEvent #":"Event #")+hashCode()+" @"+time;
  }

}

