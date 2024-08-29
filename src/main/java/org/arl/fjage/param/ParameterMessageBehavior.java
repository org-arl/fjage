/******************************************************************************

Copyright (c) 2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.param;

import java.util.*;
import java.lang.reflect.*;
import org.arl.fjage.*;
import org.apache.commons.lang3.reflect.MethodUtils;

/**
 * Behavior to handle parameter messages. To enable parameters on an agent,
 * simply add this behavior during {@code init()} of the agent:
 * <pre>
 * add(new ParameterMessageBehavior(MyParams.class));
 * </pre>
 * where {@code enum MyParams} lists the parameters supported by the agent.
 * The parameters may be exposed as {@code public} attributes (read-only if
 * marked {@code final}), getters/setters using JavaBean convention, or
 * by overridding {@link #getParam(Parameter, int)} and
 * {@link #setParam(Parameter, int, Object)} methods of this behavior.
 */
public class ParameterMessageBehavior extends MessageBehavior {

  private List<? extends Parameter> params;

  /**
   * Creates a parameter message behavior with no parameters.
   */
  public ParameterMessageBehavior() {
    super(ParameterReq.class);
    this.params = null;
  }

  /**
   * Creates a parameter message behavior with parameters specified in a list.
   */
  public ParameterMessageBehavior(List<? extends Parameter> params) {
    super(ParameterReq.class);
    this.params = params;
  }

  /**
   * Creates a parameter message behavior with parameters specified by enums.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public ParameterMessageBehavior(Class ... paramEnumClasses) {
    super(ParameterReq.class);
    params = new ArrayList<Parameter>();
    for (int i = 0; i < paramEnumClasses.length; i++)
      params.addAll(EnumSet.allOf(paramEnumClasses[i]));
  }

  @Override
  public void onReceive(Message msg) {
    final ParameterReq req = (ParameterReq)msg;
    agent.add(new OneShotBehavior() {
      @Override
      public void action() {
        ParameterRsp rsp = processParameterReq(req, null);
        if (rsp != null) agent.send(rsp);
      }
    });
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
   * Agents may provide a behavior to be executed every time a parameter has
   * been updated by overriding this method.
   *
   * @param p parameter to set value.
   * @param ndx index for indexed parameters, -1 if non-indexed.
   * @param v value of the parameter.
   */
  protected void onParamChange(Parameter p, int ndx, Object v) {

  }

