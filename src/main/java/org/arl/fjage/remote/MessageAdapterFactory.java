/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.*;
import org.arl.fjage.*;
import com.google.gson.*;
import com.google.gson.stream.*;
import com.google.gson.reflect.TypeToken;

/**
 * Message adapter for custom JSON representation.
 *
 * This adapter adds a "clazz" property to the JSON representation of the message,
 * representing the fully qualified message class name. This enables the message to
 * be unmarshalled into the appropriate message class at the destination.
 */
public class MessageAdapterFactory implements TypeAdapterFactory {

  private static ClassLoader classloader = null;

  static {
    try {
      Class<?> cls = Class.forName("groovy.lang.GroovyClassLoader");
      classloader = (ClassLoader)cls.newInstance();
      Logger log = Logger.getLogger(MessageAdapterFactory.class.getName());
      log.info("Groovy detected, using GroovyClassLoader");
    } catch (Exception ex) {
      // do nothing
    }
  }

  @SuppressWarnings("unchecked")
  public <T> TypeAdapter<T> create(final Gson gson, TypeToken<T> type) {
    final Class<T> rawType = (Class<T>)type.getRawType();
    if (!Message.class.isAssignableFrom(rawType)) return null;
    final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
    final MessageAdapterFactory parent = this;
    return new TypeAdapter<T>() {

      @Override
      public void write(JsonWriter out, T value) throws IOException {
        if (value == null) out.nullValue();
        else {
          out.beginObject();
          out.name("clazz").value(value.getClass().getName());
          out.name("data");
          delegate.write(out, value);
          out.endObject();
        }
      }

      @Override
      public T read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        T rv = null;
        Class<?> cls = null;
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          if (name.equals("clazz")) {
            String className = in.nextString();
            try {
              cls = classloader != null ? Class.forName(className, true, classloader) : Class.forName(className);
            } catch (Exception ex) {
              // do nothing
            }
          } else if (name.equals("data")) {
            if (cls == null) rv = delegate.read(in);
            else {
              TypeAdapter<?> delegate1 = gson.getDelegateAdapter(parent, TypeToken.get(cls));
              rv = (T)delegate1.read(in);
              if (rv instanceof Map) {
                try {
                  Map<?,Object> map = (Map<?,Object>)rv;
                  for (Map.Entry<?,Object> entry: map.entrySet()) {
                    Object v = entry.getValue();
                    if (v != null && v instanceof Map) {
                      String className2 = (String)((Map)v).get("clazz");
                      Class<?> cls2 = classloader != null ? Class.forName(className2, true, classloader) : Class.forName(className2);
                      String data = (String)((Map)v).get("data");
                      entry.setValue(gson.fromJson("{\"data\":\""+data+"\"}", cls2));
                    }
                  }
                } catch (Exception ex) {
                  // do nothing
                }
              }
            }
          } else in.skipValue();
        }
        in.endObject();
        return rv;
      }

    };
  }

}
