package org.arl.fjage.persistence;

import static org.junit.Assert.*;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.arl.fjage.FjageException;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class StoreTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private TestStore store;

  static class TestStore extends Store {
    TestStore(File root) {
      super("test");
      this.root = root;
    }
  }

  @Before
  public void setup() throws IOException {
    store = new TestStore(folder.newFolder());
  }

  static String bufferedId(Serializable value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(value);
      out.flush();
    }
    return new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())).toString(16);
  }

  @Test
  public void streamedHashesMatchExistingIdsIncludingLeadingZeroes() throws Exception {
    for (Serializable value : new Serializable[] {"hello", new byte[1000000], new ArrayList<>(Arrays.asList(1, 2, 3))})
      assertEquals(bufferedId(value), store.getId(value));
    for (int i = 0; i < 4096; i++) {
      String value = "leading-zero-" + i;
      String id = bufferedId(value);
      if (id.length() < 64) {
        assertEquals(id, store.getId(value));
        return;
      }
    }
    fail("No leading-zero fixture found");
  }

  public static class Named implements Serializable {
    private static final long serialVersionUID = 1L;
    public String getId() { return "custom-id"; }
  }

  private static class Broken implements Serializable {
    private static final long serialVersionUID = 1L;
    private void writeObject(ObjectOutputStream out) throws IOException { throw new IOException("broken"); }
    @Override public int hashCode() { return 42; }
  }

  @Test
  public void customIdAndSerializationFailureKeepTheirPrecedence() {
    assertEquals("custom-id", store.getId(new Named()));
    assertEquals("42", store.getId(new Broken()));
  }

  @Test
  public void concurrentHashesHaveIndependentDigests() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        String value = "value-" + i;
        results.add(executor.submit(() -> store.getId(value)));
      }
      for (int i = 0; i < results.size(); i++)
        assertEquals(bufferedId("value-" + i), results.get(i).get(5, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void loadChecksRootTypeAndRetainsIoFailureBehavior() throws Exception {
    File file = folder.newFile();
    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
      out.writeObject("stored");
    }
    assertEquals("stored", store.load(String.class, file));
    try {
      store.load(Integer.class, file);
      fail("Wrong root class should fail inside Store.load");
    } catch (FjageException expected) {
      assertTrue(expected.getMessage().contains("ClassCastException"));
    }
    try (FileOutputStream out = new FileOutputStream(file)) { out.write(1); }
    assertNull(store.load(String.class, file));
  }

  @Test
  public void customClassLoaderStillResolvesStoredClasses() throws Exception {
    File file = folder.newFile();
    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
      out.writeObject(new Named());
    }
    List<String> resolved = new ArrayList<>();
    store.clazzLoader = new ClassLoader(getClass().getClassLoader()) {
      @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
        resolved.add(name);
        return super.loadClass(name);
      }
    };
    assertNotNull(store.load(Named.class, file));
    assertTrue(resolved.contains(Named.class.getName()));
  }
}
