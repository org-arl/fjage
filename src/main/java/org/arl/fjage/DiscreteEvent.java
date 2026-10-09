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
record DiscreteEvent(long id, long tid, long created, long time, TimerTask task, boolean passive)
    implements Comparable<DiscreteEvent> {

  private static long count;

  private static synchronized long nextId() { return count++; }

  DiscreteEvent(long created, long time, TimerTask task) {
    this(created, time, task, false);
  }

  DiscreteEvent(long created, long time, TimerTask task, boolean passive) {
    this(nextId(), Thread.currentThread().getId(), created, time, task, passive);
  }

  @Override
  public String toString() {
    return (passive ? "PEvent #" : "Event #")+System.identityHashCode(this)+" @"+time+" created:"+created+" id:"+tid+"/"+id;
  }

  @Override
  public int compareTo(DiscreteEvent event) {
    int order = Long.compare(time, event.time);
    if (order == 0) order = Long.compare(created, event.created);
    if (order == 0) order = Long.compare(tid, event.tid);
    return order == 0 ? Long.compare(id, event.id) : order;
  }
}
