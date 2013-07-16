/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.awt.event.*;
import java.io.*;
import java.util.logging.Logger;
import jline.console.ConsoleReader;

/**
 * Console shell with line editing. Use three ESC to abort running processes
 * rather than ^C supported on the TcpShell.
 */
public class ConsoleShell extends Thread implements Shell {
  
  ////////// Private attributes

  private ScriptEngine engine = null;
  private ScriptOutputStream sos = new ScriptOutputStream();
  private ConsoleReader console = null;
  private Logger log = Logger.getLogger(getClass().getName());

  ////////// Methods

  /**
   * Binds the console command shell to the script engine and activates it.
   *
   * @param engine script engine to use.
   */
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
      OutputStream out = System.out;
      InputStream in = System.in;
      console = new ConsoleReader(in, out);
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
      sos.setOutputStream(out);
      Term term = sos.getTerm();
      StringBuffer sb = new StringBuffer();
      boolean nest = false;
      while (true) {
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
        if (s1 == null) break;
        sb.append(s1);
        String s = sb.toString();
        nest = nested(s);
        if (nest) sb.append('\n');
        else if (s.length() > 0) {
          sb = new StringBuffer();
          log.info("> "+s);
          boolean ok = engine.exec(s, sos);
          if (!ok) {
            sos.println(term.error("BUSY"));
            log.info("BUSY");
          }
        }
      }
      sos.println("");
      System.exit(0);
    } catch (IOException ex) {
      // do nothing
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
    try {
      if (console != null) {
        // for some strange reason, works well with a short delay!
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          interrupt();
        }
        console.redrawLine();
      }
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
