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

  private static final Logger log = Logger.getLogger(EnumTypeAdapter.class.getName());
  private static final JsonClassResolver resolver = new JsonClassResolver(JsonClassResolver.scriptLoader, Enum.class);

  /**
   * Registers additional class that requires special handling for JSON serialization.
   */
  public static void enable(Class<?> cls) {
    JsonMessage.addTypeHierarchyAdapter(cls, new EnumTypeAdapter());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Class<? extends Enum> enumClass(String clsname) {
    try {
      Class<? extends Enum> cls = (Class<? extends Enum>)resolver.resolveNested(clsname);
      if (cls != null) return cls;
    } catch (JsonSyntaxException ex) {
      // not an enum, so treat it as a named parameter
    }
    log.fine("Class "+clsname+" could not be loaded");
    return null;
  }

  @Override public void write(JsonWriter out, Object value) throws IOException {
    if (value == null) out.value((String)null);
    else if (value instanceof NamedParameter) out.value(value.toString());
    else out.value(value.getClass().getName().replace('$','.')+"."+ value);
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
    if (cls != null) {
      try {
        return Enum.valueOf(cls, value);
      } catch (IllegalArgumentException ex) {
        // the constant is unknown, perhaps because it was removed in a newer version;
        // fall back to a named parameter, so that the rest of the message survives
        log.fine("No enum constant "+s+", treating as named parameter");
      }
    }
    return new NamedParameter(s);
  }

}
