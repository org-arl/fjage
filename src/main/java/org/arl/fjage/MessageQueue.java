/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.*;

/**
 * Internal class representing a message queue.
 *
 * @author  Mandar Chitre
 */
class MessageQueue {

  /////////// Private attributes

  private LinkedList<Message> queue = new LinkedList<Message>();
  private int maxQueueLen = 0;

  /////////// Interface methods

  synchronized void setSize(int size) {
    maxQueueLen = size;
    while (maxQueueLen > 0 && queue.size() >= maxQueueLen)
      queue.remove();
  }

  synchronized void add(Message m) {
    queue.offer(m);
  }

  synchronized Message get() {
    return queue.poll();
  }

  synchronized Message get(MessageFilter filter) {
    if (filter == null) return queue.poll();
    Iterator<Message> it = queue.iterator();
    while (it.hasNext()) {
      Message m = it.next();
      if (filter.matches(m)) {
        it.remove();
        return m;
      }
    }
    return null;
  }

}

