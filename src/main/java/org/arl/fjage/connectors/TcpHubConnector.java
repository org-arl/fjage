/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * TCP hub server. All incoming connections to the TCP server are aggregated
 * into a single connector. All data in/out of the input/output streams of this
 * connector are common across all TCP clients.
 */
public class TcpHubConnector extends Thread implements Connector {

  protected int port;
  protected boolean telnet;
  protected ServerSocket sock = null;
  protected OutputThread outThread = null;
  protected List<ClientThread> clientThreads = Collections.synchronizedList(new ArrayList<ClientThread>());
  protected Logger log = Logger.getLogger(getClass().getName());
  protected PseudoInputStream pin = new PseudoInputStream();
  protected PseudoOutputStream pout = new PseudoOutputStream();
  protected ConnectionListener listener = null;

  /**
   * Creates a TCP server running on a specified port.
   *
   * @param port TCP port number.
   * @param telnet true to negotiate character mode using telnet protocol,
   *               false to leave the choice to the client.
   */
  public TcpHubConnector(int port, boolean telnet) {
    this.port = port;
    this.telnet = telnet;
    try {
      setName("tcp://"+InetAddress.getLocalHost().getHostAddress()+":"+port);
    } catch (UnknownHostException ex) {
      setName("tcp://0.0.0.0:"+port);
    }
    setDaemon(true);
    start();
  }

  /**
   * Creates a TCP server running on a specified port.
   *
   * @param port TCP port number.
   */
  public TcpHubConnector(int port) {
    this(port, false);
  }

  /**
   * Get the TCP port on which the server listens for connections.
   */
  public synchronized int getPort() {
    if (port == 0) {
      try {
        wait(100);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return -1;
      }
    }
    return port;
  }

  /**
   * Shutdown the TCP server.
   */
  @Override
  public void close() {
    synchronized(clientThreads) {
      for (ClientThread t: clientThreads)
        t.close();
    }
    clientThreads.clear();
    if (sock == null) return;
    try {
      sock.close();
    } catch (IOException ex) {
      // do nothing
    }
    sock = null;
    pin.close();
    pout.close();
    pin = null;
    pout = null;
  }

  @Override
  public boolean isReliable() {
    return true;
  }

  @Override
  public boolean waitOutputCompletion(long timeout) {
    long t = System.currentTimeMillis() + timeout;
    while (pout.available() > 0) {
      if (System.currentTimeMillis() > t) return false;
      try {
        sleep(10);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return true;
  }

  @Override
  public void run() {
    outThread = new OutputThread();
    outThread.start();
    try {
      synchronized (this) {
        sock = new ServerSocket(port);
        port = sock.getLocalPort();
        notify();
      }
      try {
        setName("tcp://"+InetAddress.getLocalHost().getHostAddress()+":"+port);
      } catch (UnknownHostException ex) {
        setName("tcp://0.0.0.0:"+port);
      }
      log.info("Listening on port "+port);
      while (sock != null) {
        try {
          new ClientThread(this, sock.accept()).start();
        } catch (IOException ex) {
          // do nothing
        }
      }
    } catch (IOException ex) {
      // do nothing
    }
    log.info("Stopped listening");
    outThread.close();
    outThread = null;
  }

  @Override
  public InputStream getInputStream() {
    return pin;
  }

  @Override
  public OutputStream getOutputStream() {
    return pout;
  }

  @Override
  public void setConnectionListener(ConnectionListener listener) {
    this.listener = listener;
  }

  @Override
  public String toString() {
    return getName();
  }

  // thread to monitor incoming data on output stream and write to TCP clients

  private class OutputThread extends Thread {

    OutputThread() {
      setName(getClass().getSimpleName());
      setDaemon(true);
    }

    @Override
    public void run() {
      while (pout.available() >= 0) {
        int c = pout.read();
        if (c >= 0) {
          synchronized(clientThreads) {
            for (ClientThread t: clientThreads)
              t.write(c);
          }
        }
      }
    }

    void close() {
      if (pout != null) pout.close();
    }

  }

  // threads to monitor incoming data from TCP clients and write to input stream

  private class ClientThread extends Thread {

    Socket client;
    OutputStream out = null;
    TcpHubConnector conn;
    boolean negotiated = false;

    ClientThread(TcpHubConnector conn, Socket client) {
      setName(getClass().getSimpleName());
      setDaemon(true);
      this.conn = conn;
      this.client = client;
    }

    @Override
    public void run() {
      clientThreads.add(this);
      String cname = "(unknown)";
      InputStream in = null;
      try {
        cname = client.getInetAddress().toString();
        log.info("New connection from "+cname);
        in = client.getInputStream();
        out = client.getOutputStream();
        // initial negotiation
        if (telnet) {
          int[] negotiationBytes = new int[] { 255, 251, 1, 255, 251, 3, 255, 252, 34, 27, 91, 63, 49, 104, 27, 61 };
          for (int b: negotiationBytes)
            out.write(b);
          out.flush();
        }
        if (listener != null) listener.connected(conn);
        negotiated = true;
        boolean iac = false;
        int skip = 0;
        while (!Thread.interrupted()) {
          int c = in.read();
          if (skip > 0) skip--;
          else if (iac) {
            if (c >= 251) skip = 1;
            if (c != 255) iac = false;
          }
          else if (telnet && c == 255) iac = true;
          else if (c < 0 || (telnet && c == 4)) break;
          else if (c > 0) pin.write(c);
        }
      } catch (Exception ex) {
        // do nothing
      }
      log.info("Connection from "+cname+" closed");
      close(in);
      close(out);
      close(client);
      clientThreads.remove(this);
      client = null;
      out = null;
    }

    void write(int c) {
      if (!negotiated) return;
      try {
        if (out != null) {
          out.write(c);
          out.flush();
        }
      } catch (IOException ex) {
        // do nothing
      }
    }

    void close() {
      close(client);
    }

    void close(Closeable x) {
      try {
        if (x != null) x.close();
      } catch (IOException ex) {
        // do nothing
      }
    }

  }

}
