/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.arl.fjage.GenericMessage;
import org.arl.fjage.Message;
import org.arl.fjage.param.NamedParameter;
import org.arl.fjage.param.Parameter;
import org.arl.fjage.param.ParameterRsp;

/** Manual benchmark, not run by JUnit. Run the same arguments in separate JVMs to compare codecs. */
public final class JsonCodecBenchmark {

  private static final int ROUNDS = 5;
  private static volatile Object consumed;

  public static class SmallMessage extends Message {
    private static final long serialVersionUID = 1L;
    public String text;
    public int count;
  }

  public static class ArrayMessage extends Message {
    private static final long serialVersionUID = 1L;
    public byte[] bytes;
    public int[] numbers;
  }

  public static class NumericListMessage extends Message {
    private static final long serialVersionUID = 1L;
    public List<Integer> numbers;
  }

  public static class ParametersMessage extends Message {
    private static final long serialVersionUID = 1L;
    public List<Parameter> parameters;
  }

  public enum Param implements Parameter { alpha, beta }

  public enum Display implements Parameter {
    alpha;
    @Override public String toString() { return "display.with.dots"; }
  }

  private interface Operation {
    Object run();
  }

  private static class Case {
    final String name;
    final int iterations;
    final Operation operation;

    Case(String name, int iterations, Operation operation) {
      this.name = name;
      this.iterations = iterations;
      this.operation = operation;
    }
  }

  private JsonCodecBenchmark() { }

  private static <T extends Message> T headers(T message) {
    message.setMessageID("benchmark-message");
    return message;
  }

  private static JsonMessage envelope(Message message) {
    JsonMessage envelope = new JsonMessage();
    envelope.message = message;
    return envelope;
  }

  private static String input(Class<? extends Message> type, String data) {
    return "{\"message\":{\"clazz\":\"" + type.getName() + "\",\"data\":" + data + "}}";
  }

  private static String reordered(Class<? extends Message> type, String data) {
    return "{\"message\":{\"data\":" + data + ",\"clazz\":\"" + type.getName() + "\"}}";
  }

  private static void decode(List<Case> cases, String name, int iterations, String json) {
    cases.add(new Case(name + "-decode", iterations, () -> JsonMessage.fromJson(json)));
  }

  private static void encode(List<Case> cases, String name, int iterations, Message message) {
    JsonMessage envelope = envelope(message);
    cases.add(new Case(name + "-encode", iterations, envelope::toJson));
  }

  private static String parameterList(String identifier, int count) {
    StringBuilder json = new StringBuilder("{\"msgID\":\"benchmark-message\",\"parameters\":[");
    for (int i = 0; i < count; i++) {
      if (i > 0) json.append(',');
      json.append('"').append(identifier).append('"');
    }
    return json.append("]}").toString();
  }

