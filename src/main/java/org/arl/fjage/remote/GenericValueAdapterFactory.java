/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.arl.fjage.GenericValue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Handles conversion of various data types to JSON.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class GenericValueAdapterFactory implements TypeAdapterFactory {

  public <T> TypeAdapter<T> create(final Gson gson, TypeToken<T> type) {
    final Class<T> rawType = (Class<T>)type.getRawType();
    if (!rawType.equals(GenericValue.class)) return null;
    final GenericValueAdapterFactory parent = this;
    final TypeAdapter<Number> numberDelegate = gson.getAdapter(Number.class);
    final TypeAdapter<List> listDelegate = gson.getAdapter(List.class);
    return new TypeAdapter<T>() {

      @Override
      public void write(JsonWriter out, T value) throws IOException {
        if (value == null) {
          out.nullValue();
          return;
        }
        Class type = ((GenericValue)value).getType();
        if (type == null) {
          out.nullValue();
          return;
        }
        if (Number.class.isAssignableFrom(type)) out.value((Number)((GenericValue)value).getValue());
        else if (type.equals(String.class)) out.value((String)((GenericValue)value).getValue());
        else if (type.equals(Boolean.class)) out.value((Boolean)((GenericValue)value).getValue());
        else if (List.class.isAssignableFrom(type) || (type.isArray() && type.getComponentType().isPrimitive())) {
          TypeAdapter delegate = gson.getAdapter(TypeToken.get(type));
          Object v = ((GenericValue)value).getValue();
          delegate.write(out, v);
        }
        else if (Map.class.isAssignableFrom(type)) {
          TypeAdapter delegate = gson.getAdapter(TypeToken.get(type));
          Object v = ((GenericValue)value).getValue();
          delegate.write(out, v);
        }
        else {
          out.beginObject();
          out.name("clazz").value(type.getName());
          out.name("data");
          TypeAdapter delegate = gson.getAdapter(TypeToken.get(type));
          Object v = ((GenericValue)value).getValue();
          delegate.write(out, v);
          out.endObject();
        }
      }

      @Override
      public T read(JsonReader in) throws IOException {
        JsonToken tok = in.peek();
        if (tok == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        if (tok == JsonToken.NUMBER) return (T) new GenericValue(numberDelegate.read(in));
        if (tok == JsonToken.STRING) return (T) new GenericValue(in.nextString());
        if (tok == JsonToken.BOOLEAN) return (T) new GenericValue(in.nextBoolean());
        if (tok == JsonToken.BEGIN_OBJECT) {
          TypeToken tt = null;
          GenericValue rv = null;
          Map<String,Object> map = new HashMap<>();
          in.beginObject();
          while (in.hasNext()) {
            String name = in.nextName();
            if (name.equals("clazz")) {
              try {
                Class<?> cls = Class.forName(in.nextString());
                tt = TypeToken.get(cls);
              } catch (Exception ex) {
                // do nothing
              }
            } else if (name.equals("data") && tt != null) {
              TypeAdapter delegate = gson.getAdapter(tt);
              rv = new GenericValue(delegate.read(in));
            }
            else {
              JsonToken tok2 = in.peek();
              if (tok2 == JsonToken.NULL) {
                in.nextNull();
                map.put(name, null);
              }
              else if (tok2 == JsonToken.NUMBER) map.put(name, numberDelegate.read(in));
              else if (tok2 == JsonToken.STRING) map.put(name, in.nextString());
              else if (tok2 == JsonToken.BOOLEAN) map.put(name, in.nextBoolean());
              else if (tok2 == JsonToken.BEGIN_OBJECT) {
                TypeAdapter delegate = gson.getAdapter(TypeToken.get(Map.class));
                map.put(name, delegate.read(in));
              }
              else if (tok2 == JsonToken.BEGIN_ARRAY) map.put(name, listDelegate.read(in));
              else in.skipValue();
            }
          }
          in.endObject();
          if (rv == null) rv = new GenericValue(map);
          return (T)rv;
        }
        if (tok == JsonToken.BEGIN_ARRAY) return (T) new GenericValue(listDelegate.read(in));
        return null;
      }

    };
  }

}
