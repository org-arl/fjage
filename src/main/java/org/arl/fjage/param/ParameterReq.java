package org.arl.fjage.param;

import java.io.Serializable;
import java.util.*;
import org.arl.fjage.*;

/**
 * Request one or more parameters of an agent.
 */
public class ParameterReq extends Message {

  private static final long serialVersionUID = 1L;

  protected int index = -1;
  protected List<Entry> requests = null;
  protected Parameter param;
  protected GenericValue value;

  public ParameterReq() {
    super(Performative.REQUEST);
  }

  public ParameterReq(AgentID recipient) {
    super(recipient, Performative.REQUEST);
  }

  /**
   * clears all parameter requests.
   */
  public void clear() {
    requests = null;
    param = null;
    value = null;
  }

  /**
   * Gets the index for index based parameters.
   *
   * @return the index, -1 if the request has not indexed
   */
  public int getIndex() {
    return index;
  }

  /**
   * Sets the index for index based parameter.
   *
   * @param index index or channel, -1 if the request has not indexed
   */
  public void setIndex(int index) {
    this.index = index;
  }

  /**
   * Requests a parameter.
   *
   * @param param
   *          parameter to be requested
   * @return this object, to allow multiple gets to be concatenated
   */
  public ParameterReq get(Parameter param) {
    if (this.param == null) this.param = param;
    else {
      if (requests == null) requests = new ArrayList<Entry>();
      requests.add(new Entry(param, null));
    }
    return this;
  }

  /**
   * Requests a list of parameters.
   *
   * @param param
   *          parameter list to be requested
   * @return this object, to allow multiple gets to be concatenated
   */
  public ParameterReq get(List<? extends Parameter> param) {
    for (Parameter p: param)
      get(p);
    return this;
  }

  /**
   * Sets a parameter value.
   *
   * @param param
   *          parameter to be set
   * @param value value of parameter
   * @return this object, to allow multiple sets to be concatenated
   */
  public ParameterReq set(Parameter param, Object value) {
    if (this.param == null) {
      this.param = param;
      this.value = new GenericValue(value);
    } else {
      if (requests == null) requests = new ArrayList<Entry>();
      requests.add(new Entry(param, value));
    }
    return this;
  }

  /**
   * Gets a list of requests to be made.
   *
   * @return requests
   */
  public List<Entry> requests() {
    int n = 0;
    if (requests != null) n = requests.size();
    List<Entry> rq = new ArrayList<Entry>(n+1);
    if (param != null) rq.add(new Entry(param, value));
    if (requests != null) rq.addAll(requests);
    return rq;
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
    if (param != null) {
      sb.append(param);
      sb.append(':');
      sb.append(value == null ? '?' : value);
      if (requests != null) {
        for (Entry e : requests) {
          sb.append(' ');
          sb.append(e.param);
          sb.append(':');
          Object evalue = e.getValue();
          sb.append(evalue == null ? '?' : evalue);
        }
      }
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * Representation for parameter and value.
   *
   */
  public class Entry implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Parameter.
     */
    public Parameter param;

    /**
     * value of parameter.
     */
    public GenericValue value;

    private Entry(Parameter param, Object value) {
      this.param = param;
      if (value == null) this.value = null;
      else if (value instanceof GenericValue) this.value = (GenericValue)value;
      else this.value = new GenericValue(value);
    }

    public Object getValue() {
      if (value == null) return null;
      return value.getValue();
    }

  }

}
