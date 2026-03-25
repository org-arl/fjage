package org.arl.fjage.remote;

import java.time.Instant;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.JsonToken;

public class InstantTypeAdapter extends TypeAdapter<Instant> {
  @Override
  public void write(JsonWriter out, Instant value) throws java.io.IOException {
    if (value == null) {
      out.nullValue();
      return;
    }
    out.value(value.toString());
  }
  @Override
  public Instant read(JsonReader in) throws java.io.IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    return Instant.parse(in.nextString());
  }
}