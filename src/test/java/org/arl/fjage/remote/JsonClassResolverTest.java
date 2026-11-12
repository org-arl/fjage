/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import com.google.gson.JsonSyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.arl.fjage.Message;
import org.arl.fjage.param.Parameter;
import org.junit.Test;

public class JsonClassResolverTest {

  static volatile boolean trapInitialized = false;
  static volatile boolean messageInitialized = false;

  public static class Trap {
    static { trapInitialized = true; }
  }

  public static class InitMessage extends Message {
    private static final long serialVersionUID = 1L;
    static { messageInitialized = true; }
  }

  public static class Outer {
    public static class Inner {
      public enum Level implements Parameter { low, high }
    }
  }

  public static class LevelMessage extends Message {
    private static final long serialVersionUID = 1L;
    public Parameter level;
  }

  /** Counts loadClass calls per name. */
  static class CountingLoader extends ClassLoader {
    final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    CountingLoader() { super(JsonClassResolverTest.class.getClassLoader()); }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      calls.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
      return super.loadClass(name, resolve);
    }
    int calls(String name) {
      AtomicInteger n = calls.get(name);
      return n == null ? 0 : n.get();
    }
  }

  private static String envelope(String clazz) {
    return "{\"action\":\"send\",\"message\":{\"clazz\":\"" + clazz + "\",\"data\":{\"msgID\":\"m1\"}}}";
  }

  @Test
  public void nestedNameResolvesOnceAndIsCached() {
    CountingLoader loader = new CountingLoader();
    JsonClassResolver resolver = new JsonClassResolver(loader, Enum.class);
    String name = Outer.Inner.Level.class.getName().replace('$', '.');
    assertEquals(Outer.Inner.Level.class, resolver.resolveNested(name));
    assertEquals(Outer.Inner.Level.class, resolver.resolveNested(name));
    assertEquals(1, loader.calls(name));
  }

  @Test
  public void classOfWrongTypeFailsWithoutInitializing() {
    JsonClassResolver resolver = new JsonClassResolver(getClass().getClassLoader(), Message.class);
    assertThrows(JsonSyntaxException.class, () -> resolver.resolve(Trap.class.getName()));
    assertThrows(JsonSyntaxException.class, () -> JsonMessage.fromJson(envelope(Trap.class.getName())));
    assertFalse(trapInitialized);
  }

  @Test
  public void messageClassIsInitializedOnDecode() {
    Message msg = JsonMessage.fromJson(envelope(InitMessage.class.getName())).message;
    assertEquals(InitMessage.class, msg.getClass());
    assertTrue(messageInitialized);
  }

  @Test
  public void enumNestedTwoLevelsDecodes() {
    LevelMessage msg = new LevelMessage();
    msg.level = Outer.Inner.Level.high;
    JsonMessage env = new JsonMessage();
    env.message = msg;
    LevelMessage decoded = (LevelMessage)JsonMessage.fromJson(env.toJson()).message;
    assertEquals(Outer.Inner.Level.high, decoded.level);
  }

}
