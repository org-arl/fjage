package org.arl.fjage.shell;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.arl.fjage.connectors.PseudoInputStream;
import org.junit.Test;

public class ShellEncodingTest {

  private static final String TEXT = "fj\u00e5ge caf\u00e9 \u4e2d\u6587 \ud83d\ude42";

  @Test(timeout = 10000)
  public void consoleShellUsesUtf8ForStreamInputAndOutput() throws Exception {
    PseudoInputStream in = new PseudoInputStream();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertRoundTrip(new ConsoleShell(in, out), in, out);
  }

  @Test(timeout = 10000)
  public void dumbShellUsesUtf8ForStreamInputAndOutput() throws Exception {
    PseudoInputStream in = new PseudoInputStream();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertRoundTrip(new DumbShell(in, out), in, out);
  }

  private void assertRoundTrip(Shell shell, PseudoInputStream in, ByteArrayOutputStream out) throws Exception {
    try {
      shell.init(null);
      shell.println(TEXT);
      assertTrue(out.toString(StandardCharsets.UTF_8).contains(TEXT));
      in.write((TEXT + "\n").getBytes(StandardCharsets.UTF_8));
      assertEquals(TEXT, shell.readLine("", "", null));
    } finally {
      shell.shutdown();
    }
  }
}
