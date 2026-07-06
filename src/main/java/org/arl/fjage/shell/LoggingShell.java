/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shell decorator that logs all output displayed to the user before delegating
 * to the underlying shell. Output lines are logged at FINE level with a "&lt; "
 * prefix to mirror the "&gt; " prefix used when logging user input.
 */
public class LoggingShell implements Shell {

  private static final int MAX_LEN = 8192;
  private static final int SNIPPET_LEN = 32;

  private final Shell delegate;
  private final Supplier<Logger> log;

  /**
   * Wrap a shell to log all output using this class's logger.
   *
   * @param delegate shell to wrap.
   */
  public LoggingShell(Shell delegate) {
    this(delegate, null);
  }

  /**
   * Wrap a shell to log all output using a specified logger.
   *
   * @param delegate shell to wrap.
   * @param log supplier of logger to log output to, or null to use this class's logger.
   */
  public LoggingShell(Shell delegate, Supplier<Logger> log) {
    if (delegate == null) throw new NullPointerException("delegate shell cannot be null");
    this.delegate = delegate;
    if (log == null) {
      Logger deflog = Logger.getLogger(getClass().getName());
      log = () -> deflog;
    }
    this.log = log;
  }

  /**
   * Get the underlying shell being decorated.
   *
   * @return the wrapped shell.
   */
  public Shell getDelegate() {
    return delegate;
  }

  @Override
  public void init(ScriptEngine engine) {
    delegate.init(engine);
  }

  @Override
  public void prompt(Object obj) {
    logOutput(Level.FINER, "? ", obj);
    delegate.prompt(obj);
  }

  @Override
  public void input(Object obj) {
    logOutput(Level.FINER, "", obj);
    delegate.input(obj);
  }

  @Override
  public void println(Object obj) {
    logOutput(Level.FINE, "< ", obj);
    delegate.println(obj);
  }

  @Override
  public void notify(Object obj) {
    // not logged, since incoming messages are already logged by the shell agent
    delegate.notify(obj);
  }

  @Override
  public void error(Object obj) {
    logOutput(Level.FINE, "< ERROR: ", obj);
    delegate.error(obj);
  }

  @Override
  public String readLine(String prompt1, String prompt2, String line) {
    return delegate.readLine(prompt1, prompt2, line);
  }

  @Override
  public boolean isDumb() {
    return delegate.isDumb();
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  // log the String version only, and never hold on to AgentIDs/Messages,
  // otherwise the toString() extensions can get called too often by GUI
  private void logOutput(Level level, String prefix, Object obj) {
    Logger logger = log.get();
    if (obj == null || !logger.isLoggable(level)) return;
    String s = obj.toString();
    int n = s.length();
    if (n > MAX_LEN) s = s.substring(0, SNIPPET_LEN) + " <<snip>> " + s.substring(n - SNIPPET_LEN);
    logger.log(level, prefix + s);
  }

}
