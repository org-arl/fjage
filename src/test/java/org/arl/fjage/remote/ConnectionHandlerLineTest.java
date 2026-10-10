package org.arl.fjage.remote;

import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.Proxy;
import org.arl.fjage.connectors.Connector;
import org.junit.Test;

public class ConnectionHandlerLineTest {
  private ConnectionHandler handler() {
    Connector connector = (Connector)Proxy.newProxyInstance(Connector.class.getClassLoader(),
        new Class<?>[] {Connector.class}, (proxy, method, args) -> {
          if (method.getName().equals("toString")) return "line-test";
          throw new UnsupportedOperationException(method.getName());
        });
    return new ConnectionHandler(connector, null);
  }

  @Test(timeout = 2000)
  public void carriageReturnCompletesWithoutWaitingForAnotherByte() throws Exception {
    ConnectionHandler handler = handler();
    try (PipedWriter writer = new PipedWriter(); BufferedReader in = new BufferedReader(new PipedReader(writer))) {
      writer.write("first\r");
      writer.flush();
      assertEquals("first", handler.readLine(in));
      writer.write("\nsecond\rthird\n");
      writer.flush();
      assertEquals("second", handler.readLine(in));
      assertEquals("third", handler.readLine(in));
    }
  }

  @Test
  public void linesAreBoundedIncludingUnterminatedInput() throws Exception {
    String property = "org.arl.fjage.maxLineChars";
    String previous = System.getProperty(property);
    System.setProperty(property, "4");
    try {
      ConnectionHandler handler = handler();
      try (BufferedReader in = new BufferedReader(new StringReader("1234\n12345"))) {
        assertEquals("1234", handler.readLine(in));
        assertThrows(IOException.class, () -> handler.readLine(in));
      }
    } finally {
      if (previous == null) System.clearProperty(property);
      else System.setProperty(property, previous);
    }
  }
}
