package org.arl.fjage.shell;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.arl.fjage.connectors.PseudoInputStream;
import org.junit.Test;

public class ConsoleShellTest {
  @Test(timeout = 10000)
  public void streamTerminalReadsAndEditsUnicodeInput() throws Exception {
    PseudoInputStream input = new PseudoInputStream();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ConsoleShell shell = new ConsoleShell(input, output);
    try {
      shell.init(null);
      assertFalse(shell.isDumb());
      CompletableFuture<String> line = CompletableFuture.supplyAsync(() -> shell.readLine("> ", "... ", ""));
      // Backspace removes the final character before submitting the line.
      input.write("hello \u4e16\u754cX\u007f\n".getBytes(StandardCharsets.UTF_8));
      assertEquals("hello \u4e16\u754c", line.get(5, TimeUnit.SECONDS));
      shell.println("output");
      assertTrue(output.toString(StandardCharsets.UTF_8).contains("output"));
    } finally {
      input.close();
      shell.shutdown();
    }
  }
}
