/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.arl.fjage.GenericMessage;
import org.arl.fjage.param.NamedParameter;
import org.arl.fjage.param.ParameterRsp;
import org.junit.Test;

/**
 * Tests JSON values carried by generic messages and parameter responses.
 */
public class GenericValueAdapterFactoryTest {

  private static Object readValue(String json) {
    GenericMessage msg = (GenericMessage)JsonMessage.fromJson(
      "{\"message\":{\"clazz\":\"org.arl.fjage.GenericMessage\",\"data\":{\"value\":" + json + "}}}").message;
    return msg.get("value");
  }

  private static void assertValueInContexts(Object expected, String json) {
    assertEquals(json, expected, readValue(json));
    assertEquals(json, Collections.singletonList(expected), readValue("[" + json + "]"));
    assertEquals(json, Collections.singletonMap("x", expected), readValue("{\"x\":" + json + "}"));
    assertEquals(json, Collections.singletonMap("x", Collections.singletonList(expected)),
      readValue("{\"x\":[" + json + "]}"));
  }

  private static Object roundTrip(Object value) {
    GenericMessage msg = new GenericMessage();
    msg.put("value", value);
    JsonMessage envelope = new JsonMessage();
    envelope.message = msg;
    return ((GenericMessage)JsonMessage.fromJson(envelope.toJson()).message).get("value");
  }

  private static Object readParameterValue(String json) {
    ParameterRsp rsp = (ParameterRsp)JsonMessage.fromJson(
      "{\"message\":{\"clazz\":\"org.arl.fjage.param.ParameterRsp\",\"data\":{\"param\":\"test\",\"value\":"
      + json + "}}}").message;
    return rsp.get(new NamedParameter("test"));
  }

  @Test
  public void booleanArrays() {
    assertValueInContexts(Arrays.asList(true, false), "[true,false]");
  }

  @Test
  public void nullArrayElements() {
    assertValueInContexts(Collections.singletonList(null), "[null]");
  }

  @Test
  public void objectArrayElements() {
    assertValueInContexts(Collections.singletonList(Collections.singletonMap("x", 1L)), "[{\"x\":1}]");
  }

  @Test
  public void nestedArrays() {
    assertValueInContexts(Collections.singletonList(Arrays.asList(1L, 2L)), "[[1,2]]");
  }

  @Test
  public void mixedArrays() {
    assertValueInContexts(Arrays.asList(1L, true, "three", null,
      Collections.singletonMap("x", 2L), Collections.singletonList(false)),
      "[1,true,\"three\",null,{\"x\":2},[false]]");
    assertValueInContexts(Collections.emptyList(), "[]");
  }

  @Test
  public void scientificNotation() {
    for (String json : new String[] {"1e3", "1E3", "1e+3", "1.0e3"})
      assertValueInContexts(1000.0, json);
    assertValueInContexts(0.001, "1e-3");
    assertValueInContexts(-0.001, "-1E-3");
  }

  @Test
  public void numericTypes() {
    for (long value : new long[] {0, 1000, Long.MIN_VALUE, Long.MAX_VALUE, 9007199254740993L})
      assertValueInContexts(value, Long.toString(value));
    assertValueInContexts(1.0, "1.0");
    assertValueInContexts(1.5, "1.5");
    assertValueInContexts(0x1.0p63, "9223372036854775808");
    assertValueInContexts("1e3", "\"1e3\"");
  }

  @Test
  public void listsRoundTrip() {
    List<?> booleans = Arrays.asList(true, false);
    assertEquals(booleans, roundTrip(booleans));
    assertEquals(booleans, roundTrip(new boolean[] {true, false}));
    List<?> nested = Arrays.asList(booleans, Collections.singletonMap("x", 1L), null);
    assertEquals(nested, roundTrip(nested));
  }

  @Test
  public void typedValues() {
    assertValueInContexts(new Date(1234), "{\"clazz\":\"java.util.Date\",\"data\":1234}");
    assertValueInContexts(new Date(1234), "{\"data\":1234,\"clazz\":\"java.util.Date\"}");
    assertValueInContexts(new Date(1234), "{\"x\":1,\"data\":1234,\"clazz\":\"java.util.Date\",\"y\":2}");
  }

  @Test
  public void typedArraysAtAnyDepth() {
    List<?> list = (List<?>)readValue("[{\"clazz\":\"[D\",\"data\":\"AAAAAAAA+D8=\"},{\"data\":[1.5,NaN],\"clazz\":\"[D\"}]");
    assertArrayEquals(new double[] {1.5}, (double[])list.get(0), 0);
    assertArrayEquals(new double[] {1.5, Double.NaN}, (double[])list.get(1), 0);
  }

  @Test
  public void ordinaryClazzKeysStayInTheMap() {
    assertValueInContexts(map("clazz", "missing.Type", "x", 1L), "{\"clazz\":\"missing.Type\",\"x\":1}");
    assertValueInContexts(map("clazz", "missing.Type", "data", 1L), "{\"clazz\":\"missing.Type\",\"data\":1}");
    assertValueInContexts(map("data", 1L, "clazz", "missing.Type"), "{\"data\":1,\"clazz\":\"missing.Type\"}");
    assertValueInContexts(map("clazz", 1L), "{\"clazz\":1}");
    assertValueInContexts(map("clazz", map("a", true)), "{\"clazz\":{\"a\":true}}");
    assertValueInContexts(map("clazz", "java.util.Date", "x", 1L), "{\"clazz\":\"java.util.Date\",\"x\":1}");
  }

  @Test
  public void ordinaryDataKeysKeepTheirValues() {
    assertValueInContexts(map("data", map("data", Arrays.asList(1L, "NaN", null))), "{\"data\":{\"data\":[1,NaN,null]}}");
  }

  @Test
  public void mapsKeepKeyOrder() {
    Map<?,?> m = (Map<?,?>)readValue("{\"z\":1,\"data\":{\"y\":1,\"b\":2},\"clazz\":\"missing.Type\",\"a\":{\"q\":1,\"c\":2}}");
    assertEquals(Arrays.asList("z", "data", "clazz", "a"), new ArrayList<>(m.keySet()));
    assertEquals(Arrays.asList("y", "b"), new ArrayList<>(((Map<?,?>)m.get("data")).keySet()));
    assertEquals(Arrays.asList("q", "c"), new ArrayList<>(((Map<?,?>)m.get("a")).keySet()));
  }

  private static Map<String,Object> map(Object... kv) {
    Map<String,Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String)kv[i], kv[i+1]);
    return m;
  }

  @Test
  public void parameterArrayValues() {
    assertEquals(Arrays.asList(true, null, Collections.singletonMap("x", 1L)),
      readParameterValue("[true,null,{\"x\":1}]"));
  }

  @Test
  public void parameterNumericValues() {
    assertEquals(-0.001, readParameterValue("-1E-3"));
    assertEquals(Collections.singletonList(1000.0), readParameterValue("[1e3]"));
  }

}
