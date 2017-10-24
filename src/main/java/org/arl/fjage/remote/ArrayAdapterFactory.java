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

/**
 * Array adapter factory for custom JSON representation.
 *
 * Numeric arrays are compressed into a base 64 notation for quick transmission
 * over a network.
 */
class ArrayAdapterFactory implements TypeAdapterFactory {

  private boolean bare;

  public ArrayAdapterFactory() {
    bare = true;
  }

  public ArrayAdapterFactory(boolean bare) {
    this.bare = bare;
  }

  @SuppressWarnings("unchecked")
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    final Class<T> rawType = (Class<T>)type.getRawType();
    if (!rawType.isArray()) return null;
    final Class<?> compType = rawType.getComponentType();
    if (compType == null) return null;
    if (!compType.isPrimitive()) return null;
    if (!compType.equals(byte.class) && !compType.equals(int.class) &&
        !compType.equals(short.class) && !compType.equals(long.class) &&
        !compType.equals(float.class) && !compType.equals(double.class)) return null;
    final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
    return new TypeAdapter<T>() {

      @Override
      public void write(JsonWriter out, T value) throws IOException {
        if (value == null) out.nullValue();
        else {
          byte[] data;
          if (compType.equals(byte.class)) data = (byte[])value;
          else {
            ByteBuffer buf = null;
            if (compType.equals(int.class)) {
              buf = ByteBuffer.allocate(Integer.SIZE/Byte.SIZE*((int[])value).length);
              buf.asIntBuffer().put((int[])value);
            } else if (compType.equals(short.class)) {
              buf = ByteBuffer.allocate(Short.SIZE/Byte.SIZE*((short[])value).length);
              buf.asShortBuffer().put((short[])value);
            } else if (compType.equals(long.class)) {
              buf = ByteBuffer.allocate(Long.SIZE/Byte.SIZE*((long[])value).length);
              buf.asLongBuffer().put((long[])value);
            } else if (compType.equals(float.class)) {
              buf = ByteBuffer.allocate(Float.SIZE/Byte.SIZE*((float[])value).length);
              buf.asFloatBuffer().put((float[])value);
            } else if (compType.equals(double.class)) {
              buf = ByteBuffer.allocate(Double.SIZE/Byte.SIZE*((double[])value).length);
              buf.asDoubleBuffer().put((double[])value);
            }
            data = buf.array();
          }
          if (bare) out.value(Base64.getEncoder().encodeToString(data));
          else {
            out.beginObject();
            out.name("clazz").value(rawType.getName());
            out.name("data").value(Base64.getEncoder().encodeToString(data));
            out.endObject();
          }
        }
      }

      @Override
      public T read(JsonReader in) throws IOException {
        JsonToken tok = in.peek();
        if (tok == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        if (tok == JsonToken.STRING) return decodeString(in.nextString());
        if (tok == JsonToken.BEGIN_ARRAY) return delegate.read(in);
        if (tok != JsonToken.BEGIN_OBJECT) return null;
        T rv = null;
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          if (name.equals("data")) rv = decodeString(in.nextString());
          else in.skipValue();
        }
        in.endObject();
        return rv;
      }

      private T decodeString(String s) {
        byte[] data = Base64.getDecoder().decode(s);
        if (compType.equals(byte.class)) return (T)data;
        ByteBuffer buf = ByteBuffer.wrap(data);
        if (compType.equals(int.class)) {
          IntBuffer buf2 = ByteBuffer.wrap(Base64.getDecoder().decode(s)).asIntBuffer();
          int[] array = new int[buf2.limit()];
          buf2.get(array);
          return (T)array;
        }
        if (compType.equals(short.class)) {
          ShortBuffer buf2 = ByteBuffer.wrap(Base64.getDecoder().decode(s)).asShortBuffer();
          short[] array = new short[buf2.limit()];
          buf2.get(array);
          return (T)array;
        }
        if (compType.equals(long.class)) {
          LongBuffer buf2 = ByteBuffer.wrap(Base64.getDecoder().decode(s)).asLongBuffer();
          long[] array = new long[buf2.limit()];
          buf2.get(array);
          return (T)array;
        }
        if (compType.equals(float.class)) {
          FloatBuffer buf2 = ByteBuffer.wrap(Base64.getDecoder().decode(s)).asFloatBuffer();
          float[] array = new float[buf2.limit()];
          buf2.get(array);
          return (T)array;
        }
        if (compType.equals(double.class)) {
          DoubleBuffer buf2 = ByteBuffer.wrap(Base64.getDecoder().decode(s)).asDoubleBuffer();
          double[] array = new double[buf2.limit()];
          buf2.get(array);
          return (T)array;
        }
        return null;
      }

    };
  }

}
