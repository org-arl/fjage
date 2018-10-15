/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;
import java.net.*;

/**
 * TCP client connector.
 */
public class TcpConnector implements Connector {

  protected Socket sock;

  /**
   * Open a TCP client connection to a TCP server.
   */
  public TcpConnector(String hostname, int port) throws IOException {
    sock = new Socket(hostname, port);
  }

  /**
   * Create a TCP connector object with an already open socket.
   */
  public TcpConnector(Socket sock) {
    this.sock = sock;
  }

  @Override
  public String getName() {
    if (sock == null) return "tcp:[closed]";
    return "tcp:[from "+sock.getLocalAddress()+":"+sock.getLocalPort()+" to "+sock.getInetAddress()+":"+sock.getPort()+"]";
  }

  @Override
  public InputStream getInputStream() {
    if (sock == null) return null;
    try {
      return sock.getInputStream();
    } catch (IOException ex) {
      return null;
    }
  }

  @Override
  public OutputStream getOutputStream() {
    if (sock == null) return null;
    try {
      return sock.getOutputStream();
    } catch (IOException ex) {
      return null;
    }
  }

  @Override
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
  public boolean isReliable() {
    return true;
  }

  @Override
  public boolean waitOutputCompletion(long timeout) {
    try {
      sock.getOutputStream().flush();
      return true;
    } catch (IOException ex) {
      return false;
    }
  }

  @Override
  public String toString() {
    return getName();
  }

}
