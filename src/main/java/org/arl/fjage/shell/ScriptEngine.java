/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.File;
import java.io.Reader;
import java.util.List;

/**
 * An interface representing a scripting engine.
 */
public interface ScriptEngine {

  /**
   * Bind input/output shell.
   *
   * @param shell shell for input/output (null to supress).
   */
  public void bind(Shell shell);

  /**
   * Get the current prompt.
   *
   * @return prompt string or null to let shell choose prompt.
   */
  public String getPrompt();

  /**
   * Checks if a string is a complete command. This can be used by a shell to determine
   * if a multiline command is being composed by the user.
   *
   * @param cmd command to check.
   * @return true if complete, false if incomplete.
   */
  public boolean isComplete(String cmd);

  /**
   * Execute a command. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param cmd command to execute.
   * @return true if accepted for execution, false if busy.
   */
  public boolean exec(String cmd);

  /**
   * Execute a script file. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param script script file to execute.
   * @return true if accepted for execution, false if busy.
   */
  public boolean exec(File script);

  /**
   * Execute a script file. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param script script file to execute.
   * @param args arguments to pass to script.
   * @return true if accepted for execution, false if busy.
   */
  public boolean exec(File script, List<String> args);

  /**
   * Execute a precompiled script. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param script script class to execute.
   * @return true if accepted for execution, false if busy or unable to instantiate class.
   */
  public boolean exec(Class<?> script);

  /**
   * Execute a precomplied script. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param script script class to execute.
   * @param args arguments to pass to script.
   * @return true if accepted for execution, false if busy or unable to instantiate class.
   */
  public boolean exec(Class<?> script, List<String> args);

  /**
   * Execute a script from a reader. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param reader reader to read script from.
   * @param name reader name for logging.
   * @return true if accepted for execution, false if busy.
   */
  public boolean exec(Reader reader, String name);

  /**
   * Execute a script from a reader. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param reader reader to read script from.
   * @param name reader name for logging.
   * @param args arguments to pass to script.
   * @return true if accepted for execution, false if busy.
   */
  public boolean exec(Reader reader, String name, List<String> args);

  /**
   * Check if script is currently being executed.
   */
  public boolean isBusy();

  /**
   * Abort currently running script.
   */
  public void abort();

  /**
   * Wait for script/command execution to complete.
   */
  public void waitUntilCompletion();

  /**
   * Bind script variable.
   *
   * @param name name of script variable.
   * @param value of script variable.
   */
  public void setVariable(String name, Object value);

  /**
   * Get value of script variable.
   *
   * @param name name of script variable.
   * @return value of script variable.
   */
  public Object getVariable(String name);

  /**
   * Terminate the scripting engine.
   */
  public void shutdown();

}
