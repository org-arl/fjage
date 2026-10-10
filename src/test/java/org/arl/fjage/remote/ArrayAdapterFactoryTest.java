/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.arl.fjage.GenericMessage;
import org.arl.fjage.Message;
import org.junit.Test;

/**
 * Tests JSON encoding of primitive arrays and messages carrying them.
 */
public class ArrayAdapterFactoryTest {

  public static class ArrayMsg extends Message {
    public byte[] b;
    public short[] s;
    public int[] i;
    public long[] l;
    public float[] f;
    public double[] d;
  }

  public static class TagMsg extends Message {
    public String tag;
  }

  private static Message roundTrip(Message m) {
    JsonMessage jm = new JsonMessage();
    jm.action = Action.SEND;
    jm.message = m;
    return JsonMessage.fromJson(jm.toJson()).message;
  }

  // lengths 0..3 cover base64 padding of 0, 1 and 2 characters for every element size
  @Test
  public void messageFieldsRoundTrip() {
    for (int n = 0; n < 4; n++) {
      ArrayMsg m = new ArrayMsg();
      m.b = new byte[n];
      m.s = new short[n];
      m.i = new int[n];
      m.l = new long[n];
      m.f = new float[n];
      m.d = new double[n];
      for (int k = 0; k < n; k++) {
        m.b[k] = (byte)(-k-1);
        m.s[k] = (short)(-1000*k-1);
        m.i[k] = -100000*k-1;
        m.l[k] = Long.MIN_VALUE+k;
        m.f[k] = 1.5f*k-0.25f;
        m.d[k] = Math.PI*k-1e-300;
      }
      ArrayMsg r = (ArrayMsg)roundTrip(m);
      assertArrayEquals(m.b, r.b);
      assertArrayEquals(m.s, r.s);
      assertArrayEquals(m.i, r.i);
      assertArrayEquals(m.l, r.l);
      assertArrayEquals(m.f, r.f, 0f);
      assertArrayEquals(m.d, r.d, 0.0);
    }
  }

  @Test
  public void genericMessageRoundTrip() {
    for (int n = 0; n < 4; n++) {
      GenericMessage m = new GenericMessage();
      m.put("b", new byte[n]);
      m.put("s", new short[n]);
      m.put("i", new int[n]);
      m.put("l", new long[n]);
      m.put("f", new float[n]);
      m.put("d", new double[n]);
      for (int k = 0; k < n; k++) {
        ((short[])m.get("s"))[k] = (short)(k+1);
        ((double[])m.get("d"))[k] = k+0.5;
      }
      GenericMessage r = (GenericMessage)roundTrip(m);
      assertArrayEquals((byte[])m.get("b"), (byte[])r.get("b"));
      assertArrayEquals((short[])m.get("s"), (short[])r.get("s"));
      assertArrayEquals((int[])m.get("i"), (int[])r.get("i"));
      assertArrayEquals((long[])m.get("l"), (long[])r.get("l"));
      assertArrayEquals((float[])m.get("f"), (float[])r.get("f"), 0f);
      assertArrayEquals((double[])m.get("d"), (double[])r.get("d"), 0.0);
    }
  }

  @Test
  public void base64IsWrittenUnescaped() {
    ArrayMsg m = new ArrayMsg();
    m.f = new float[] { 1f };
    JsonMessage jm = new JsonMessage();
    jm.action = Action.SEND;
    jm.message = m;
    assertTrue(jm.toJson().contains("{\"clazz\":\"[F\",\"data\":\"AACAPw==\"}"));
  }

  @Test
  public void interleavedMessageClasses() {
    for (int k = 0; k < 3; k++) {
      ArrayMsg a = new ArrayMsg();
      a.i = new int[] { k };
      TagMsg t = new TagMsg();
      t.tag = "t"+k;
      assertArrayEquals(a.i, ((ArrayMsg)roundTrip(a)).i);
      assertEquals(t.tag, ((TagMsg)roundTrip(t)).tag);
    }
  }

}