  private static List<Case> corpus() {
    List<Case> cases = new ArrayList<>();
    SmallMessage small = headers(new SmallMessage());
    small.text = "hello";
    small.count = 7;
    String smallData = "{\"msgID\":\"benchmark-message\",\"text\":\"hello\",\"count\":7}";
    String smallInput = input(SmallMessage.class, smallData);
    SmallMessage checked = (SmallMessage)JsonMessage.fromJson(smallInput).message;
    if (checked.count != 7 || !"hello".equals(checked.text)) throw new AssertionError("Small-message corpus");
    encode(cases, "small", 10000, small);
    decode(cases, "small", 10000, smallInput);
    decode(cases, "small-reordered", 10000, reordered(SmallMessage.class, smallData));

    GenericMessage generic = headers(new GenericMessage());
    generic.put("text", "hello");
    generic.put("count", 7);
    generic.put("active", true);
    encode(cases, "generic-scalar", 5000, generic);
    decode(cases, "generic-scalar", 5000, input(GenericMessage.class,
      "{\"msgID\":\"benchmark-message\",\"text\":\"hello\",\"count\":7,\"active\":true}"));

    ParameterRsp response = headers(new ParameterRsp(small));
    response.set(new NamedParameter("gain"), 7, true);
    response.set(new NamedParameter("label"), "hello", false);
    encode(cases, "parameter-response", 3000, response);
    decode(cases, "parameter-response", 3000, input(ParameterRsp.class,
      "{\"msgID\":\"benchmark-message\",\"index\":-1,\"param\":\"gain\",\"value\":7,"
      + "\"values\":{\"label\":\"hello\"},\"readonly\":[\"gain\"]}"));

    ArrayMessage binary = headers(new ArrayMessage());
    binary.bytes = new byte[65536];
    for (int i = 0; i < binary.bytes.length; i++) binary.bytes[i] = (byte)(i * 31);
    String binaryData = "{\"msgID\":\"benchmark-message\",\"bytes\":{\"clazz\":\"[B\",\"data\":\""
      + Base64.getEncoder().encodeToString(binary.bytes) + "\"}}";
    String binaryInput = input(ArrayMessage.class, binaryData);
    if (((ArrayMessage)JsonMessage.fromJson(binaryInput).message).bytes.length != 65536)
      throw new AssertionError("Binary corpus");
    encode(cases, "bytes64KiB", 100, binary);
    decode(cases, "bytes64KiB", 100, binaryInput);
    decode(cases, "bytes64KiB-reordered", 100, reordered(ArrayMessage.class, binaryData));

    NumericListMessage numeric = headers(new NumericListMessage());
    numeric.numbers = new ArrayList<>();
    StringBuilder numbers = new StringBuilder("{\"msgID\":\"benchmark-message\",\"numbers\":[");
    for (int i = 0; i < 4096; i++) {
      numeric.numbers.add(i);
      if (i > 0) numbers.append(',');
      numbers.append(i);
    }
    String numberData = numbers.append("]}").toString();
    String numberInput = input(ArrayMessage.class, numberData);
    if (((ArrayMessage)JsonMessage.fromJson(numberInput).message).numbers.length != 4096)
      throw new AssertionError("Numeric-array corpus");
    encode(cases, "json-numeric4096", 100, numeric);
    decode(cases, "json-numeric4096", 100, numberInput);
    decode(cases, "json-numeric4096-reordered", 100, reordered(ArrayMessage.class, numberData));

    GenericMessage nested = headers(new GenericMessage());
    Map<String, Object> tree = new LinkedHashMap<>();
    tree.put("values", Arrays.asList(new int[] {1, 2}, "text", Arrays.asList(1, 2, 3)));
    tree.put("meta", Collections.singletonMap("active", true));
    nested.put("tree", tree);
    encode(cases, "nested-generic", 2000, nested);
    decode(cases, "nested-generic", 2000, input(GenericMessage.class,
      "{\"msgID\":\"benchmark-message\",\"tree\":{\"values\":[{\"clazz\":\"[I\","
      + "\"data\":\"AQAAAAIAAAA=\"},\"text\",[1,2,3]],\"meta\":{\"active\":true}}}"));

    char[] leaf = new char[4096];
    Arrays.fill(leaf, 'a');
    for (int depth : new int[] {1, 16, 64}) {
      String value = "\"" + new String(leaf) + "\"";
      for (int i = 0; i < depth; i++) value = "{\"data\":" + value + "}";
      decode(cases, "ordinary-data-depth" + depth, 300, input(GenericMessage.class,
        "{\"msgID\":\"benchmark-message\",\"tree\":" + value + "}"));
    }

    String paramName = Param.class.getName().replace('$', '.') + ".alpha";
    String displayName = Display.class.getName().replace('$', '.') + ".display.with.dots";
    for (int kind = 0; kind < 3; kind++) {
      String label = kind == 0 ? "known-enums64" : kind == 1 ? "unknown-qualified-parameter" : "dotted-display";
      String identifier = kind == 0 ? paramName : kind == 1 ? "missing.library.RemoteParam.setting" : displayName;
      int count = kind == 0 ? 64 : 1;
      ParametersMessage parameters = headers(new ParametersMessage());
      parameters.parameters = new ArrayList<>();
      for (int i = 0; i < count; i++) parameters.parameters.add(kind == 0 ? Param.alpha : kind == 1
        ? new NamedParameter(identifier) : Display.alpha);
      encode(cases, label, kind == 0 ? 1000 : 2000, parameters);
      decode(cases, label, kind == 0 ? 1000 : 300, input(ParametersMessage.class, parameterList(identifier, count)));
    }
    decode(cases, "unknown-message-class", 1000,
      "{\"message\":{\"clazz\":\"missing.application.UnknownMessage\",\"data\":{"
      + "\"msgID\":\"benchmark-message\",\"text\":\"discarded\"}}}");
    return cases;
  }

