/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import org.arl.fjage.GenericMessage;
import org.arl.fjage.Message;
import org.arl.fjage.Performative;
import org.junit.Test;

public class MessageAdapterFactoryTest {

  public static class NoDefaultConstructor extends GenericMessage {
    private static final long serialVersionUID = 1L;
    public NoDefaultConstructor(int x) { }
  }

  /** Moves "data" before every other member, in every object. */
  private static JsonElement reorder(JsonElement e) {
    if (e.isJsonArray()) {
      JsonArray a = new JsonArray();
      for (JsonElement x : e.getAsJsonArray()) a.add(reorder(x));
      return a;
    }
    if (!e.isJsonObject()) return e;
    JsonObject o = e.getAsJsonObject();
    JsonObject r = new JsonObject();
    if (o.has("data")) r.add("data", reorder(o.get("data")));
    for (Map.Entry<String, JsonElement> m : o.entrySet())
      if (!m.getKey().equals("data")) r.add(m.getKey(), reorder(m.getValue()));
    return r;
  }

  private static GenericMessage generic(String clazz, String data) {
    return (GenericMessage)JsonMessage.fromJson("{\"message\":{\"clazz\":\"" + clazz + "\",\"data\":" + data + "}}").message;
  }

  @Test
  public void fixturesDecodeTheSameWithDataBeforeClazz() throws IOException {
    for (String name : JsonProtocolFixtures.samples().keySet()) {
      String json = JsonProtocolCompatibilityTest.fixture(name);
      String reordered = reorder(JsonParser.parseString(json)).toString();
      assertEquals(name, JsonParser.parseString(JsonMessage.fromJson(json).toJson()),
        JsonParser.parseString(JsonMessage.fromJson(reordered).toJson()));
    }
  }

  @Test
  public void nonFiniteValuesSurviveDataBeforeClazz() {
    JsonProtocolFixtures.NonFiniteMessage msg = (JsonProtocolFixtures.NonFiniteMessage)JsonMessage.fromJson(
      "{\"message\":{\"data\":{\"msgID\":\"n\",\"nan\":NaN,\"infinity\":Infinity,\"negativeInfinity\":-Infinity},"
      + "\"clazz\":\"" + JsonProtocolFixtures.NonFiniteMessage.class.getName() + "\"}}").message;
    assertTrue(Double.isNaN(msg.nan));
    assertEquals(Double.POSITIVE_INFINITY, msg.infinity, 0);
    assertEquals(Float.NEGATIVE_INFINITY, msg.negativeInfinity, 0);
  }

  @Test
  public void unknownClassWithDataFirstFallsBackToMessage() {
    Message msg = JsonMessage.fromJson("{\"message\":{\"data\":{\"msgID\":\"u\"},\"clazz\":\"missing.Type\"}}").message;
    assertEquals(Message.class, msg.getClass());
    assertEquals("u", msg.getMessageID());
  }

  @Test
  public void genericMessageReadsSubclassesHeadersAndNulls() {
    GenericMessage msg = generic(JsonProtocolFixtures.GenericSubclass.class.getName(),
      "{\"msgID\":\"g\",\"perf\":null,\"recipient\":\"agent\",\"sentAt\":1234,\"x\":null,\"y\":1}");
    assertEquals(JsonProtocolFixtures.GenericSubclass.class, msg.getClass());
    assertEquals("g", msg.getMessageID());
    assertEquals(Performative.INFORM, msg.getPerformative());
    assertEquals("agent", msg.getRecipient().getName());
    assertEquals(Long.valueOf(1234), msg.getSentAt());
    assertEquals(new HashSet<>(Arrays.asList("x", "y")), msg.keySet());
    assertNull(msg.get("x"));
    assertEquals(1L, msg.get("y"));
  }

  @Test
  public void genericMessageWithoutNoArgConstructorFails() {
    JsonSyntaxException ex = assertThrows(JsonSyntaxException.class,
      () -> generic(NoDefaultConstructor.class.getName(), "{\"msgID\":\"g\"}"));
    assertTrue(ex.getMessage(), ex.getMessage().contains(NoDefaultConstructor.class.getName()));
  }

  @Test
  public void genericMessageSentAtRoundTrips() {
    GenericMessage msg = new GenericMessage();
    msg.setSentAt(1234);
    JsonMessage env = new JsonMessage();
    env.message = msg;
    String json = env.toJson();
    assertTrue(json, json.contains("\"sender\":null,\"sentAt\":1234}"));
    GenericMessage back = (GenericMessage)JsonMessage.fromJson(json).message;
    assertEquals(Long.valueOf(1234), back.getSentAt());
    assertTrue(back.isEmpty());
  }

  @Test
  public void genericMessageReservedKeysFailTheEncode() {
    for (String key : Arrays.asList("msgID", "inReplyTo", "perf", "recipient", "sender", "sentAt")) {
      GenericMessage msg = new GenericMessage();
      msg.putAll(Collections.singletonMap(key, 1));
      JsonMessage env = new JsonMessage();
      env.message = msg;
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, env::toJson);
      assertTrue(ex.getMessage(), ex.getMessage().contains("'" + key + "'"));
    }
  }

  @Test
  public void genericMessageNullValuesRoundTrip() {
    GenericMessage msg = new GenericMessage();
    msg.put("x", null);
    msg.put("y", 1);
    JsonMessage env = new JsonMessage();
    env.message = msg;
    String json = env.toJson();
    assertTrue(json, json.contains("\"x\":null"));
    GenericMessage back = (GenericMessage)JsonMessage.fromJson(json).message;
    assertTrue(back.containsKey("x"));
    assertNull(back.get("x"));
    assertEquals(1L, back.get("y"));
  }

}
