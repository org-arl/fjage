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
class DiscreteEvent implements Comparable<DiscreteEvent> {

  /////////// Attributes

  static volatile long count = 0;

  long id;               // event count, for resolving ordering ties
  long tid;              // thread id of creator
  long created;          // time when the event was created
  long time;             // time of the event
  TimerTask task;        // task to be executed when time is reached
  boolean passive;       // passive tasks are ones which do not wake up any agent

  /////////// Constructors for convenience

  DiscreteEvent(long created, long time, TimerTask task) {
    synchronized (DiscreteEvent.class) {
      this.id = count++;
    }
    this.tid = Thread.currentThread().getId();
    this.created = created;
    this.time = time;
    this.task = task;
    passive = false;
  }

  DiscreteEvent(long created, long time, TimerTask task, boolean passive) {
    synchronized (DiscreteEvent.class) {
      this.id = count++;
    }
    this.tid = Thread.currentThread().getId();
    this.created = created;
    this.time = time;
    this.task = task;
    this.passive = passive;
  }
  
  //////////// For display
  
  public String toString() {
    return (passive?"PEvent #":"Event #")+hashCode()+" @"+time+" created:"+created+" id:"+tid+"/"+id;
  }

  /////////// Comparison operator

  public int compareTo(DiscreteEvent e) {
    if (time < e.time) return -1;
    if (time > e.time) return 1;
    if (created < e.created) return -1;
    if (created > e.created) return 1;
    if (tid < e.tid) return -1;
    if (tid > e.tid) return 1;
    if (id < e.id) return -1;
    if (id > e.id) return 1;
    return 0;
  }

}
