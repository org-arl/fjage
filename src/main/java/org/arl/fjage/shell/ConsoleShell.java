/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.IOException;
import jline.Terminal;
import jline.console.ConsoleReader;
import jline.console.UserInterruptException;

/**
 * Shell input/output driver for console devices.
 */
public class ConsoleShell implements Shell {

  public static final String ESC = "\033[";
  public static final String RESET = ESC+"0m";
  public static final String BLACK = ESC+"30m";
  public static final String RED = ESC+"31m";
  public static final String GREEN = ESC+"32m";
  public static final String YELLOW = ESC+"33m";
  public static final String BLUE = ESC+"34m";
  public static final String MAGENTA = ESC+"35m";
  public static final String CYAN = ESC+"36m";
  public static final String WHITE = ESC+"37m";
  public static final String HOME = ESC+"0G";
  public static final String UP = ESC+"A";
  public static final String CLREOL = ESC+"0K";

  /**
   * Color of println output on terminals supporting ANSI sequences.
   */
  public String PROMPT = BLUE;

  /**
   * Color of println output on terminals supporting ANSI sequences.
   */
  public String OUTPUT = GREEN;

  /**
   * Color of notifications on terminals supporting ANSI sequences.
   */
  public String NOTIFY = BLUE;

  /**
   * Color of errors on terminals supporting ANSI sequences.
   */
  public String ERROR = RED;

  private ConsoleReader console = null;
  private boolean ansiEnable = false;

  private String ansi(String t, String s) {
    if (s == null) s = "";
    if (!ansiEnable) return s;
    return t + s + RESET;
  }

  private String ansi(String t) {
    return ansi(t, null);
  }

  public void init() {
    try {
      console = new ConsoleReader(System.in, System.out);
      console.setHandleUserInterrupt(true);
      console.setHistoryEnabled(true);
      try {
        Terminal t = console.getTerminal();
        t.init();
        ansiEnable = t.isAnsiSupported();
      } catch (Exception ex) {
        // do nothing
      }
    } catch (IOException ex) {
      System.err.println(ex.toString());
      console = null;
    }
  }

  private void output(String s) {
    if (console == null) return;
    try {
      console.println(s);
    } catch(IOException ex) {
      System.err.println(ex.toString());
      console = null;
    }
  }

  public void println(Object obj) {
    if (obj == null) return;
    output(ansi(HOME+OUTPUT, obj.toString()));
  }

  public void notify(Object obj) {
    if (obj == null) return;
    output(ansi(HOME+NOTIFY, obj.toString()));
  }

  public void error(Object obj) {
    if (obj == null) return;
    output(ansi(HOME+ERROR, obj.toString()));
  }

  public void alert() {
    console.bell();
  }

  public String readLine(String prompt, String line) {
    if (console == null) return null;
    try {
      if (line != null) console.print(ansi(UP));
      console.print(ansi(HOME+CLREOL));
      console.setPrompt(ansi(HOME+PROMPT, prompt));
      if (line != null) {
        console.putString(line);
        console.setCursorPosition(0);
      }
      console.drawLine();
      return console.readLine();
    } catch (IOException ex) {
      System.err.println(ex.toString());
      console = null;
      return null;
    } catch (UserInterruptException ex) {
      return null;
    }
  }

  public void shutdown() {
    console = null;
  }

}
