/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Internal class representing a message queue.
 *
 * @author  Mandar Chitre
 */
public class MessageQueue {

  /////////// Private attributes

  private final LinkedList<Message> queue = new LinkedList<>();
  private final LinkedList<Message> quarantine = new LinkedList<>();
  private int maxQueueLen;

  /////////// Interface methods

  public MessageQueue() {
    maxQueueLen = 0;  // unlimited queue
  }

  public MessageQueue(int maxlen) {
    maxQueueLen = maxlen;
  }

  public synchronized void setSize(int size) {
    maxQueueLen = size;
    while (maxQueueLen > 0 && queue.size() >= maxQueueLen)
      queue.remove();
  }

  public synchronized void add(Message m) {
    quarantine.offer(m);
    while (maxQueueLen > 0 && quarantine.size() >= maxQueueLen)
      quarantine.remove();
  }

  public synchronized void commit(Iterable<MessageFilter> exclusions) {
    Iterator<Message> it = quarantine.iterator();
    while (it.hasNext()) {
      Message m = it.next();
      boolean exclude = false;
      for (MessageFilter mf: exclusions) {
        if (mf == null || mf.matches(m)) {
          exclude = true;
          break;
        }
      }
      if (!exclude) {
        it.remove();
        queue.offer(m);
      }
    }
    while (maxQueueLen > 0 && queue.size() >= maxQueueLen)
      queue.remove();
  }

  public synchronized Message get() {
    return queue.poll();
  }

  public synchronized Message get(MessageFilter filter) {
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

  public synchronized void clear() {
    queue.clear();
  }

  public int length() {
    return queue.size();
  }

}

