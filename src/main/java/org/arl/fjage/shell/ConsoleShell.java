/******************************************************************************

 Copyright (c) 2018-2019, Mandar Chitre

 This file is part of fjage which is released under Simplified BSD License.
 See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
 for full license details.
 ******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.arl.fjage.connectors.ConnectionListener;
import org.arl.fjage.connectors.Connector;
import org.arl.fjage.connectors.WebSocketHubConnector;
import org.jline.reader.*;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.widget.AutosuggestionWidgets;

/**
 * Shell input/output driver for console devices with line editing and
 * color support.
 */
public class ConsoleShell implements Shell, ConnectionListener {

  private static final String FORCE_BRACKETED_PASTE_ON = "FORCE_BRACKETED_PASTE_ON";
  private static final String BRACKETED_PASTE_ON = "\033[?2004h";
  private static final String APPLICATION_CURSOR = "\033[?1h";
  private final Logger log = Logger.getLogger(getClass().getName());
  private Terminal term = null;
  private LineReader console = null;
  private Connector connector = null;
  private ScriptEngine scriptEngine = null;
  private AttributedStyle promptStyle = null;
  private AttributedStyle inputStyle = null;
  private AttributedStyle outputStyle = null;
  private AttributedStyle notifyStyle = null;
  private AttributedStyle errorStyle = null;
  private Path historyFile = null;

  /**
   * Create a console shell attached to the system terminal.
   */
  public ConsoleShell() {
    try {
      term = TerminalBuilder.terminal();
      setupStyles();
    } catch (IOException ex) {
      log.log(Level.WARNING, "Unable to open terminal: ", ex);
    }
  }

  /**
   * Create a console shell attached to a specified input and output stream.
   *
   * @param in  input stream.
   * @param out output stream.
   */
  public ConsoleShell(InputStream in, OutputStream out) {
    try {
      term = TerminalBuilder.builder().system(false).type("xterm").jni(false).jansi(false).streams(in, out).build();
      setupStyles();
    } catch (IOException ex) {
      log.log(Level.WARNING, "Unable to open terminal: ", ex);
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
      term = TerminalBuilder.builder().system(false).type("xterm").jni(false).jansi(false).streams(in, out).build();
      setupStyles();
    } catch (IOException ex) {
      log.log(Level.WARNING, "Unable to open terminal: ", ex);
    }
  }

  /**
   * Create a console shell attached to the system terminal.
   * @param historyFile file to store command history.
   */
  public ConsoleShell(Path historyFile) {
    this();
    this.historyFile = historyFile;
  }

  /**
   * Create a console shell attached to a specified input and output stream.
   *
   * @param in  input stream.
   * @param out output stream.
   * @param historyFile file to store command history.
   */
  public ConsoleShell(InputStream in, OutputStream out, Path historyFile) {
    this(in, out);
    this.historyFile = historyFile;
  }

  /**
   * Create a console shell attached to a specified connector.
   *
   * @param connector input/output streams.
   * @param historyFile file to store command history.
   */
  public ConsoleShell(Connector connector, Path historyFile){
    this(connector);
    this.historyFile = historyFile;
  }

  @Override
  public void connected(Connector connector) {
    try {
      if (console != null) {
        // force bracketed paste mode on for websockets based shells
        if (connector instanceof WebSocketHubConnector) console.callWidget(FORCE_BRACKETED_PASTE_ON);
        console.callWidget(LineReader.REDRAW_LINE);
        console.callWidget(LineReader.REDISPLAY);
      }
    } catch (IllegalStateException ex) {
      log.log(Level.FINE, "Unable to redraw console: ", ex);
    }
  }

  private void setupStyles() {
    AttributedStyle style = new AttributedStyle();
    promptStyle = style.foreground(AttributedStyle.BRIGHT + AttributedStyle.YELLOW);
    inputStyle = style.foreground(AttributedStyle.WHITE);
    outputStyle = style.foreground(AttributedStyle.GREEN);
    notifyStyle = style.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLUE);
    errorStyle = style.foreground(AttributedStyle.RED);
  }

  @Override
  public void init(ScriptEngine engine) {
    if (term == null) return;
    scriptEngine = engine;
    if (Terminal.TYPE_DUMB.equals(term.getType())) {
      console = LineReaderBuilder.builder().terminal(term).build();
      console.setVariable(LineReader.DISABLE_COMPLETION, true);
      return;
    }
    if (scriptEngine == null)
      console = LineReaderBuilder.builder().terminal(term).option(LineReader.Option.AUTO_FRESH_LINE, true).build();
    else {
      if (historyFile == null) {
        console = LineReaderBuilder.builder().terminal(term).build();
      } else {
        console = LineReaderBuilder.builder().terminal(term).history(new DefaultHistory()).build();
        console.setVariable(LineReader.HISTORY_FILE, historyFile); // set history file
        console.setVariable(LineReader.HISTORY_SIZE, 1000); // set history size
      }
      AutosuggestionWidgets autosuggestionWidgets = new AutosuggestionWidgets(console);
      autosuggestionWidgets.enable();
      console.setVariable(LineReader.DISABLE_COMPLETION, true);
      console.setOpt(LineReader.Option.ERASE_LINE_ON_FINISH);
      console.getWidgets().put(FORCE_BRACKETED_PASTE_ON, () -> {
        console.getTerminal().writer().write(BRACKETED_PASTE_ON);
        console.getTerminal().writer().write(APPLICATION_CURSOR);
        console.getTerminal().flush();
        return true;
      });
    }
  }

  @Override
  public void prompt(Object obj) {
    if (obj == null || console == null) return;
    console.printAbove(new AttributedString(obj.toString(), promptStyle));
  }

  @Override
  public void input(Object obj) {
    if (obj == null || console == null || isDumb()) return;
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
      return console.readLine(prompt1, null, (Character) null, line);
    } catch (UserInterruptException ex) {
      return ABORT;
    } catch (EndOfFileException ex) {
      return null;
    } catch (Throwable ex) {
      log.log(Level.WARNING, "Error reading line: ", ex);
      return "";
    }
  }

  @Override
  public boolean isDumb() {
    return Terminal.TYPE_DUMB.equals(term.getType());
  }

  @Override
  public void shutdown() {
    if (term != null) {
      try {
        term.close();
      } catch (IOException ex) {
        log.log(Level.FINE, "Error closing terminal: ", ex);
      }
      term = null;
    }
    if (connector != null) {
      connector.close();
      connector = null;
    }
    console = null;
  }

  @Override
  public String toString() {
    if (connector == null) return "console://-";
    else return connector.toString();
  }

}
