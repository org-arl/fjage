/******************************************************************************

Copyright (c) 2020, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.io.IOException;
import java.util.logging.Logger;
import org.arl.fjage.param.NamedParameter;
import com.google.gson.*;
import com.google.gson.stream.*;

/**
 * Type adapter used for custom serialization of enums.
 */
public class EnumTypeAdapter extends TypeAdapter<Object> {

  private static ClassLoader classloader = null;
  private static Logger log;

  static {
    log = Logger.getLogger(EnumTypeAdapter.class.getName());
    try {
      Class<?> cls = Class.forName("groovy.lang.GroovyClassLoader");
      classloader = (ClassLoader)cls.getDeclaredConstructor().newInstance();
      log.info("Groovy detected, using GroovyClassLoader");
    } catch (Exception ex) {
      // do nothing
    }
  }

  /**
   * Registers additional class that requires special handling for JSON serialization.
   */
  public static void enable(Class<?> cls) {
    JsonMessage.addTypeHierarchyAdapter(cls, new EnumTypeAdapter());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Class<? extends Enum> enumClass(String clsname) {
    try {
      if (classloader != null) return (Class<? extends Enum>)Class.forName(clsname, true, classloader);
      else return (Class<? extends Enum>)Class.forName(clsname);
    } catch (ClassNotFoundException ex) {
      // ignore this, and try next method
    }
    // if inner class, have to load main class first
    try {
      int pos = clsname.lastIndexOf('.');
      if (pos >= 0) {
        String clsname1 = clsname.substring(0,pos);
        if (classloader != null) Class.forName(clsname1, true, classloader);
        else Class.forName(clsname1);
        clsname1 = clsname1 + '$' + clsname.substring(pos+1);
        if (classloader != null) return (Class<? extends Enum>)Class.forName(clsname1, true, classloader);
        else return (Class<? extends Enum>)Class.forName(clsname1);
      }
    } catch (ClassNotFoundException ex) {
      // ignore this, as we'll return an error below
    }
    log.fine("Class "+clsname+" could not be loaded");
    return null;
  }

  @Override public void write(JsonWriter out, Object value) throws IOException {
    if (value == null) out.value((String)null);
    else if (value instanceof NamedParameter) out.value(value.toString());
    else out.value(value.getClass().getName().replace('$','.')+"."+value.toString());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override public Object read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    String s = in.nextString();
    int pos = s.lastIndexOf('.');
    if (pos < 0) return new NamedParameter(s);
    String value = s.substring(pos+1);
    Class<? extends Enum> cls = enumClass(s.substring(0,pos));
    if (cls != null) return Enum.valueOf(cls, value);
    if (value != null) return new NamedParameter(s);
    return null;
  }

}
