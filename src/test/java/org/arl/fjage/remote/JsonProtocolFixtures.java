/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.arl.fjage.AgentID;
import org.arl.fjage.GenericMessage;
import org.arl.fjage.Message;
import org.arl.fjage.Performative;
import org.arl.fjage.param.NamedParameter;
import org.arl.fjage.param.Parameter;
import org.arl.fjage.param.ParameterReq;
import org.arl.fjage.param.ParameterRsp;

/** Deterministic inputs for the master-generated JSON compatibility fixtures. */
final class JsonProtocolFixtures {

  private JsonProtocolFixtures() {
  }

  public enum Param implements Parameter {
    alpha, beta
  }

  public enum DisplayParam implements Parameter {
    alpha {
      @Override
      public String toString() {
        return "display.name";
      }
    }
  }

  public static class TypedMessage extends Message {
    private static final long serialVersionUID = 1L;
    public String text;
    public long integer = 9007199254740993L;
    public double fraction = -0.125;
    public AgentID topic = new AgentID("events", true);
    public Date date = new Date(1700000000123L);
    public Instant instant = Instant.parse("2026-10-19T01:02:03.123456789Z");
    public Duration duration = Duration.ofSeconds(2, 345678901);
    public Parameter parameter = Param.alpha;
    public String optional;
  }

  public static class ArrayMessage extends Message {
    private static final long serialVersionUID = 1L;
    public byte[] bytes;
    public short[] shorts;
    public int[] ints;
    public long[] longs;
    public float[] floats;
    public double[] doubles;
    public boolean[] booleans = {true, false};
    public char[] chars = {'A', '\u03bb'};
  }

  public static class NestedMessage extends Message {
    private static final long serialVersionUID = 1L;
    public Message child;
    public String after;
  }

  public static class Pojo {
    public String a;
    public int b = 1;
  }

  public static class NestedPojoMessage extends Message {
    private static final long serialVersionUID = 1L;
    public Message child;
    public Pojo pojo = new Pojo();
    public String after;
  }

  public static class NonFiniteMessage extends Message {
    private static final long serialVersionUID = 1L;
    public double nan = Double.NaN;
    public Double infinity = Double.POSITIVE_INFINITY;
    public float negativeInfinity = Float.NEGATIVE_INFINITY;
    public double negativeZero = -0.0;
  }

  public static class GenericSubclass extends GenericMessage {
    private static final long serialVersionUID = 1L;
  }

  static <T extends Message> T headers(T message, String id) {
    message.setMessageID(id);
    message.setPerformative(Performative.INFORM);
    message.setRecipient(new AgentID("receiver"));
    message.setSender(new AgentID("sender"));
    message.setInReplyTo("request-id");
    message.setSentAt(1700000000456L);
    return message;
  }

  static JsonMessage envelope(Message message) {
    JsonMessage envelope = new JsonMessage();
    envelope.id = "envelope-id";
    envelope.action = Action.SEND;
    envelope.message = message;
    envelope.relay = false;
    return envelope;
  }

  static Map<String, JsonMessage> samples() {
    Map<String, JsonMessage> samples = new LinkedHashMap<>();
    TypedMessage typed = headers(new TypedMessage(), "typed-id");
    typed.text = "<>&='\"\\\n caf\u00e9 \ud83d\ude80";
    samples.put("typed-message", envelope(typed));

    ArrayMessage arrays = headers(new ArrayMessage(), "arrays-id");
    arrays.bytes = new byte[] {-1, 0, 1};
    arrays.shorts = new short[] {Short.MIN_VALUE, 0, Short.MAX_VALUE};
    arrays.ints = new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE};
    arrays.longs = new long[] {Long.MIN_VALUE, 9007199254740993L, Long.MAX_VALUE};
    arrays.floats = new float[] {-0.0f, 1.5f, Float.NaN, Float.POSITIVE_INFINITY};
    arrays.doubles = new double[] {-0.0, Math.PI, Double.NaN, Double.NEGATIVE_INFINITY};
    samples.put("primitive-arrays", envelope(arrays));

