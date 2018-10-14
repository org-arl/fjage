/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.logging.*;
import org.jline.reader.*;
import org.jline.terminal.*;
import org.jline.utils.*;

/**
 * Shell input/output driver for console devices.
 */
public class ConsoleShell implements Shell {

  private Terminal term = null;
  private LineReader console = null;
  private ScriptEngine scriptEngine = null;
  private AttributedStyle outputStyle = null;
  private AttributedStyle notifyStyle = null;
  private AttributedStyle errorStyle = null;
  private Logger log = Logger.getLogger(getClass().getName());

  public ConsoleShell() {
    try {
      term = TerminalBuilder.terminal();
    } catch (IOException ex) {
      log.warning("Unable to open terminal: "+ex.toString());
    }
    AttributedStyle style = new AttributedStyle();
    outputStyle = style.foreground(AttributedStyle.GREEN);
    notifyStyle = style.foreground(AttributedStyle.BLUE);
    errorStyle = style.foreground(AttributedStyle.RED);
  }

  public ConsoleShell(InputStream in, OutputStream out, boolean dumb) {
    try {
      term = TerminalBuilder.builder().streams(in, out).build();
    } catch (IOException ex) {
      log.warning("Unable to open terminal: "+ex.toString());
    }
    if (!dumb) {
      AttributedStyle style = new AttributedStyle();
      outputStyle = style.foreground(AttributedStyle.GREEN);
      notifyStyle = style.foreground(AttributedStyle.BLUE);
      errorStyle = style.foreground(AttributedStyle.RED);
    }
  }

  public void init(ScriptEngine engine) {
    if (term == null) return;
    scriptEngine = engine;
    if (scriptEngine == null) console = LineReaderBuilder.builder().terminal(term).build();
    else {
      Parser parser = new Parser() {
        @Override
        public ParsedLine parse(String s, int cursor) {
          if (!scriptEngine.isComplete(s)) throw new EOFError(0, cursor, "Incomplete sentence");
          return null;
        }
        @Override
        public ParsedLine parse(String s, int cursor, Parser.ParseContext context) {
          return parse(s, cursor);
        }
      };
      console = LineReaderBuilder.builder().parser(parser).terminal(term).build();
    }
  }

  public void println(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), outputStyle));
  }

  public void notify(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), notifyStyle));
  }

  public void error(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), errorStyle));
  }

  public String readLine(String prompt1, String prompt2, String line) {
    if (console == null) return null;
    try {
      console.setVariable(LineReader.SECONDARY_PROMPT_PATTERN, prompt2);
      return console.readLine(prompt1, null, (Character)null, line);
    } catch (UserInterruptException ex) {
      return ABORT;
    } catch (Throwable ex) {
      Thread.interrupted();
      return null;
    }
  }

  public void shutdown() {
    console = null;
  }

}
