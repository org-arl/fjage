package org.arl.fjage.connectors;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import org.junit.Test;

public class WebSocketOutputTest {
  @Test(timeout = 5000)
  public void decoderPreservesCharactersAndCountsMalformedInput() throws Exception {
    var messages = new LinkedBlockingQueue<String>();
    var output = new WebSocketOutput("test", false, messages::add, () -> {}, Logger.getLogger(getClass().getName()));
    output.start();
    try {
      String text = "a".repeat(4095)+"\ud83d\ude42"+"b".repeat(5000);
      output.write(text.getBytes(StandardCharsets.UTF_8));
      output.write(0xff);
      assertTrue(output.awaitCompletion(2000));
      assertEquals(text+"\ufffd", String.join("", messages));
      output.write(0xe4);
      assertFalse(output.awaitCompletion(20));
      output.write(new byte[] {(byte)0xb8, (byte)0x96});
      assertTrue(output.awaitCompletion(2000));
      assertEquals(text+"\ufffd\u4e16", String.join("", messages));
    } finally { output.close(); }
  }
}