  /**
   * Agents providing dynamic parameters may override this method to specify
   * whether a parameter is read-only or read-write. The default behavior of this
   * method is to guess whether the parameter is read-only based on whether the
   * associated atrribute is marked as {@code  final} or from a missing setter in
   * the Java bean convention.
   *
   * @param p parameter to check.
   * @param ndx index for indexed parameters, -1 if non-indexed.
   * @return true if read-only, false if read-write.
   */
  protected boolean isReadOnly(Parameter p, int ndx) {
    String fldName = p.toString();
    String methodNameFragment = fldName.substring(0, 1).toUpperCase() + fldName.substring(1);
    if (isCallable("set"+methodNameFragment, ndx<0?1:2)) return false;
    if (ndx < 0) {
      try {
        Field f = agent.getClass().getField(fldName);
        int mod = f.getModifiers();
        if (Modifier.isPublic(mod) && !Modifier.isFinal(mod)) return false;
      } catch (NoSuchFieldException ex) {
        // do nothing
      }
    }
    return true;
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
        Object current = null;
        try {
          if (fldName.equals("type")) current = agent.getClass().getName(); // special automatic parameter
          else if (ndx < 0) current = MethodUtils.invokeMethod(agent, "get" + methodNameFragment);
          else current = MethodUtils.invokeMethod(agent, "get" + methodNameFragment, ndx);
        } catch (NoSuchMethodException ex) {
          current = getParam(e.param, ndx);
          if (current == null) {
            if (ndx >= 0 && evalue == null) throw ex;
            Field f = cls.getField(fldName);
            current = f.get(agent);
          }
        }
        if (evalue == null) {
          // get request
          if (fldName.equals("type")) rsp.set(e.param, current, true);     // special automatic parameter
          else rsp.set(e.param, current, isReadOnly(e.param, ndx));
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
            if (sv != null) {
              rsp.set(e.param, sv, false);
              if (!sv.equals(current)) onParamChange(e.param, ndx, sv);
            }
          } catch (NoSuchMethodException ex) {
            Object rv = setParam(e.param, ndx, evalue);
            if (rv != null) {
              rsp.set(e.param, rv, isReadOnly(e.param, ndx));
              if (!rv.equals(current)) onParamChange(e.param, ndx, rv);
            } else {
              if (ndx >= 0) throw ex;
              Field f = cls.getField(fldName);
              boolean ro = false;
              try {
                f.set(agent, evalue);
              } catch (IllegalAccessException ex1) {
                ro = true;
              }
              rv = f.get(agent);
              rsp.set(e.param, rv, ro);
              if (!rv.equals(current)) onParamChange(e.param, ndx, rv);
            }
          }
        }
      } catch (InvocationTargetException ex){
        log.fine ("Error thrown while setting parameter: " + ex.getCause().getMessage());
      } catch (Exception ex) {
        // do nothing
      }
    }
    return rsp;
  }

  private double[] asDoubleArray(List<?> a) {
    try {
      double[] x = new double[a.size()];
      for (int i = 0; i < a.size(); i++)
        x[i] = ((Number)a.get(i)).doubleValue();
      return x;
    } catch (Exception ex) {
      throw new ArrayStoreException();
    }
  }

  private float[] asFloatArray(List<?> a) {
    try {
      float[] x = new float[a.size()];
      for (int i = 0; i < a.size(); i++)
        x[i] = ((Number)a.get(i)).floatValue();
      return x;
    } catch (Exception ex) {
      throw new ArrayStoreException();
    }
  }

  private long[] asLongArray(List<?> a) {
    try {
      long[] x = new long[a.size()];
      for (int i = 0; i < a.size(); i++)
        x[i] = ((Number)a.get(i)).longValue();
      return x;
    } catch (Exception ex) {
      throw new ArrayStoreException();
    }
  }

  private int[] asIntArray(List<?> a) {
    try {
      int[] x = new int[a.size()];
      for (int i = 0; i < a.size(); i++)
        x[i] = ((Number)a.get(i)).intValue();
      return x;
    } catch (Exception ex) {
      throw new ArrayStoreException();
    }
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
    if (value instanceof List) {
      List<?> lvalue = (List<?>) value;
      try {
        return MethodUtils.invokeMethod(agent, name, (Object)asDoubleArray(lvalue));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, (Object)asFloatArray(lvalue));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, (Object)asLongArray(lvalue));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, (Object)asIntArray(lvalue));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, (Object)lvalue.toArray(new String[0]));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
    }
    throw nsme;
  }

  private Object invokeCompatibleSetter(String name, int ndx, Object value) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    NoSuchMethodException nsme = null;
    try {
      return MethodUtils.invokeMethod(agent, name, ndx, value);
    } catch (NoSuchMethodException ignored) {
      // If MethodUtils.invokeMethod fails, we will try to invoke the method by
      // getting the method and invoking it directly
      Class<?>[] parameterTypes = new Class<?>[2];
      parameterTypes[0] = Integer.TYPE;
      parameterTypes[1] = value.getClass();
      try {
        Method m = agent.getClass().getMethod(name, parameterTypes);
        return m.invoke(agent, ndx, value);
      } catch (NoSuchMethodException ex) {
        nsme = ex;
      }
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
    if (value instanceof List) {
      List<?> lvalue = (List<?>) value;
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, asDoubleArray(lvalue));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, asFloatArray(lvalue));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, asLongArray(lvalue));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, asIntArray(lvalue));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
      try {
        return MethodUtils.invokeMethod(agent, name, ndx, lvalue.toArray(new String[0]));
      } catch (NoSuchMethodException | ArrayStoreException ex) {
        // do nothing
      }
    }
    throw nsme;
  }

  private boolean isCallable(String methodName, int params) {
    Class<?> cls = agent.getClass();
    for (Method m: cls.getMethods()) {
      if (m.getName().equals(methodName) && m.getParameterCount() == params) {
        int mod = m.getModifiers();
        if (Modifier.isPublic(mod) && !Modifier.isAbstract(mod)) {
          if (params == 1) return true;
          Class<?>[] p = m.getParameterTypes();
          if (p[0].equals(Integer.class) || p[0].equals(Integer.TYPE)) return true;
        }
      }
    }
    return false;
  }

}
