/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.net.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
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
  private ConsoleReader console = null;
  private Term term = new Term();

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
  public void println(String s, OutputType type) {
    s = s.replace("\n","\r\n")+"\r\n";
    try {
      if (console != null) {
        switch(type) {
          case RESPONSE:
            console.print(term.response(s));
            break;
          case NOTIFICATION:
            console.print(term.notification(s));
            break;
          case ERROR:
            console.print(term.error(s));
            break;
          default:
            console.print(s);
            break;
        }
        if (term.isEnabled()) console.redrawLine();
        console.flush();
      }
    } catch (IOException ex) {
      // do nothing
    }
  }

  ////////// Private stuff

  private class ClientThread extends Thread {

    private Socket client;

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
        try {
          console.getTerminal().init();
        } catch (Exception ex) {
          // do nothing
        }
        if (!console.getTerminal().isAnsiSupported()) term.disable();
        console.setExpandEvents(false);
        console.addTriggeredAction((char)27, new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
            try {
              console.redrawLine();
            } catch (IOException ex) {
              // do nothing
            }
          }
        });
        StringBuffer sb = new StringBuffer();
        boolean nest = false;
        while (true) {
          int esc = 0;
          while (engine.isBusy()) {
            if (in.available() > 0) {
              int c = in.read();
              if (c == 3) engine.abort();
              else if (c == 27) esc += 10;
              if (esc > 20) {
                engine.abort();
                log.info("ABORT");
              }
            } else if (esc > 0) esc--;
            try {
              sleep(100);
            } catch (InterruptedException ex) {
              interrupt();
            }
          }
          if (sb.length() > 0) console.setPrompt(term.prompt("- "));
          else console.setPrompt(term.prompt("> "));
          while (in.available() > 0) in.read();
          String s1 = console.readLine();
          console.print("\r");
          console.flush();
          if (s1 == null) break;
          sb.append(s1);
          String s = sb.toString();
          nest = nested(s);
          if (nest) sb.append('\n');
          else if (s.length() > 0) {
            sb = new StringBuffer();
            log.info("> "+s);
            boolean ok = engine.exec(s, TcpShell.this);
            if (!ok) {
              println("BUSY", OutputType.ERROR);
              log.info("BUSY");
            }
          }
        }

      } catch (Exception ex) {
        log.warning(ex.toString());
      }
      log.info("Connection closed");
      console = null;
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
