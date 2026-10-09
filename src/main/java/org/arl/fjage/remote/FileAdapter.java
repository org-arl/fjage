package org.arl.fjage.remote;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.IOException;

/** Serializes paths without reflecting into JDK internals or resolving relative files. */
public class FileAdapter extends TypeAdapter<File> {

  @Override
  public void write(JsonWriter out, File value) throws IOException {
    if (value == null) out.nullValue();
    else out.beginObject().name("path").value(value.getPath()).endObject();
  }

  @Override
  public File read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    if (in.peek() == JsonToken.STRING) return new File(in.nextString());
    String path = null;
    in.beginObject();
    while (in.hasNext()) {
      if (in.nextName().equals("path")) path = in.nextString();
      else in.skipValue();
    }
    in.endObject();
    if (path == null) throw new JsonParseException("File path is missing");
    return new File(path);
  }
}
