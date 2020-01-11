package org.arl.fjage.param;

import java.util.*;
import java.lang.reflect.*;
import org.arl.fjage.*;
import org.apache.commons.lang3.reflect.MethodUtils;

public class ParameterMessageBehavior extends MessageBehavior {

  private List<? extends Parameter> params;

  public ParameterMessageBehavior() {
    super(ParameterReq.class);
    this.params = null;
  }

  public ParameterMessageBehavior(List<? extends Parameter> params) {
    super(ParameterReq.class);
    this.params = params;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public ParameterMessageBehavior(Class ... paramEnumClasses) {
    super(ParameterReq.class);
    params = new ArrayList<Parameter>();
    for (int i = 0; i < paramEnumClasses.length; i++)
      params.addAll(EnumSet.allOf(paramEnumClasses[i]));
  }

  @Override
  public void onReceive(Message msg) {
    ParameterReq req = (ParameterReq)msg;
    ParameterRsp rsp = processParameterReq(req, null);
    if (rsp != null) agent.send(rsp);
  }

  /**
   * An agent supporting dynamic parameters may override this to return a list
   * of parameters available.
   *
   * @return list of supported parameters, null if none supported.
   */
  protected List<? extends Parameter> getParameterList() {
    return params;
  }

  /**
   * An agent supporting dynamic parameters may override this to return a list
   * of parameters available.
   *
   * @param ndx index for indexed parameters, -1 if non-indexed.
   * @return list of supported parameters, null if none supported.
   */
  protected List<? extends Parameter> getParameterList(int ndx) {
    if (ndx < 0) return getParameterList();
    return null;
  }

  /**
   * Agents providing dynamic parameters may override this method to provide
   * a value for a given parameter.
   *
   * @param p parameter to get value.
   * @param ndx index for indexed parameters, -1 if non-indexed.
   * @return value of the parameter.
   */
  protected Object getParam(Parameter p, int ndx) {
    return null;
  }

  /**
   * Agents providing dynamic parameters may override this method to set
   * a value for a given parameter.
   *
   * @param p parameter to set value.
   * @param ndx index for indexed parameters, -1 if non-indexed.
   * @param v value of the parameter.
   * @return new value of the parameter.
   */
  protected Object setParam(Parameter p, int ndx, Object v) {
    return null;
  }

  /**
   * Generate a list of parameters from a parameter enumeration.
   *
   * @param paramEnumClasses enums representing the parameters.
   * @return list of parameters.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected static List<? extends Parameter> allOf(Class ... paramEnumClasses) {
    List<? extends Parameter> p = new ArrayList<Parameter>();
    for (int i = 0; i < paramEnumClasses.length; i++)
      p.addAll(EnumSet.allOf(paramEnumClasses[i]));
    return p;
  }

  /**
   * Default handling of parameter requests is provided by this method.
   * Agents wishing to have special handling may override this method.
   *
   * @param msg incoming parameter request.
   * @param rsp response message to fill in to send back, or null to create new one.
   * @return response message
   */
  protected ParameterRsp processParameterReq(ParameterReq msg, ParameterRsp rsp) {
    if (rsp == null) rsp = new ParameterRsp(msg);
    int ndx = msg.getIndex();
    List<? extends Parameter> plist = null;
    if (msg.requests().isEmpty()) {
      plist = ndx < 0 ? getParameterList() : getParameterList(ndx);
      if (plist != null) msg.get(plist);
      msg.get(new NamedParameter("title"));           // special optional parameter
      msg.get(new NamedParameter("description"));     // special optional parameter
    }
    Class<?> cls = agent.getClass();
    for (ParameterReq.Entry e : msg.requests()) {
      if (e.param instanceof NamedParameter) {
        if (plist == null) {
          plist = ndx < 0 ? getParameterList() : getParameterList(ndx);
          if (plist == null) plist = new ArrayList<Parameter>(1);
        }
        for (Parameter p: plist) {
          if (p.toString().equals(e.param.toString())) {
            e.param = p;
            break;
          }
        }
      }
      try {
        String fldName = e.param.toString();
        String methodNameFragment = fldName.substring(0, 1).toUpperCase() + fldName.substring(1);
        Object evalue = e.getValue();
        if (evalue == null) {
          // get request
          try {
            if (fldName.equals("type")) rsp.set(e.param, agent.getClass().getName());     // special automatic parameter
            else if (ndx < 0) rsp.set(e.param, MethodUtils.invokeMethod(agent, "get" + methodNameFragment));
            else rsp.set(e.param, MethodUtils.invokeMethod(agent, "get" + methodNameFragment, ndx));
          } catch (NoSuchMethodException ex) {
            Object rv = getParam(e.param, ndx);
            if (rv != null) rsp.set(e.param, rv);
            else {
              if (ndx >= 0) throw ex;
              Field f = cls.getField(fldName);
              rsp.set(e.param, f.get(agent));
            }
          }
        } else {
          // set request
          try {
            Method m = null;
            Object sv = null;
            if (ndx < 0) sv = invokeCompatibleSetter("set" + methodNameFragment, evalue);
            else sv = invokeCompatibleSetter("set" + methodNameFragment, ndx, evalue);
            if (sv == null) {
              try {
                if (ndx < 0) sv = MethodUtils.invokeMethod(agent, "get" + methodNameFragment);
                else sv = MethodUtils.invokeMethod(agent, "get" + methodNameFragment, ndx);
              } catch (NoSuchMethodException ex) {
                sv = getParam(e.param, ndx);
                if (sv == null) sv = evalue;
              }
            }
            if (sv != null) rsp.set(e.param, sv);
          } catch (NoSuchMethodException ex) {
            Object rv = setParam(e.param, ndx, evalue);
            if (rv != null) {
              rsp.set(e.param, rv);
            } else {
              if (ndx >= 0) throw ex;
              Field f = cls.getField(fldName);
              try {
                f.set(agent, evalue);
              } catch (IllegalAccessException ex1) {
                // do nothing
              }
              rsp.set(e.param, f.get(agent));
            }
          }
        }
      } catch (Exception ex) {
        // do nothing
      }
    }
    return rsp;
  }

  private Object invokeCompatibleSetter(String name, Object value) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    NoSuchMethodException nsme = null;
    try {
      return MethodUtils.invokeMethod(agent, name, value);
    } catch (NoSuchMethodException ex) {
      nsme = ex;
    }
    if (value instanceof Number) {
      Number nvalue = (Number) value;
      try {
        return MethodUtils.invokeMethod(agent, name, nvalue.doubleValue());
      } catch (NoSuchMethodException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, nvalue.floatValue());
      } catch (NoSuchMethodException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, nvalue.longValue());
      } catch (NoSuchMethodException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, nvalue.intValue());
      } catch (NoSuchMethodException ex) {
        // do nothing
      }
    }
    throw nsme;
  }

  private Object invokeCompatibleSetter(String name, int ndx, Object value) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    NoSuchMethodException nsme = null;
    try {
      return MethodUtils.invokeMethod(agent, name, ndx, value);
    } catch (NoSuchMethodException ex) {
      nsme = ex;
    }
    if (value instanceof Number) {
      Number nvalue = (Number) value;
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, nvalue.doubleValue());
      } catch (NoSuchMethodException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, nvalue.floatValue());
      } catch (NoSuchMethodException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, nvalue.longValue());
      } catch (NoSuchMethodException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, nvalue.intValue());
      } catch (NoSuchMethodException ex) {
        // do nothing
      }
    }
    throw nsme;
  }

}
