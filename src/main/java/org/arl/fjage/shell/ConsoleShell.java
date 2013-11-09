/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.logging.Logger;
import jline.console.ConsoleReader;

/**
 * Console shell with line editing. Use three ESC to abort running processes
 * rather than ^C supported on the TcpShell.
 */
public class ConsoleShell extends Thread implements Shell {
  
  ////////// Private attributes

  private ScriptEngine engine = null;
  private Term term = new Term();
  private ConsoleReader console = null;
  private Logger log = Logger.getLogger(getClass().getName());
  private boolean quit = false;
  private boolean shutdownOnExit = true;

  ////////// Methods

  /**
   * Binds the console command shell to the script engine and activates it.
   *
   * @param engine script engine to use.
   */
  @Override
  public void bind(ScriptEngine engine) {
    this.engine = engine;
    setName(getClass().getSimpleName());
    setDaemon(true);
  }

  @Override
  public void shutdown() {
    quit = true;
  }

  /**
   * Set whether to shutdown platform when console shell is terminated.
   *
   * @param value true to initiate shutdown, false otherwise.
   */
  public void setShutdownOnExit(boolean value) {
    shutdownOnExit = value;
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
      OutputStream out = System.out;
      InputStream in = System.in;
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
      try {
        // wait a short while to let fshrc execution progress
        Thread.sleep(500);
      } catch (InterruptedException ex) {
        // do nothing
      }
      while (!quit) {
        int esc = 0;
        while (engine.isBusy()) {
          if (in.available() > 0) {
            int c = in.read();
            if (c == 27) esc += 10;
            if (esc > 20) {
              log.info("ABORT");
              engine.abort();
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
        String s1 = console.readLine();
        if (s1 == null) {
          if (shutdownOnExit) engine.exec("shutdown", null);
          break;
        }
        sb.append(s1);
        String s = sb.toString();
        nest = nested(s);
        if (nest) sb.append('\n');
        else if (s.length() > 0) {
          sb = new StringBuffer();
          log.info("> "+s);
          boolean ok = engine.exec(s, this);
          if (!ok) {
            console.println(term.error("BUSY"));
            log.info("BUSY");
          }
        }
      }
    } catch (IOException ex) {
      log.warning(ex.toString());
    }
  }
  
  @Override
  public void println(Object obj, OutputType type) {
    if (obj == null) return;
    String s = obj.toString();
    try {
      if (console != null) {
        switch(type) {
          case INPUT:
            console.println(s);
            break;
          case OUTPUT:
            console.println(term.response(s));
            break;
          case ERROR:
            console.println(term.error(s));
            break;
          case NOTIFY:
            console.println(term.notification(s));
            break;
          default:
            return;
        }
        if (term.isEnabled()) console.redrawLine();
        console.flush();
      }
    } catch (Exception ex) {
      log.warning("println: "+ex.toString());
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
