package org.arl.fjage.param;

import java.util.*;
import java.lang.reflect.*;
import org.arl.fjage.*;
import org.apache.commons.lang3.reflect.MethodUtils;

public class ParameterMessageBehavior extends MessageBehavior {

  private List<Parameter> params;

  public ParameterMessageBehavior() {
    super(ParameterReq.class);
    this.params = null;
  }

  public ParameterMessageBehavior(List<Parameter> params) {
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
    Message rsp = processParameterReq(req);
    if (rsp != null) agent.send(rsp);
  }

  /**
   * An agent supporting dynamic parameters may override this to return a list
   * of parameters available.
   *
   * @return list of supported parameters, null if none supported.
   */
  protected List<Parameter> getParameterList() {
    return params;
  }

  /**
   * Agents providing dynamic parameters may override this method to provide
   * a value for a given parameter.
   *
   * @param p parameter to get value.
   * @return value of the parameter.
   */
  protected Object getParam(Parameter p) {
    return null;
  }

  /**
   * Agents providing dynamic parameters may override this method to set
   * a value for a given parameter.
   *
   * @param p parameter to set value.
   * @param v value of the parameter.
   * @return new value of the parameter.
   */
  protected Object setParam(Parameter p, Object v) {
    return null;
  }

  /**
   * Generate a list of parameters from a parameter enumeration.
   *
   * @param paramEnumClasses enums representing the parameters.
   * @return list of parameters.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected static List<Parameter> allOf(Class ... paramEnumClasses) {
    List<Parameter> p = new ArrayList<Parameter>();
    for (int i = 0; i < paramEnumClasses.length; i++)
      p.addAll(EnumSet.allOf(paramEnumClasses[i]));
    return p;
  }

  /**
   * Default handling of parameter requests is provided by this method.
   * Agents wishing to have special handling may override this method.
   *
   * @param msg incoming parameter request.
   * @return response message to send back.
   */
  protected ParameterRsp processParameterReq(ParameterReq msg) {
    List<Parameter> plist = null;
    if (msg.requests().isEmpty()) {
      plist = getParameterList();
      if (plist != null) msg.get(plist);
      msg.get(new NamedParameter("title"));           // special optional parameter
      msg.get(new NamedParameter("description"));     // special optional parameter
    }
    ParameterRsp rsp = new ParameterRsp(msg);
    Class<?> cls = agent.getClass();
    for (ParameterReq.Entry e : msg.requests()) {
      if (e.param instanceof NamedParameter) {
        if (plist == null) {
          plist = getParameterList();
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
            else rsp.set(e.param, MethodUtils.invokeMethod(agent, "get" + methodNameFragment));
          } catch (NoSuchMethodException ex) {
            Object rv = getParam(e.param);
            if (rv != null) rsp.set(e.param, rv);
            else {
              Field f = cls.getField(fldName);
              rsp.set(e.param, f.get(agent));
            }
          }
        } else {
          // set request
          try {
            Method m = null;
            Object sv = invokeCompatibleSetter("set" + methodNameFragment, evalue);
            if (sv == null) {
              try {
                sv = MethodUtils.invokeMethod(agent, "get" + methodNameFragment);
              } catch (NoSuchMethodException ex) {
                sv = getParam(e.param);
                if (sv == null) sv = evalue;
              }
            }
            if (sv != null) rsp.set(e.param, sv);
          } catch (NoSuchMethodException ex) {
            Object rv = setParam(e.param, evalue);
            if (rv != null) {
              rsp.set(e.param, rv);
            } else {
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


}
