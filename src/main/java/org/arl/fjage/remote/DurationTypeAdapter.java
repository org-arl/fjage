package org.arl.fjage.remote;

import java.time.Duration;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.JsonToken;

public class DurationTypeAdapter extends TypeAdapter<Duration> {
  @Override
  public void write(JsonWriter out, Duration value) throws java.io.IOException {
    if (value == null) {
      out.nullValue();
      return;
    }
    out.value(value.toMillis());
  }
  @Override
  public Duration read(JsonReader in) throws java.io.IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    return Duration.ofMillis(in.nextLong());
  }
}