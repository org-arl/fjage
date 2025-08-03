package org.arl.fjage.remote;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.IOException;

public class FileAdapter extends TypeAdapter<File> {

  @Override
  public void write(JsonWriter out, File value) throws IOException {
    if (value == null) {
      out.nullValue();
    } else {
      out.value(value.getAbsolutePath());
    }
  }

  @Override
  public File read(JsonReader in) throws IOException {
    if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    String path = in.nextString();
    return new File(path);
  }
}