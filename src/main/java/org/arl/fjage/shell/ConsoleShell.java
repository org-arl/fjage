/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.connectors.ConnectionListener;
import org.arl.fjage.connectors.Connector;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * Shell input/output driver for console devices.
 */
public class ConsoleShell implements Shell, ConnectionListener {

  private Terminal term = null;
  private LineReader console = null;
  private Connector connector = null;
  private ScriptEngine scriptEngine = null;
  private AttributedStyle promptStyle = null;
  private AttributedStyle inputStyle = null;
  private AttributedStyle outputStyle = null;
  private AttributedStyle notifyStyle = null;
  private AttributedStyle errorStyle = null;
  private Logger log = Logger.getLogger(getClass().getName());

  /**
   * Create a console shell attached to the system terminal.
   */
  public ConsoleShell() {
    try {
      term = TerminalBuilder.terminal();
      setupStyles();
    } catch (IOException ex) {
      log.warning("Unable to open terminal: "+ex.toString());
    }
  }

  /**
   * Create a console shell attached to a specified input and output stream.
   *
   * @param in input stream.
   * @param out output stream.
   */
  public ConsoleShell(InputStream in, OutputStream out) {
    try {
      term = TerminalBuilder.builder().streams(in, out).build();
      setupStyles();
    } catch (IOException ex) {
      log.warning("Unable to open terminal: "+ex.toString());
    }
  }

  /**
   * Create a console shell attached to a specified input and output stream.
   *
   * @param in input stream.
   * @param out output stream.
   * @param dumb true to force a dumb terminal, false otherwise.
   */
  public ConsoleShell(InputStream in, OutputStream out, boolean dumb) {
    try {
      if (dumb) term = new org.jline.terminal.impl.DumbTerminal(in, out);
      else {
        term = TerminalBuilder.builder().streams(in, out).dumb(false).build();
        setupStyles();
      }
    } catch (IOException ex) {
      log.warning("Unable to open terminal: "+ex.toString());
    }
  }

  /**
   * Create a console shell attached to a specified connector.
   *
   * @param connector input/output streams.
   */
  public ConsoleShell(Connector connector) {
    try {
      InputStream in = connector.getInputStream();
      OutputStream out = connector.getOutputStream();
      connector.setConnectionListener(this);
      this.connector = connector;
      term = TerminalBuilder.builder().streams(in, out).build();
      setupStyles();
    } catch (IOException ex) {
      log.warning("Unable to open terminal: "+ex.toString());
    }
  }

  /**
   * Create a console shell attached to a specified connector.
   *
   * @param connector input/output streams.
   * @param dumb true to force a dumb terminal, false otherwise.
   */
  public ConsoleShell(Connector connector, boolean dumb) {
    try {
      InputStream in = connector.getInputStream();
      OutputStream out = connector.getOutputStream();
      connector.setConnectionListener(this);
      this.connector = connector;
      if (dumb) term = new org.jline.terminal.impl.DumbTerminal(in, out);
      else {
        term = TerminalBuilder.builder().streams(in, out).dumb(false).build();
        setupStyles();
      }
    } catch (IOException ex) {
      log.warning("Unable to open terminal: "+ex.toString());
    }
  }

  @Override
  public void connected(Connector connector) {
    try {
      if (console != null) {
        console.callWidget(LineReader.REDRAW_LINE);
        console.callWidget(LineReader.REDISPLAY);
      }
    } catch(IllegalStateException ex) {
      // safely ignore exception
    }
  }

  private void setupStyles() {
    AttributedStyle style = new AttributedStyle();
    promptStyle = style.foreground(AttributedStyle.BRIGHT+AttributedStyle.YELLOW);
    inputStyle = style.foreground(AttributedStyle.WHITE);
    outputStyle = style.foreground(AttributedStyle.GREEN);
    notifyStyle = style.foreground(AttributedStyle.BRIGHT+AttributedStyle.BLUE);
    errorStyle = style.foreground(AttributedStyle.RED);
  }

  @Override
  public void init(ScriptEngine engine) {
    if (term == null) return;
    scriptEngine = engine;
    if (scriptEngine == null) console = LineReaderBuilder.builder().terminal(term).option(LineReader.Option.AUTO_FRESH_LINE, true).build();
    else {
      Parser parser = new Parser() {
        @Override
        public CompletingParsedLine parse(String s, int cursor) {
          if (!scriptEngine.isComplete(s)) throw new EOFError(-1, -1, "");
          if (s.contains("\n") && cursor < s.length()) throw new EOFError(-1, -1, "");
          return null;
        }
        @Override
        public CompletingParsedLine parse(String s, int cursor, Parser.ParseContext context) {
          return parse(s, cursor);
        }
      };
      console = LineReaderBuilder.builder().parser(parser).terminal(term).build();
      console.setVariable(LineReader.DISABLE_COMPLETION, true);
      console.setOpt(LineReader.Option.ERASE_LINE_ON_FINISH);
    }
  }

  @Override
  public void prompt(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), promptStyle));
  }

  @Override
  public void input(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), inputStyle));
  }

  @Override
  public void println(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), outputStyle));
  }

  @Override
  public void notify(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), notifyStyle));
  }

  @Override
  public void error(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), errorStyle));
  }

  @Override
  public String readLine(String prompt1, String prompt2, String line) {
    if (console == null) return null;
    try {
      console.setVariable(LineReader.SECONDARY_PROMPT_PATTERN, prompt2);
      return console.readLine(prompt1, null, (Character)null, line);
    } catch (UserInterruptException ex) {
      return ABORT;
    } catch (Throwable ex) {
      log.warning(ex.toString());
      return null;
    }
  }

  @Override
  public void shutdown() {
    if (term != null) {
      try {
        term.close();
      } catch (IOException ex) {
        // do nothing
      }
      term = null;
    }
    if (connector != null) {
      connector.close();
      connector = null;
    }
    console = null;
  }

}
