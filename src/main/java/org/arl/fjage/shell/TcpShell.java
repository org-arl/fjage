/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.net.*;
import java.awt.event.*;
import java.util.logging.Logger;
import jline.console.ConsoleReader;
import jline.console.completer.*;

/**
 * TCP socket command shell.
 *
 * @author Mandar Chitre
 */
public class TcpShell extends Thread implements Shell {

  ////////// Private attributes

  private ScriptEngine engine = null;
  private int port;
  private ServerSocket sock = null;
  private ClientThread clientThread = null;
  private Logger log = Logger.getLogger(getClass().getName());
  private ScriptOutputStream sos = new ScriptOutputStream();

  ////////// Methods

  /**
   * Creates a TCP command shell running on a specified port.
   *
   * @param port TCP port number.
   */
  public TcpShell(int port) {
    this.port = port;
  }
  
  @Override
  public void start(ScriptEngine engine) {
    this.engine = engine;
    setName(getClass().getSimpleName());
    setDaemon(true);
    start();
  }

  /**
   * Gets the current script output handler.
   * 
   * @return the current script output handler.
   */
  public ScriptOutputStream getOutputStream() {
    return sos;
  }

  /**
   * Thread implementation.
   *
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    if (engine == null) return;
    try {
      Socket client = null;
      sock = new ServerSocket(port);
      while (true) {
        try {
          log.info("Listening on port "+port);
          client = sock.accept();
          if (client != null) {
            if (clientThread != null) {
              clientThread.close();
              try {
                clientThread.join(1000);
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
              }
            }
            clientThread = new ClientThread(client);
            clientThread.start();
          }
        } catch (IOException ex) {
          // do nothing
        }
      }
    } catch (IOException ex) {
      log.severe(ex.toString());
    }
  }

  @Override
  public Term getTerm() {
    if (sos == null) return null;
    return sos.getTerm();
  }
  
  @Override
  public void println(String s) {
    if (sos != null) sos.println(s);
  }

  ////////// Private stuff

  private class ClientThread extends Thread {

    private Socket client;
    private ConsoleReader console = null;

    public ClientThread(Socket client) {
      setName(getClass().getSimpleName());
      this.client = client;
    }

    @Override
    public void run() {
      InputStream in = null;
      BufferedOutputStream out = null;
      try {
        log.info("New connection from " + client.getInetAddress().toString());
        in = client.getInputStream();
        out = new BufferedOutputStream(client.getOutputStream());
        int[] charmode = new int[] { 255, 251, 1, 255, 251, 3, 255, 252, 34 };
        for (int b: charmode)
          out.write(b);
        out.flush();
        try {
          sleep(100);
        } catch (InterruptedException ex) {
          // do nothing
        }
        while (in.available() > 0) in.read();  // flush input stream
        console = new ConsoleReader(in, out);
        console.setExpandEvents(false);
        sos.setOutputStream(out);
        sos.setTelnet(true);
        Term term = sos.getTerm();
        sos.setPrompt(term.prompt("$ "));
        StringBuffer sb = new StringBuffer();
        boolean nest = false;
        while (true) {
          int esc = 0;
          while (engine.isBusy()) {
            if (in.available() > 0) {
              int c = in.read();
              if (c == 3) engine.abort();
              else if (c == 27) esc += 10;
              if (esc > 20) engine.abort();
            } else if (esc > 0) esc--;
            try {
              sleep(100);
            } catch (InterruptedException ex) {
              interrupt();
            }
          }
          if (sb.length() > 0) console.setPrompt(term.prompt("- "));
          else console.setPrompt(term.prompt("$ "));
          sos.print("\r");
          while (in.available() > 0) in.read();
          String s1 = console.readLine();
          sos.print("\r");
          if (s1 == null) break;
          sb.append(s1);
          String s = sb.toString();
          nest = nested(s);
          if (nest) sb.append('\n');
          else if (s.length() > 0) {
            sb = new StringBuffer();
            boolean ok = engine.exec(s, sos);
            if (!ok) sos.println(term.error("BUSY"));
          }
        }

      } catch (Exception ex) {
        log.warning(ex.toString());
      }
      log.info("Connection closed");
      sos.setOutputStream(null);
      try {
        if (in != null) in.close();
      } catch (IOException ex) {
        // do nothing
      }
      try {
        if (out != null) out.close();
      } catch (IOException ex) {
        // do nothing
      }
      try {
        client.close();
      } catch (IOException ex) {
        // do nothing
      }
    }

    public void close() {
      try {
        client.close();
      } catch (IOException ex) {
        // do nothing
      }
    }

    private boolean nested(String s) {
      int nest1 = 0;
      int nest2 = 0;
      int nest3 = 0;
      int quote1 = 0;
      int quote2 = 0;
      for (int i = 0; i < s.length(); i++) {
        switch (s.charAt(i)) {
          case '{':
            nest1++;
            break;
          case '}':
            if (nest1 > 0) nest1--;
            break;
          case '(':
            nest2++;
            break;
          case ')':
            if (nest2 > 0) nest2--;
            break;
          case '[':
            nest3++;
            break;
          case ']':
            if (nest3 > 0) nest3--;
            break;
          case '\'':
            quote1 = 1-quote1;
            break;
          case '"':
            quote2 = 1-quote2;
            break;
        }
      }
      return nest1+nest2+nest3+quote1+quote2 > 0;
    }

  }

}
