/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.io.IOException;
import java.nio.*;
import java.util.Base64;
import com.google.gson.*;
import com.google.gson.stream.*;
import com.google.gson.reflect.TypeToken;
import org.arl.fjage.GenericValue;

/**
 * Array adapter factory for custom JSON representation.
 *
 * Numeric arrays are compressed into a base 64 notation for quick transmission
 * over a network.
 */
class GenericValueAdapterFactory implements TypeAdapterFactory {

  @SuppressWarnings("unchecked")
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
        if (tok != JsonToken.BEGIN_OBJECT) return null;
        TypeToken tt = null;
        GenericValue rv = null;
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          if (name.equals("clazz")) {
            try {
              Class cls = Class.forName(in.nextString());
              tt = TypeToken.get(cls);
            } catch (Exception ex) {
              // do nothing
            }
          } else if (name.equals("data") && tt != null) {
            TypeAdapter delegate = gson.getAdapter(tt);
            rv = new GenericValue(delegate.read(in));
          }
          else in.skipValue();
        }
        in.endObject();
        return (T)rv;
      }

    };
  }

}
