package org.arl.fjage.persistence;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import groovy.lang.GroovyClassLoader;

public class StoreFilterTest {

  @Test
  public void java8ResourceFiltersRejectOversizedGraphsAndAllowApplicationClasses() throws Exception {
    for (String filter : new String[] {"maxarray=64", "maxdepth=4", "maxrefs=8"}) {
      String java = new File(System.getProperty("java.home"), "bin/java").getPath();
      Process process = new ProcessBuilder(java, "-Djdk.serialFilter=" + filter, "-cp",
        System.getProperty("fjage.test.classpath"), getClass().getName(), filter).inheritIO().start();
      try {
        assertTrue("Filter subprocess timed out", process.waitFor(10, TimeUnit.SECONDS));
        assertEquals("Filter subprocess failed: " + filter, 0, process.exitValue());
      } finally {
        process.destroyForcibly();
      }
    }
  }

  public static class Node implements Serializable {
    private static final long serialVersionUID = 1L;
    Node next;
  }

  public static void main(String[] args) throws Exception {
    File file = File.createTempFile("fjage-filter-", ".store");
    StoreTest.TestStore store = new StoreTest.TestStore(file.getParentFile());
    try {
      Serializable rejected;
      if (args[0].startsWith("maxarray")) rejected = new byte[65];
      else if (args[0].startsWith("maxdepth")) {
        Node node = new Node();
        for (int i = 0; i < 10; i++) {
          Node parent = new Node();
          parent.next = node;
          node = parent;
        }
        rejected = node;
      } else {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 20; i++) nodes.add(new Node());
        rejected = (Serializable)nodes;
      }
      write(file, rejected);
      assertNull("Configured limit was not enforced", store.load(Object.class, file));
      write(file, new Node());
      assertNotNull("Application class should remain usable", store.load(Node.class, file));
      try (GroovyClassLoader loader = new GroovyClassLoader()) {
        Class<?> type = loader.parseClass("class StoredRecord implements java.io.Serializable { String value }");
        Object record = type.getDeclaredConstructor().newInstance();
        type.getMethod("setValue", String.class).invoke(record, "hello");
        write(file, (Serializable)record);
        store.clazzLoader = loader;
        Object loaded = store.load(type, file);
        assertNotNull("Groovy class should remain usable", loaded);
        assertEquals("hello", type.getMethod("getValue").invoke(loaded));
      }
    } finally {
      file.delete();
    }
  }

  private static void write(File file, Serializable value) throws IOException {
    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
      out.writeObject(value);
    }
  }
}
