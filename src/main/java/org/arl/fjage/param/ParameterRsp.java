/******************************************************************************

Copyright (c) 2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.param;

import java.util.*;
import java.util.Map.Entry;
import org.arl.fjage.*;

/**
 * Response message for {@link ParameterReq}. </br>
 *
 * @see ParameterReq
 */
public class ParameterRsp extends Message {

  private static final long serialVersionUID = 1L;

  protected int index = -1;
  protected Map<Parameter, GenericValue> values = null;
  protected Parameter param;
  protected GenericValue value;

  /**
   * Constructs a response message.
   *
   * @param msg message to which this response corresponds to.
   */
  public ParameterRsp(Message msg) {
    super(msg, Performative.INFORM);
  }

  /**
   * Clears all parameter and values.
   */
  public void clear() {
    param = null;
    value = null;
    values = null;
  }

  /**
   * Gets the index.
   *
   * @return index or -1 if not indexed
   */
  public int getIndex() {
    return index;
  }

  /**
   * Sets the index for parameter.
   *
   * @param index index or -1 if not indexed
   */
  public void setIndex(int index) {
    this.index = index;
  }

  /**
   * Sets the parameter.
   *
   * @param param parameter
   * @param value value
   */
  public void set(Parameter param, Object value) {
    if (this.param == null) {
      this.param = param;
      this.value =  new GenericValue(value);
    } else {
      if (values == null) values = new HashMap<Parameter, GenericValue>();
      values.put(param, new GenericValue(value));
    }
  }

  /**
   * Gets the queried parameter value.
   *
   * @param param {@link Parameter}
   * @return value of requested parameter
   */
  public Object get(Parameter param) {
    if (this.param == null) return null;
    Object rv = null;
    if (this.param.equals(param)) {
      if (value == null) return null;
      rv = value.getValue();
    } else {
      if (values == null) return null;
      GenericValue v = values.get(param);
      if (v == null) return null;
      rv = v.getValue();
    }
    if (rv instanceof Double && ((Double)rv).intValue() == ((Double)rv).doubleValue()) rv = new Integer(((Double)rv).intValue());
    return rv;
  }

  /**
   * Gets all requested parameters as set.
   *
   * @return the parameter set
   */
  public Set<Parameter> parameters() {
    Set<Parameter> set = new HashSet<Parameter>();
    if (param != null) set.add(param);
    if (values != null) set.addAll(values.keySet());
    return set;
  }

  /**
   * Gets all requested parameters as map.
   *
   * @return the parameter map
   */
  public Map<Parameter, Object> getParameters() {
    Map<Parameter, Object> map = new HashMap<Parameter, Object>();
    if (param != null) map.put(param, value);
    if (values != null) map.putAll(values);
    return map;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(getClass().getSimpleName());
    sb.append('[');
    if (index >= 0) {
      sb.append("index:");
      sb.append(index);
      if (param != null) sb.append(' ');
    }
    String s = null;
    Object v = null;
    if (param != null) {
      sb.append(param);
      sb.append(':');
      if (value != null) {
        v = value.getValue();
        if (v instanceof Double && ((Double)v).intValue() == ((Double)v).doubleValue()) v = new Integer(((Double)v).intValue());
        sb.append(v);
      } else {
        sb.append("null");
      }
      if (values != null) {
        for (Entry<Parameter, GenericValue> e : values.entrySet()) {
          sb.append(' ');
          sb.append(e.getKey());
          sb.append(':');
          GenericValue gv = e.getValue();
          v = null;
          if (gv != null) v = gv.getValue();
          if (v instanceof Double && ((Double)v).intValue() == ((Double)v).doubleValue()) v = new Integer(((Double)v).intValue());
          sb.append(v);
        }
      }
      s = sb.toString();
      if (s.length() == 0) s = null;
    }
    sb.append(']');
    return sb.toString();
  }

}