    ArrayMessage empty = headers(new ArrayMessage(), "empty-arrays-id");
    empty.bytes = new byte[0];
    empty.shorts = new short[0];
    empty.ints = new int[0];
    empty.longs = new long[0];
    empty.floats = new float[0];
    empty.doubles = new double[0];
    samples.put("empty-arrays", envelope(empty));

    GenericMessage generic = headers(new GenericMessage(), "generic-id");
    generic.put("text", typed.text);
    generic.put("integer", 9007199254740993L);
    generic.put("date", typed.date);
    generic.put("agent", typed.topic);
    generic.put("parameter", Param.alpha);
    generic.put("strings", new String[] {"one", "two"});
    generic.put("mixed", Arrays.asList(1L, 1.5, true, "text", null,
      Collections.singletonMap("x", Arrays.asList(false, 2L))));
    generic.put("array", arrays.ints);
    samples.put("generic-message", envelope(generic));

    ParameterReq request = headers(new ParameterReq(), "parameter-request-id");
    request.setPerformative(Performative.REQUEST);
    request.setIndex(2);
    request.get(Param.alpha).set(new NamedParameter("gain"), 1.25)
      .set(new NamedParameter("bytes"), arrays.bytes);
    samples.put("parameter-request", envelope(request));

    ParameterRsp response = headers(new ParameterRsp(request), "parameter-response-id");
    response.set(Param.alpha, typed.date, true);
    response.set(new NamedParameter("agent"), typed.topic, false);
    response.set(new NamedParameter("strings"), new String[] {"one", "two"}, false);
    response.set(new NamedParameter("array"), arrays.doubles, false);
    response.set(new NamedParameter("empty"), null, false);
    samples.put("parameter-response", envelope(response));

    NestedMessage nested = headers(new NestedMessage(), "nested-id");
    nested.child = new Message();
    nested.child.setMessageID("child-id");
    samples.put("nested-message", envelope(nested));

    NestedPojoMessage nestedPojo = headers(new NestedPojoMessage(), "nested-pojo-id");
    nestedPojo.child = new Message();
    nestedPojo.child.setMessageID("child-id");
    samples.put("nested-pojo", envelope(nestedPojo));

    samples.put("nonfinite-message", envelope(headers(new NonFiniteMessage(), "nonfinite-id")));
    GenericMessage genericNumbers = headers(new GenericMessage(), "generic-nonfinite-id");
    genericNumbers.put("nan", Double.NaN);
    genericNumbers.put("infinity", Double.POSITIVE_INFINITY);
    genericNumbers.put("negativeZero", -0.0);
    samples.put("generic-nonfinite", envelope(genericNumbers));

    ParameterRsp parameterNumbers = headers(new ParameterRsp(request), "parameter-nonfinite-id");
    parameterNumbers.set(new NamedParameter("nan"), Double.NaN, false);
    parameterNumbers.set(new NamedParameter("infinity"), Double.NEGATIVE_INFINITY, false);
    samples.put("parameter-nonfinite", envelope(parameterNumbers));

    GenericMessage legacyEnum = headers(new GenericMessage(), "legacy-enum-id");
    legacyEnum.put("parameter", DisplayParam.alpha);
    samples.put("legacy-enum", envelope(legacyEnum));

    JsonMessage containsAgent = new JsonMessage();
    containsAgent.id = "envelope-id";
    containsAgent.action = Action.CONTAINS_AGENT;
    containsAgent.agentID = new AgentID("receiver");
    samples.put("contains-agent-request", containsAgent);

    JsonMessage agentForService = new JsonMessage();
    agentForService.id = "envelope-id";
    agentForService.inResponseTo = Action.AGENT_FOR_SERVICE;
    agentForService.agentID = new AgentID("receiver");
    samples.put("agent-for-service-response", agentForService);
    return samples;
  }
}
