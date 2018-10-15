/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;
import java.net.*;
import java.util.logging.Logger;

/**
 * TCP server. For each incoming connection, this invokes a listener callback with a
 * TcpConnector object for that connection.
 */
public class TcpServer extends Thread {

  protected int port;
  protected ServerSocket sock = null;
  protected ConnectionListener listener;
  protected Logger log = Logger.getLogger(getClass().getName());

  /**
   * Create a TCP server running on a specified port.
   *
   * @param port TCP port number (0 to autoselect).
   */
  public TcpServer(int port, ConnectionListener listener) {
    this.port = port;
    this.listener = listener;
    setName("tcpserver:[listening on port "+port+"]");
    setDaemon(true);
    start();
  }

  /**
   * Get the TCP port on which the server listens for connections.
   */
  public int getPort() {
    return port;
  }

  /**
   * Shutdown the TCP server.
   */
  public void close() {
    if (sock == null) return;
    try {
      sock.close();
    } catch (IOException ex) {
      // do nothing
    }
    sock = null;
  }

  @Override
  public void run() {
    try {
      sock = new ServerSocket(port);
      port = sock.getLocalPort();
      setName("tcpserver:[listening on port "+port+"]");
      log.info("Listening on port "+port);
      while (sock != null) {
        try {
          listener.connected(new TcpConnector(sock.accept()));
        } catch (IOException ex) {
          log.warning("accept failed: "+ex.toString());
        }
      }
    } catch (IOException ex) {
      log.info("socket closed: "+ex.toString());
    }
    log.info("Stopped listening");
  }

  @Override
  public String toString() {
    return getName();
  }

}
