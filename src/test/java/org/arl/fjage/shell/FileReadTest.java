package org.arl.fjage.shell;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import org.arl.fjage.*;
import org.arl.fjage.remote.Gateway;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class FileReadTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private final byte[] contents = new byte[100];
  private File file;
  private Platform platform;
  private Gateway gateway;
  private TestShell shell;

  private static class TestShell extends ShellAgent {
    TestShell() { super((ScriptEngine)null); }

    void shortCachedRead() throws IOException {
      InputStreamCacheEntry entry = isCache.values().iterator().next();
      entry.is.close();
      entry.is = new ByteArrayInputStream(new byte[2]);
    }

    void closeCachedStreams() throws IOException {
      for (InputStreamCacheEntry entry : isCache.values()) entry.is.close();
      isCache.clear();
    }
  }

  @Before
  public void setup() throws Exception {
    for (int i = 0; i < contents.length; i++) contents[i] = (byte)i;
    file = folder.newFile();
    Files.write(file.toPath(), contents);
    platform = new RealTimePlatform();
    Container container = new Container(platform);
    shell = new TestShell();
    container.add("shell", shell);
    gateway = new Gateway(container);
    platform.start();
  }

  @After
  public void shutdown() throws IOException {
    if (gateway != null) gateway.close();
    if (platform != null) platform.shutdown();
    if (shell != null) shell.closeCachedStreams();
  }

  private Message read(long offset, long length) {
    Message response = gateway.request(new GetFileReq(new AgentID("shell"), file.getPath(), offset, length), 2000);
    assertNotNull("Missing file response", response);
    return response;
  }

  private void assertSlice(long offset, long length, int start, int end) {
    Message response = read(offset, length);
    assertTrue(response instanceof GetFileRsp);
    GetFileRsp rsp = (GetFileRsp)response;
    assertEquals(offset, rsp.getOffset());
    assertArrayEquals(Arrays.copyOfRange(contents, start, end), rsp.getContents());
  }

  @Test
  public void negativeOffsetsAndCachedReadsUseAbsolutePositions() {
    assertSlice(-10, 4, 90, 94);
    assertSlice(94, 3, 94, 97);
    assertSlice(-3, 0, 97, 100);
    assertSlice(-10, 0, 90, 100);
    assertSlice(-100, 2, 0, 2);
    assertSlice(100, 0, 100, 100);
    assertSlice(98, Long.MAX_VALUE, 98, 100);
  }

  @Test
  public void outOfBoundsOffsetsAreRefused() {
    for (long offset : new long[] {-101, 101, Long.MIN_VALUE, Long.MAX_VALUE})
      assertEquals(Performative.REFUSE, read(offset, 1).getPerformative());
  }

  @Test
  public void cachedStreamFailureDoesNotPoisonTheNextRead() throws Exception {
    assertSlice(90, 5, 90, 95);
    shell.shortCachedRead();
    assertEquals(Performative.FAILURE, read(95, 5).getPerformative());
    assertTrue(shell.isCache.isEmpty());
    assertSlice(95, 5, 95, 100);
  }

  @Test
  public void exactReadsHandleShortSkipsAndZeroProgress() throws Exception {
    InputStream in = new ByteArrayInputStream(contents) {
      @Override public long skip(long count) { return 0; }
      @Override public synchronized int read(byte[] b, int ofs, int len) { return 0; }
    };
    ShellAgent.skipFully(in, 90);
    byte[] actual = new byte[10];
    ShellAgent.readFully(in, actual);
    assertArrayEquals(Arrays.copyOfRange(contents, 90, 100), actual);
    try {
      ShellAgent.readFully(in, new byte[1]);
      fail("Premature EOF should fail");
    } catch (EOFException expected) {}
    try {
      ShellAgent.skipFully(new ByteArrayInputStream(contents), 101);
      fail("Seeking past EOF should fail");
    } catch (EOFException expected) {}
  }
}
