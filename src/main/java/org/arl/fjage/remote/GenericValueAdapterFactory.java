/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.arl.fjage.GenericValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Handles conversion of various data types to JSON.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class GenericValueAdapterFactory implements TypeAdapterFactory {

  private static final JsonClassResolver resolver = new JsonClassResolver(GenericValueAdapterFactory.class.getClassLoader(), Object.class);

  public <T> TypeAdapter<T> create(final Gson gson, TypeToken<T> type) {
    final Class<T> rawType = (Class<T>)type.getRawType();
    if (!rawType.equals(GenericValue.class)) return null;
    final GenericValueAdapterFactory parent = this;
    final TypeAdapter<Number> numberDelegate = gson.getAdapter(Number.class);
    final TypeAdapter<JsonElement> treeDelegate = gson.getAdapter(JsonElement.class);
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
        if (in.peek() == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        return (T) new GenericValue(value(in));
      }

      private Object value(JsonReader in) throws IOException {
        switch (in.peek()) {
          case NUMBER: return numberDelegate.read(in);
          case STRING: return in.nextString();
          case BOOLEAN: return in.nextBoolean();
          case BEGIN_ARRAY:
            List<Object> list = new ArrayList<>();
            in.beginArray();
            while (in.hasNext()) list.add(value(in));
            in.endArray();
            return list;
          case BEGIN_OBJECT: return object(in);
          default:
            in.skipValue();
            return null;
        }
      }

      // {"clazz":<resolvable name>,"data":<value>} is a typed value; any other object is a map
      private Object object(JsonReader in) throws IOException {
        Map<String,Object> map = new LinkedHashMap<>();
        Class<?> cls = null;
        JsonElement early = null;
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          if (name.equals("clazz") && in.peek() == JsonToken.STRING) {
            String s = in.nextString();
            map.put(name, s);
            cls = resolver.resolve(s);
          } else if (name.equals("data") && cls != null) {
            Object v = gson.getAdapter(cls).read(in);
            while (in.hasNext()) {
              in.nextName();
              in.skipValue();
            }
            in.endObject();
            return v;
          } else if (name.equals("data") && !map.containsKey("clazz")) {
            early = treeDelegate.read(in);
            map.put(name, null);
          } else map.put(name, value(in));
        }
        in.endObject();
        if (early == null) return map;
        if (cls != null) return gson.fromJson(early, cls);
        map.put("data", value(early));
        return map;
      }

      // same as value(JsonReader), for data buffered before its clazz
      private Object value(JsonElement e) {
        if (e.isJsonNull()) return null;
        if (e.isJsonArray()) {
          List<Object> list = new ArrayList<>();
          for (JsonElement x: e.getAsJsonArray()) list.add(value(x));
          return list;
        }
        if (e.isJsonObject()) {
          JsonObject obj = e.getAsJsonObject();
          JsonElement clazz = obj.get("clazz");
          Class<?> cls = clazz != null && clazz.isJsonPrimitive() && clazz.getAsJsonPrimitive().isString() ? resolver.resolve(clazz.getAsString()) : null;
          if (cls != null && obj.has("data")) return gson.fromJson(obj.get("data"), cls);
          Map<String,Object> map = new LinkedHashMap<>();
          for (Map.Entry<String,JsonElement> x: obj.entrySet()) map.put(x.getKey(), value(x.getValue()));
          return map;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return gson.fromJson(p, Number.class);
        return p.getAsString();
      }

    };
  }

}
