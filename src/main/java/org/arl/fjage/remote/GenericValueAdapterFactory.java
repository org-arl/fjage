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
import java.util.ArrayList;
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
        if (tok == JsonToken.NUMBER) {
          String s = in.nextString();
          try {
            if (s.contains(".")) return (T) new GenericValue(Double.parseDouble(s));
            else return (T) new GenericValue(Long.parseLong(s));
          } catch (NumberFormatException ex) {
            return (T) new GenericValue(null);
          }
        }
        if (tok == JsonToken.STRING) return (T) new GenericValue(in.nextString());
        if (tok == JsonToken.BOOLEAN) return (T) new GenericValue(in.nextBoolean());
        if (tok == JsonToken.BEGIN_OBJECT) {
          TypeToken tt = null;
          GenericValue rv = null;
          Map<String,Object> map = new HashMap<String,Object>();
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
              else if (tok2 == JsonToken.NUMBER) {
                String s = in.nextString();
                try {
                  if (s.contains(".")) map.put(name, Double.parseDouble(s));
                  else map.put(name, Long.parseLong(s));
                } catch (NumberFormatException ex) {
                  // ignore
                }
              }
              else if (tok2 == JsonToken.STRING) map.put(name, in.nextString());
              else if (tok2 == JsonToken.BOOLEAN) map.put(name, in.nextBoolean());
              else in.skipValue();
            }
          }
          in.endObject();
          if (rv == null) rv = new GenericValue(map);
          return (T)rv;
        }
        if (tok == JsonToken.BEGIN_ARRAY) {
          List<Object> list = new ArrayList<Object>();
          in.beginArray();
          while (in.hasNext()) {
            JsonToken tok2 = in.peek();
            String s = in.nextString();
            if (tok2 != JsonToken.NUMBER) list.add(s);
            else {
              try {
                if (s.contains(".")) list.add(Double.parseDouble(s));
                else list.add(Long.parseLong(s));
              } catch (NumberFormatException ex) {
                list.add(s);
              }
            }
          }
          in.endArray();
          return (T) new GenericValue(list);
        }
        return null;
      }

    };
  }

}
