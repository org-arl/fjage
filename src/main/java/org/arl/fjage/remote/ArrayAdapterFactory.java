/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

  private final boolean bare;
  private final int threshold;

  public ArrayAdapterFactory() {
    bare = false;
    threshold = 0;
  }

  public ArrayAdapterFactory(boolean bare, int threshold) {
    this.bare = bare;
    this.threshold = threshold;
  }

  // Quoted base64 for JsonWriter.jsonValue(), as base64 needs no escaping. Built in one
  // buffer, since quoting encodeToString() output copies large arrays several more times.
  private static String quoted(byte[] data) {
    byte[] q = new byte[(data.length+2)/3*4+2];
    int n = Base64.getEncoder().encode(data, q);
    System.arraycopy(q, 0, q, 1, n);
    q[0] = q[n+1] = '"';
    return new String(q, StandardCharsets.ISO_8859_1);
  }

  // writers that cannot take raw JSON, such as Gson's tree writer, get a plain string
  private static void writeBase64(JsonWriter out, byte[] data) throws IOException {
    try {
      out.jsonValue(quoted(data));
    } catch (UnsupportedOperationException ex) {
      out.value(Base64.getEncoder().encodeToString(data));
    }
  }

  // plain JSON arrays of floats and doubles, where null stands for NaN
  private static double[] doubles(JsonReader in) throws IOException {
    double[] a = new double[16];
    int n = 0;
    in.beginArray();
    while (in.hasNext()) {
      if (n == a.length) a = Arrays.copyOf(a, 2*n);
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        a[n++] = Double.NaN;
      } else a[n++] = in.nextDouble();
    }
    in.endArray();
    return Arrays.copyOf(a, n);
  }

  /** Little-endian packing, and plain JSON array reading, for each primitive array type. */
  private enum Codec {
    BYTE(byte[].class, 1) {
      byte[] pack(Object a) { return (byte[])a; }
      Object unpack(ByteBuffer b) { return b.array(); }
    },
    SHORT(short[].class, 2) {
      byte[] pack(Object a) { ByteBuffer b = buffer(a); b.asShortBuffer().put((short[])a); return b.array(); }
      Object unpack(ByteBuffer b) { ShortBuffer v = b.asShortBuffer(); short[] a = new short[v.limit()]; v.get(a); return a; }
    },
    INT(int[].class, 4) {
      byte[] pack(Object a) { ByteBuffer b = buffer(a); b.asIntBuffer().put((int[])a); return b.array(); }
      Object unpack(ByteBuffer b) { IntBuffer v = b.asIntBuffer(); int[] a = new int[v.limit()]; v.get(a); return a; }
    },
    LONG(long[].class, 8) {
      byte[] pack(Object a) { ByteBuffer b = buffer(a); b.asLongBuffer().put((long[])a); return b.array(); }
      Object unpack(ByteBuffer b) { LongBuffer v = b.asLongBuffer(); long[] a = new long[v.limit()]; v.get(a); return a; }
    },
    FLOAT(float[].class, 4) {
      byte[] pack(Object a) { ByteBuffer b = buffer(a); b.asFloatBuffer().put((float[])a); return b.array(); }
      Object unpack(ByteBuffer b) { FloatBuffer v = b.asFloatBuffer(); float[] a = new float[v.limit()]; v.get(a); return a; }
      Object read(JsonReader in, TypeAdapter<?> delegate) throws IOException {
        double[] d = doubles(in);
        float[] a = new float[d.length];
        for (int i = 0; i < d.length; i++) a[i] = (float)d[i];
        return a;
      }
    },
    DOUBLE(double[].class, 8) {
      byte[] pack(Object a) { ByteBuffer b = buffer(a); b.asDoubleBuffer().put((double[])a); return b.array(); }
      Object unpack(ByteBuffer b) { DoubleBuffer v = b.asDoubleBuffer(); double[] a = new double[v.limit()]; v.get(a); return a; }
      Object read(JsonReader in, TypeAdapter<?> delegate) throws IOException { return doubles(in); }
    };

    final Class<?> type;
    final int size;

    Codec(Class<?> type, int size) {
      this.type = type;
      this.size = size;
    }

    static Codec of(String name) {
      for (Codec c: values())
        if (c.type.getName().equals(name)) return c;
      return null;
    }

    ByteBuffer buffer(Object a) {
      return ByteBuffer.allocate(size*Array.getLength(a)).order(ByteOrder.LITTLE_ENDIAN);
    }

    abstract byte[] pack(Object a);
    abstract Object unpack(ByteBuffer b);

    Object read(JsonReader in, TypeAdapter<?> delegate) throws IOException {
      return delegate.read(in);
    }
  }

  @SuppressWarnings("unchecked")
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    final Class<T> rawType = (Class<T>)type.getRawType();
    final Codec codec = Codec.of(rawType.getName());
    if (codec == null) return null;
    final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
    return new TypeAdapter<T>() {

      @Override
      public void write(JsonWriter out, T value) throws IOException {
        if (value == null) out.nullValue();
        else if (Array.getLength(value) < threshold) {
          out.beginArray();
          for (int i = 0, n = Array.getLength(value); i < n; i++) out.value((Number)Array.get(value, i));
          out.endArray();
        }
        else if (bare) writeBase64(out, codec.pack(value));
        else {
          out.beginObject();
          out.name("clazz").value(rawType.getName());
          out.name("data");
          writeBase64(out, codec.pack(value));
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
        if (tok == JsonToken.STRING) return decode(in.nextString());
        if (tok == JsonToken.BEGIN_ARRAY) return (T)codec.read(in, delegate);
        if (tok != JsonToken.BEGIN_OBJECT) return null;
        T rv = null;
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          if (name.equals("data")) rv = decode(in.nextString());
          else if (name.equals("clazz") && in.peek() == JsonToken.STRING) {
            String clazz = in.nextString();
            Codec c = Codec.of(clazz);
            if (c != null && c != codec) throw new JsonSyntaxException("Expected " + rawType.getName() + " but received " + clazz);
          }
          else in.skipValue();
        }
        in.endObject();
        return rv;
      }

      private T decode(String s) {
        byte[] data = Base64.getDecoder().decode(s);
        if (data.length % codec.size != 0)
          throw new JsonSyntaxException("Base64 data for " + rawType.getName() + " has " + data.length + " bytes, not a multiple of " + codec.size);
        return (T)codec.unpack(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
      }

    };
  }

}
