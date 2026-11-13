/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.arl.fjage.GenericMessage;
import org.arl.fjage.Message;
import org.arl.fjage.param.ParameterRsp;
import org.arl.fjage.param.ParameterReq;
import org.junit.Test;

/** Checks the wire representations captured from master. */
public class JsonProtocolCompatibilityTest {

  static String fixture(String name) throws IOException {
    String path = "/org/arl/fjage/remote/json-protocol/" + name + ".json";
    try (InputStream stream = JsonProtocolCompatibilityTest.class.getResourceAsStream(path)) {
      assertNotNull("Missing fixture " + path, stream);
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[1024];
        int count;
        while ((count = reader.read(buffer)) != -1) text.append(buffer, 0, count);
        return text.toString().trim();
      }
    }
  }

  /** Expected output: an approved difference in corrected/ if present, else the baseline. */
  static String expected(String name) throws IOException {
    String path = "/org/arl/fjage/remote/json-protocol/corrected/" + name + ".json";
    return JsonProtocolCompatibilityTest.class.getResource(path) != null ? fixture("corrected/" + name) : fixture(name);
  }

  private static JsonObject data(String json) {
    return JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("message").getAsJsonObject("data");
  }

  private static JsonElement sortedObjects(JsonElement value) {
    if (value.isJsonObject()) {
      JsonObject result = new JsonObject();
      Map<String, JsonElement> members = new TreeMap<>();
      for (Map.Entry<String, JsonElement> member : value.getAsJsonObject().entrySet())
        members.put(member.getKey(), sortedObjects(member.getValue()));
      for (Map.Entry<String, JsonElement> member : members.entrySet())
        result.add(member.getKey(), member.getValue());
      return result;
    }
    if (value.isJsonArray()) {
      JsonArray result = new JsonArray();
      for (JsonElement element : value.getAsJsonArray()) result.add(sortedObjects(element));
      return result;
    }
    return value;
  }

  private static void assertWrapperOrder(JsonElement value) {
    if (value.isJsonObject()) {
      JsonObject object = value.getAsJsonObject();
      boolean classSeen = false;
      for (Map.Entry<String, JsonElement> member : object.entrySet()) {
        if (member.getKey().equals("clazz")) classSeen = true;
        if (object.has("clazz") && member.getKey().equals("data"))
          assertTrue("Wrapper must emit clazz before data: " + object, classSeen);
        assertWrapperOrder(member.getValue());
      }
    } else if (value.isJsonArray()) {
      for (JsonElement element : value.getAsJsonArray()) assertWrapperOrder(element);
    }
  }

  @Test
  public void encodingMatchesMasterFixtures() throws IOException {
    for (Map.Entry<String, JsonMessage> sample : JsonProtocolFixtures.samples().entrySet()) {
      String name = sample.getKey();
      String expected = expected(name);
      String actual = sample.getValue().toJson();
      // Sort object members without coercing numbers: JsonPrimitive.equals() would
      // consider different number spellings equal, whereas these strings retain them.
      assertEquals(sample.getKey(), sortedObjects(JsonParser.parseString(expected)).toString(),
        sortedObjects(JsonParser.parseString(actual)).toString());
      if (!sample.getKey().startsWith("generic-") && !sample.getKey().startsWith("parameter-"))
        assertEquals(sample.getKey(), expected, actual);
      if (sample.getValue().message != null)
        assertTrue(sample.getKey(), actual.contains("\"message\":{\"clazz\":"));
      assertWrapperOrder(JsonParser.parseString(actual));
    }
  }

  @Test
  public void stringsAndBinaryKeepTheirExactEscaping() throws IOException {
    for (String name : new String[] {"typed-message", "generic-message"}) {
      String json = JsonProtocolFixtures.samples().get(name).toJson();
      assertTrue(name, json.contains("\\u003c\\u003e\\u0026\\u003d\\u0027\\\"\\\\\\n caf\u00e9 \ud83d\ude80"));
      assertTrue(name, json.contains("9007199254740993"));
    }
    JsonProtocolFixtures.ArrayMessage arrays = (JsonProtocolFixtures.ArrayMessage)
      JsonProtocolFixtures.samples().get("primitive-arrays").message;
    JsonObject expected = data(fixture("primitive-arrays"));
    JsonObject actual = data(JsonProtocolFixtures.envelope(arrays).toJson());
    String encoded = JsonProtocolFixtures.envelope(arrays).toJson();
    for (String field : new String[] {"bytes", "shorts", "ints", "longs", "floats", "doubles"}) {
      assertEquals(field, expected.get(field), actual.get(field));
      assertTrue(field, encoded.contains("\"" + field + "\":" + expected.get(field)));
    }
  }

  @Test
  public void masterFixturesDecodeWithoutLosingTypedFields() throws IOException {
    JsonProtocolFixtures.TypedMessage typed = (JsonProtocolFixtures.TypedMessage)
      JsonMessage.fromJson(fixture("typed-message")).message;
    assertEquals("typed-id", typed.getMessageID());
    assertEquals("receiver", typed.getRecipient().getName());
    assertEquals("sender", typed.getSender().getName());
    assertEquals("request-id", typed.getInReplyTo());
    assertEquals(Long.valueOf(1700000000456L), typed.getSentAt());
    assertEquals(9007199254740993L, typed.integer);
    assertEquals(new Date(1700000000123L), typed.date);
    assertEquals(2345L, typed.duration.toMillis());
    assertTrue(typed.topic.isTopic());
    assertEquals(JsonProtocolFixtures.Param.alpha, typed.parameter);

    for (String name : new String[] {"primitive-arrays", "empty-arrays"}) {
      JsonProtocolFixtures.ArrayMessage expected = (JsonProtocolFixtures.ArrayMessage)
        JsonProtocolFixtures.samples().get(name).message;
      JsonProtocolFixtures.ArrayMessage actual = (JsonProtocolFixtures.ArrayMessage)
        JsonMessage.fromJson(fixture(name)).message;
      assertArrayEquals(expected.bytes, actual.bytes);
      assertArrayEquals(expected.shorts, actual.shorts);
      assertArrayEquals(expected.ints, actual.ints);
      assertArrayEquals(expected.longs, actual.longs);
      assertArrayEquals(expected.floats, actual.floats, 0f);
      assertArrayEquals(expected.doubles, actual.doubles, 0.0);
      assertArrayEquals(expected.booleans, actual.booleans);
      assertArrayEquals(expected.chars, actual.chars);
    }

    ParameterRsp response = (ParameterRsp)JsonMessage.fromJson(fixture("parameter-response")).message;
    assertEquals(new Date(1700000000123L), response.get(JsonProtocolFixtures.Param.alpha));
    assertTrue(response.isReadonly(JsonProtocolFixtures.Param.alpha));
    ParameterRsp peerResponse = (ParameterRsp)JsonMessage.fromJson(fixture("parameter-response-no-readonly")).message;
    assertEquals(42L, peerResponse.get(JsonProtocolFixtures.Param.alpha));
    assertFalse(peerResponse.isReadonly(JsonProtocolFixtures.Param.alpha));
    ParameterReq request = (ParameterReq)JsonMessage.fromJson(fixture("parameter-request")).message;
    assertEquals(2, request.getIndex());
    assertEquals(3, request.requests().size());
    assertEquals(JsonProtocolFixtures.Param.alpha, request.requests().get(0).param);
    assertEquals(1.25, request.requests().get(1).getValue());
    assertArrayEquals(new byte[] {-1, 0, 1}, (byte[])request.requests().get(2).getValue());
  }

  @Test
  public void bareGenericValuesRetainTheirExistingTypeLoss() throws IOException {
    GenericMessage message = (GenericMessage)JsonMessage.fromJson(fixture("generic-message")).message;
    assertEquals(1700000000123L, message.get("date"));
    assertEquals("#events", message.get("agent"));
    assertEquals(JsonProtocolFixtures.Param.class.getName().replace('$', '.') + ".alpha", message.get("parameter"));
    assertTrue(message.get("strings") instanceof List);
    assertEquals(9007199254740993L, message.get("integer"));
  }

  @Test
  public void unknownMessageClassesKeepTheLossyFallback() {
    Message message = JsonMessage.fromJson("{\"message\":{\"clazz\":\"missing.CustomMessage\","
      + "\"data\":{\"msgID\":\"unknown-id\",\"perf\":\"REQUEST\",\"custom\":42}}}").message;
    assertEquals(Message.class, message.getClass());
    assertEquals("unknown-id", message.getMessageID());
    JsonObject encoded = JsonParser.parseString(JsonProtocolFixtures.envelope(message).toJson())
      .getAsJsonObject().getAsJsonObject("message");
    assertEquals(Message.class.getName(), encoded.get("clazz").getAsString());
    assertFalse(encoded.getAsJsonObject("data").has("custom"));
  }

  @Test
  public void resolvableMetadataStillMeansTypedWrapper() {
    GenericMessage message = (GenericMessage)JsonMessage.fromJson("{\"message\":{"
      + "\"clazz\":\"org.arl.fjage.GenericMessage\",\"data\":{\"value\":{"
      + "\"clazz\":\"java.util.Date\",\"extra\":true,\"data\":1234}}}}").message;
    assertEquals(new Date(1234), message.get("value"));
  }

  @Test
  public void historicalMapAliasesStillReferToHeaders() {
    GenericMessage message = JsonProtocolFixtures.headers(new GenericMessage(), "alias-id");
    message.put("messageID", "ignored-id");
    assertEquals("alias-id", message.get("messageID"));
    message.put("performative", org.arl.fjage.Performative.REQUEST);
    assertEquals(org.arl.fjage.Performative.REQUEST, message.getPerformative());
  }

  @Test
  public void integralFieldsKeepStreamingReaderConversions() {
    String prefix = "{\"message\":{\"clazz\":\"" + JsonProtocolFixtures.TypedMessage.class.getName()
      + "\",\"data\":{\"msgID\":\"numbers-id\",\"integer\":";
    assertThrows(JsonSyntaxException.class, () -> JsonMessage.fromJson(prefix + "1.5}}}"));
    assertEquals(Long.MAX_VALUE, ((JsonProtocolFixtures.TypedMessage)
      JsonMessage.fromJson(prefix + "9223372036854775808}}}").message).integer);
    assertEquals(12L, ((JsonProtocolFixtures.TypedMessage)
      JsonMessage.fromJson(prefix + "\"12\"}}}").message).integer);
    assertEquals(2L, ((JsonProtocolFixtures.TypedMessage)
      JsonMessage.fromJson(prefix + "1,\"integer\":2}}}").message).integer);
  }

  @Test
  public void primitiveArraysKeepBareAndNumericFormsAndThresholds() throws IOException {
    Gson wrapped = new GsonBuilder().registerTypeAdapterFactory(new ArrayAdapterFactory()).create();
    Gson bare = new GsonBuilder().registerTypeAdapterFactory(new ArrayAdapterFactory(true, 0)).create();
    Gson threshold = new GsonBuilder().registerTypeAdapterFactory(new ArrayAdapterFactory(false, 2)).create();
    JsonProtocolFixtures.ArrayMessage arrays = (JsonProtocolFixtures.ArrayMessage)
      JsonProtocolFixtures.samples().get("primitive-arrays").message;
    Object[] values = {arrays.bytes, arrays.shorts, arrays.ints, arrays.longs, arrays.floats, arrays.doubles};
    Object[] singletons = {new byte[] {-1}, new short[] {Short.MIN_VALUE}, new int[] {Integer.MIN_VALUE},
      new long[] {Long.MIN_VALUE}, new float[] {-0.0f}, new double[] {-0.0}};
    String[] fields = {"bytes", "shorts", "ints", "longs", "floats", "doubles"};
    String[] numeric = {"[-1]", "[-32768]", "[-2147483648]", "[-9223372036854775808]", "[-0.0]", "[-0.0]"};
    JsonObject expected = data(fixture("primitive-arrays"));
    for (int i = 0; i < values.length; i++) {
      String encoded = expected.getAsJsonObject(fields[i]).get("data").toString();
      assertEquals(fields[i], encoded, bare.toJson(values[i]));
      Object decoded = wrapped.fromJson(encoded, values[i].getClass());
      assertEquals(fields[i], expected.get(fields[i]).toString(), wrapped.toJson(decoded));
      assertEquals(fields[i], numeric[i], threshold.toJson(singletons[i]));
      assertEquals(fields[i], wrapped.toJson(singletons[i]),
        wrapped.toJson(wrapped.fromJson(numeric[i], values[i].getClass())));
      assertEquals(fields[i], wrapped.toJson(values[i]), threshold.toJson(values[i]));
    }
    assertEquals("[1]", threshold.toJson(new int[] {1}));
    assertEquals("{\"clazz\":\"[I\",\"data\":\"AQAAAAIAAAA=\"}", threshold.toJson(new int[] {1, 2}));
  }

  @Test
  public void nonFiniteRepresentationsStayContextDependent() throws IOException {
    Map<String, JsonMessage> samples = JsonProtocolFixtures.samples();
    String typed = samples.get("nonfinite-message").toJson();
    String generic = samples.get("generic-nonfinite").toJson();
    String parameter = samples.get("parameter-nonfinite").toJson();
    assertTrue(typed, typed.contains("\"nan\":null"));
    assertTrue(typed, typed.contains("\"infinity\":Infinity"));
    assertTrue(generic, generic.contains("\"nan\":null"));
    assertTrue(generic, generic.contains("\"infinity\":Infinity"));
    assertTrue(parameter, parameter.contains("\"value\":NaN"));
    assertTrue(parameter, parameter.contains("\"infinity\":-Infinity"));
    assertEquals(Double.doubleToRawLongBits(-0.0),
      Double.doubleToRawLongBits(data(typed).get("negativeZero").getAsDouble()));
    GenericMessage decodedGeneric = (GenericMessage)JsonMessage.fromJson(fixture("generic-nonfinite")).message;
    assertNull(decodedGeneric.get("nan"));
    assertEquals("Infinity", decodedGeneric.get("infinity"));
    ParameterRsp decodedParameter = (ParameterRsp)JsonMessage.fromJson(fixture("parameter-nonfinite")).message;
    assertEquals("NaN", decodedParameter.get(new org.arl.fjage.param.NamedParameter("nan")));
    assertEquals("-Infinity", decodedParameter.get(new org.arl.fjage.param.NamedParameter("infinity")));
    for (String name : new String[] {"nonfinite-message", "generic-nonfinite", "parameter-nonfinite"}) {
      JsonElement expected = JsonParser.parseString(expected(name));
      assertEquals(name, expected, JsonParser.parseString(samples.get(name).toJson()));
    }
  }
}