  private static ThreadMXBean allocationCounter() {
    java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    if (!(bean instanceof ThreadMXBean)) return null;
    ThreadMXBean counter = (ThreadMXBean)bean;
    if (!counter.isThreadAllocatedMemorySupported()) return null;
    if (!counter.isThreadAllocatedMemoryEnabled()) counter.setThreadAllocatedMemoryEnabled(true);
    return counter;
  }

  private static void measure(Case sample, double scale, ThreadMXBean counter) {
    int iterations = Math.max(1, (int)(sample.iterations * scale));
    int warmup = Math.max(1000, Math.min(10000, iterations));
    for (int i = 0; i < warmup; i++) consumed = sample.operation.run();
    double[] nanos = new double[ROUNDS];
    double[] bytes = new double[ROUNDS];
    long thread = Thread.currentThread().getId();
    for (int round = 0; round < ROUNDS; round++) {
      long allocated = counter == null ? 0 : counter.getThreadAllocatedBytes(thread);
      long start = System.nanoTime();
      for (int i = 0; i < iterations; i++) consumed = sample.operation.run();
      nanos[round] = (double)(System.nanoTime() - start) / iterations;
      bytes[round] = counter == null ? -1 : (double)(counter.getThreadAllocatedBytes(thread) - allocated) / iterations;
    }
    Arrays.sort(nanos);
    Arrays.sort(bytes);
    System.out.printf(Locale.ROOT, "%s,%.1f,%.1f,%d%n", sample.name, nanos[ROUNDS / 2], bytes[ROUNDS / 2], iterations);
  }

  private static void firstUse(String name, String input, ThreadMXBean counter) {
    long thread = Thread.currentThread().getId();
    long before = counter == null ? 0 : counter.getThreadAllocatedBytes(thread);
    long start = System.nanoTime();
    consumed = JsonMessage.fromJson(input);
    long nanos = System.nanoTime() - start;
    long bytes = counter == null ? -1 : counter.getThreadAllocatedBytes(thread) - before;
    System.out.printf(Locale.ROOT, "%s,%d,%d,1%n", name, nanos, bytes);
  }

  /** Arguments: --cold, --scale=N and --include=REGEX select measurements. */
  public static void main(String[] args) {
    boolean cold = false;
    double scale = 1;
    String include = ".*";
    for (String arg : args) {
      if (arg.equals("--cold")) cold = true;
      else if (arg.startsWith("--scale=")) scale = Double.parseDouble(arg.substring(8));
      else if (arg.startsWith("--include=")) include = arg.substring(10);
      else throw new IllegalArgumentException("Unknown argument " + arg);
    }
    if (!(scale > 0) || Double.isInfinite(scale)) throw new IllegalArgumentException("Scale must be finite and positive");
    ThreadMXBean counter = allocationCounter();
    if (counter == null) System.err.println("Thread allocation counter unavailable; bytesPerOp will be -1");
    if (cold) {
      System.out.println("label,nsPerOp,bytesPerOp,iterations");
      firstUse("first-message-including-initialization", input(SmallMessage.class,
        "{\"msgID\":\"cold\",\"text\":\"hello\",\"count\":7}"), counter);
      firstUse("first-enum-message", input(ParametersMessage.class,
        parameterList(Param.class.getName().replace('$', '.') + ".alpha", 1)), counter);
      return;
    }
    List<Case> cases = corpus();
    System.out.println("label,nsPerOp,bytesPerOp,iterations");
    for (Case sample : cases) if (sample.name.matches(include)) measure(sample, scale, counter);
  }
}
