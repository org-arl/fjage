package org.arl.fjage.remote;

import static org.junit.Assert.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.File;
import org.junit.Test;

public class FileAdapterTest {
  private final Gson gson = new GsonBuilder().registerTypeAdapter(File.class, new FileAdapter()).create();

  @Test
  public void legacyObjectAndTransitionStringPreservePaths() {
    for (String path : new String[] {"relative/file", new File("absolute").getAbsolutePath(), "quote\"and\\slash"}) {
      File file = new File(path);
      String json = gson.toJson(file, File.class);
      assertTrue(json.startsWith("{\"path\":"));
      assertEquals(file, gson.fromJson(json, File.class));
      assertEquals(file, gson.fromJson(gson.toJson(path), File.class));
    }
    assertEquals(new File("relative"), gson.fromJson("{\"extra\":1,\"path\":\"relative\"}", File.class));
    assertNull(gson.fromJson("null", File.class));
    assertThrows(JsonParseException.class, () -> gson.fromJson("{}", File.class));
  }
}
