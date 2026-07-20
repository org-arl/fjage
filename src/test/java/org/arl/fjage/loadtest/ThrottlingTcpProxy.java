package org.arl.fjage.loadtest;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Localhost TCP proxy with fault injection, placed between a SlaveContainer and the
 * MasterContainer. Supports byte-rate throttling, pausing reads (TCP backpressure),
 * abrupt connection drops (RST, no SIGN_OFF), and refusing new connections.
 */
public class ThrottlingTcpProxy extends Thread {

  private final ServerSocket server;
  private final String dstHost;
  private final int dstPort;

  private volatile int bytesPerSec = 0;      // 0 = unlimited
  private volatile boolean paused = false;   // stop reading -> backpressure
  private volatile boolean refuse = false;   // accept then immediately close
  private volatile boolean shutdown = false;
  private final List<Socket> live = Collections.synchronizedList(new ArrayList<Socket>());

  public ThrottlingTcpProxy(String dstHost, int dstPort) throws IOException {
    this.dstHost = dstHost;
    this.dstPort = dstPort;
    server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
    setName("proxy:" + server.getLocalPort() + "->" + dstPort);
    setDaemon(true);
  }

  public int getPort() {
    return server.getLocalPort();
  }

  public void setRate(int bps) {
    bytesPerSec = bps;
  }

  public void pause() {
    paused = true;
  }

  public void unpause() {
    paused = false;
  }

  public void setRefuse(boolean b) {
    refuse = b;
  }

  /** Abruptly kills all live connections with RST (no clean close, no SIGN_OFF). */
  public void dropConnections() {
    synchronized (live) {
      for (Socket s : new ArrayList<Socket>(live)) {
        try {
          s.setSoLinger(true, 0);   // force RST on close
        } catch (SocketException ex) {
          // socket may already be dead
        }
        try {
          s.close();
        } catch (IOException ex) {
          // ignore
        }
      }
      live.clear();
    }
  }

  public void shutdownProxy() {
    shutdown = true;
    dropConnections();
    try {
      server.close();
    } catch (IOException ex) {
      // ignore
    }
  }

  @Override
  public void run() {
    while (!shutdown) {
      final Socket client;
      try {
        client = server.accept();
      } catch (IOException ex) {
        break;
      }
      if (refuse) {
        try {
          client.setSoLinger(true, 0);
          client.close();
        } catch (IOException ex) {
          // ignore
        }
        continue;
      }
      try {
        client.setTcpNoDelay(true);
        final Socket upstream = new Socket(dstHost, dstPort);
        upstream.setTcpNoDelay(true);
        live.add(client);
        live.add(upstream);
        pump(client, upstream, "c2m");
        pump(upstream, client, "m2c");
      } catch (IOException ex) {
        try {
          client.close();
        } catch (IOException e2) {
          // ignore
        }
      }
    }
  }

  private void pump(final Socket from, final Socket to, String tag) {
    Thread t = new Thread(getName() + ":" + tag) {
      @Override
      public void run() {
        byte[] buf = new byte[4096];
        try {
          InputStream in = from.getInputStream();
          OutputStream out = to.getOutputStream();
          while (true) {
            while (paused && !from.isClosed()) Thread.sleep(20);
            int n = in.read(buf);
            if (n < 0) break;
            out.write(buf, 0, n);
            out.flush();
            int bps = bytesPerSec;
            if (bps > 0) Thread.sleep(Math.max(1, n * 1000L / bps));
          }
        } catch (IOException ex) {
          // connection torn down
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        } finally {
          try {
            from.close();
          } catch (IOException ex) {
            // ignore
          }
          try {
            to.close();
          } catch (IOException ex) {
            // ignore
          }
          live.remove(from);
          live.remove(to);
        }
      }
    };
    t.setDaemon(true);
    t.start();
  }
}
