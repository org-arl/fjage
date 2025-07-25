/******************************************************************************

 Copyright (c) 2018-2019, Mandar Chitre

 This file is part of fjage which is released under Simplified BSD License.
 See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
 for full license details.
 ******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.arl.fjage.connectors.ConnectionListener;
import org.arl.fjage.connectors.Connector;
import org.arl.fjage.connectors.WebSocketHubConnector;
import org.jline.reader.*;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
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
  private static final Path HISTORY_FILE = Paths.get(".fjage-shell-history");
  private static String[] shellCommands;
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
  private static final List<String> EXCLUDED_METHODS = Arrays.asList("methodMissing", "propertyMissing");

  /**
   * Create a console shell attached to the system terminal.
   */
  public ConsoleShell() {
    try {
      term = TerminalBuilder.terminal();
      setupStyles();
      shellCommands = getCommandsFromGroovyScript();
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
      shellCommands = getCommandsFromGroovyScript();
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
      // safely ignore exception
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
    History history = new DefaultHistory();
    Completer myCompleter = new AggregateCompleter(
        new StringsCompleter(shellCommands), // static commands
        new HistoryCompleter(history)       // dynamic history
    );

    if (Terminal.TYPE_DUMB.equals(term.getType())) {
      console = LineReaderBuilder.builder().terminal(term).history(history).completer(myCompleter).build();
      console.setVariable(LineReader.HISTORY_FILE, HISTORY_FILE);
      console.setVariable(LineReader.HISTORY_SIZE, 1000); // set history size
      return;
    }
    if (scriptEngine == null)
      console = LineReaderBuilder.builder().terminal(term).option(LineReader.Option.AUTO_FRESH_LINE, true).history(history).completer(myCompleter).build();
    else {
      console = LineReaderBuilder.builder().terminal(term).history(history).completer(myCompleter).build();
      AutosuggestionWidgets autosuggestionWidgets = new AutosuggestionWidgets(console);
      autosuggestionWidgets.enable();
      console.setVariable(LineReader.HISTORY_FILE, HISTORY_FILE); // set history file
      console.setVariable(LineReader.HISTORY_SIZE, 1000); // set history size
      console.getWidgets().put(FORCE_BRACKETED_PASTE_ON, () -> {
        console.getTerminal().writer().write(BRACKETED_PASTE_ON);
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

  @Override
  public String toString() {
    if (connector == null) return "console://-";
    else return connector.toString();
  }

  /**
   * Get the list of commands available for completion from the BaseGroovyScript class.
   *
   * @return an array of command names.
   */
  private String[] getCommandsFromGroovyScript() {
    Set<String> commands = new HashSet<>();
    try {
      Class<?> thisClass = Class.forName("org.arl.fjage.shell.BaseGroovyScript");
      Method[] methods = thisClass.getDeclaredMethods();
      for (Method method : methods) {
        if (!method.getName().startsWith("get") &&
            !method.getName().startsWith("__") &&
            !EXCLUDED_METHODS.contains(method.getName()) &&
            !method.isSynthetic() &&
            !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
          commands.add(method.getName());
        }
      }
    } catch (ClassNotFoundException ex) {
      log.info("BaseGroovyScript class not found, no commands available for completion.");
    }

    return commands.toArray(new String[0]);
  }


}
