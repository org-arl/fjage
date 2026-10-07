package org.arl.fjage.remote;

import static org.junit.Assert.*;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.arl.fjage.*;
import org.junit.Test;

public class JsonPlatformValueTest {

  public static class Values extends Message {
    private static final long serialVersionUID = 1L;
    public Map<String, GenericValue> values = new LinkedHashMap<>();
  }

  private static Gson gson() throws Exception {
    Field field = JsonMessage.class.getDeclaredField("gson");
    field.setAccessible(true);
    return (Gson)field.get(null);
  }

  @Test
  public void supportedPlatformValuesKeepTheirWireFormatWithReflectionBlocked() throws Exception {
    Values message = new Values();
    message.values.put("date", new GenericValue(new Date(1234)));
    message.values.put("instant", new GenericValue(Instant.parse("2020-01-02T03:04:05Z")));
    message.values.put("duration", new GenericValue(Duration.ofMillis(1234)));
    message.values.put("uuid", new GenericValue(UUID.fromString("00000000-0000-0000-0000-000000000001")));
    message.values.put("uri", new GenericValue(URI.create("https://example.com/test")));
    message.values.put("list", new GenericValue(new ArrayList<>(Arrays.asList(1L, "hello"))));
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("nested", Arrays.asList(1L, 2L));
    map.put("null", null);
    message.values.put("map", new GenericValue(map));
    message.values.put("array", new GenericValue(new int[] {1, 2}));
    message.values.put("null", new GenericValue(null));
    JsonMessage envelope = new JsonMessage();
    envelope.message = message;
    String json = envelope.toJson();
    assertTrue(json.contains("\"clazz\":\"java.util.Date\",\"data\":1234"));
    assertTrue(json.contains("\"clazz\":\"java.time.Instant\",\"data\":\"2020-01-02T03:04:05Z\""));
    assertTrue(json.contains("\"clazz\":\"java.time.Duration\",\"data\":1234"));
    Gson restricted = gson().newBuilder().addReflectionAccessFilter(ReflectionAccessFilter.BLOCK_ALL_PLATFORM).create();
    assertEquals(json, restricted.toJson(envelope));
    Values decoded = (Values)restricted.fromJson(json, JsonMessage.class).message;
    for (String key : new String[] {"date", "instant", "duration", "uuid", "uri", "list", "map"})
      assertEquals(key, message.values.get(key), decoded.values.get(key));
    assertArrayEquals(new int[] {1, 2}, (int[])decoded.values.get("array").getValue());
    assertNull(decoded.values.get("null"));
  }

  @Test
  public void platformFilteringWouldBreakExistingFileValues() throws Exception {
    Gson original = gson();
    GenericValue value = new GenericValue(new File("samples/test.groovy"));
    String json = original.toJson(value);
    assertEquals(value, original.fromJson(json, GenericValue.class));
    Gson restricted = original.newBuilder().addReflectionAccessFilter(ReflectionAccessFilter.BLOCK_ALL_PLATFORM).create();
    try {
      restricted.toJson(value);
      fail("File needs an explicit adapter before platform reflection can be blocked");
    } catch (JsonIOException expected) {}
  }

  public static class CustomValue {
    public String text;
    CustomValue(String text) { this.text = text; }
  }

  @Test
  public void applicationAdaptersStillWorkInsideGenericValues() {
    JsonMessage.addTypeAdapterFactory(new TypeAdapterFactory() {
      @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != CustomValue.class) return null;
        TypeAdapter<CustomValue> adapter = new TypeAdapter<CustomValue>() {
          @Override public void write(JsonWriter out, CustomValue value) throws IOException { out.value(value.text); }
          @Override public CustomValue read(JsonReader in) throws IOException { return new CustomValue(in.nextString()); }
        };
        @SuppressWarnings("unchecked") TypeAdapter<T> typed = (TypeAdapter<T>)adapter;
        return typed;
      }
    });
    Values message = new Values();
    message.values.put("custom", new GenericValue(new CustomValue("hello")));
    JsonMessage envelope = new JsonMessage();
    envelope.message = message;
    Values decoded = (Values)JsonMessage.fromJson(envelope.toJson()).message;
    assertEquals("hello", ((CustomValue)decoded.values.get("custom").getValue()).text);
  }
}
