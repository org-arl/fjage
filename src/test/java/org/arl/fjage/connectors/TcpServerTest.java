/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import static org.junit.Assert.assertEquals;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Test;

public class TcpServerTest {

  @Test
  public void busyPortIsLoggedOnce() throws Exception {
    Logger logger = Logger.getLogger(TcpServer.class.getName());
    List<LogRecord> warnings = new ArrayList<>();
    Handler handler = new Handler() {
      @Override public void publish(LogRecord r) { if (r.getLevel() == Level.WARNING) warnings.add(r); }
      @Override public void flush() { }
      @Override public void close() { }
    };
    logger.addHandler(handler);
    try (ServerSocket blocker = new ServerSocket(0)) {
      int port = blocker.getLocalPort();
      TcpServer server = new TcpServer(port, conn -> { });
      server.join(1000);
      assertEquals(1, warnings.size());
      assertEquals("Unable to listen on TCP port "+port, warnings.get(0).getMessage());
      server.close();
    } finally {
      logger.removeHandler(handler);
    }
  }

}
