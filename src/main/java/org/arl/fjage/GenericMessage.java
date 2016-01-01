/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.*;

/**
 * A message class that can convey generic messages represented by key-value pairs.
 *
 * @author  Mandar Chitre
 */
public class GenericMessage extends Message implements Map<Object,Object> {

  private static final long serialVersionUID = -1L;

  //////////// Private attributes

  private Map<Object,Object> map = new HashMap<Object,Object>();

  //////////// Interface methods

  /**
   * Creates an empty generic message.
   */
  public GenericMessage() {
    super();
  }

  /**
   * Creates a generic new message.
   *
   * @param perf performative.
   */
  public GenericMessage(Performative perf) {
    super(perf);
  }

  /**
   * Creates a generic new message.
   *
   * @param recipient agent id of recipient agent or topic.
   */
  public GenericMessage(AgentID recipient) {
    super(recipient);
  }

  /**
   * Creates a generic new message.
   *
   * @param recipient agent id of recipient agent or topic.
   * @param perf performative.
   */
  public GenericMessage(AgentID recipient, Performative perf) {
    super(recipient, perf);
  }

  /**
   * Creates a generic response message.
   *
   * @param inReplyTo message to which this response corresponds to.
   */
  public GenericMessage(Message inReplyTo) {
    super(inReplyTo);
  }

  /**
   * Creates a generic response message.
   *
   * @param inReplyTo message to which this response corresponds to.
   * @param perf performative.
   */
  public GenericMessage(Message inReplyTo, Performative perf) {
    super(inReplyTo, perf);
  }

  ///////////// Map interface delegated to the backing HashMap

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  @Override
  public Set<java.util.Map.Entry<Object, Object>> entrySet() {
    return map.entrySet();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public Set<Object> keySet() {
    return map.keySet();
  }

  @Override
  public Object put(Object key, Object value) {
    if (key.equals("performative")) {
      setPerformative((Performative)value);
      return value;
    }
    if (key.equals("recipient")) {
      setRecipient((AgentID)value);
      return value;
    }
    if (key.equals("sender")) return getSender();
    if (key.equals("messageID")) return getMessageID();
    if (key.equals("inReplyTo")) return getInReplyTo();
    return map.put(key, value);
  }

  @Override
  public Object get(Object key) {
    if (key.equals("performative")) return getPerformative();
    if (key.equals("recipient")) return getRecipient();
    if (key.equals("sender")) return getSender();
    if (key.equals("messageID")) return getMessageID();
    if (key.equals("inReplyTo")) return getInReplyTo();
    return map.get(key);
  }

  @Override
  public void putAll(Map<? extends Object, ? extends Object> map) {
    this.map.putAll(map);
  }

  @Override
  public Object remove(Object key) {
    return map.remove(key);
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public Collection<Object> values() {
    return map.values();
  }

  /////////////// Special getters

  /**
   * Gets the string value associated with a key.
   *
   * @param key the key.
   * @param defVal default value to return, if the key is not found.
   * @return the string value associated with the key, or defVal if not found.
   */
  public String get(Object key, String defVal) {
    Object obj = get(key);
    if (obj == null) return defVal;
    return obj.toString();
  }

  /**
   * Gets the integer value associated with a key.
   *
   * @param key the key.
   * @param defVal default value to return, if the key is not found.
   * @return the integer value associated with the key, or defVal if not found.
   * @throws java.lang.NumberFormatException if the value is not numeric.
   */
  public int get(Object key, int defVal) {
    Object obj = get(key);
    if (obj == null) return defVal;
    return ((Number)obj).intValue();
  }

  /**
   * Gets the long value associated with a key.
   *
   * @param key the key.
   * @param defVal default value to return, if the key is not found.
   * @return the long value associated with the key, or defVal if not found.
   * @throws java.lang.NumberFormatException if the value is not numeric.
   */
  public long get(Object key, long defVal) {
    Object obj = get(key);
    if (obj == null) return defVal;
    return ((Number)obj).longValue();
  }

  /**
   * Gets the double value associated with a key.
   *
   * @param key the key.
   * @param defVal default value to return, if the key is not found.
   * @return the double value associated with the key, or defVal if not found.
   * @throws java.lang.NumberFormatException if the value is not numeric.
   */
  public double get(Object key, double defVal) {
    Object obj = get(key);
    if (obj == null) return defVal;
    return ((Number)obj).doubleValue();
  }

}

