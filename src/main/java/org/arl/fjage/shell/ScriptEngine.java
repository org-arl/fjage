/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.List;

/**
 * An interface representing a scripting engine.
 *
 * @author Mandar Chitre
 */
public interface ScriptEngine {

  /**
   * Execute a command. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param cmd command to execute.
   * @param out output stream (null to suppress output).
   * @return true if accepted for execution, false if busy.
   */
  public boolean exec(String cmd, ScriptOutputStream out);

  /**
   * Execute a script file. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param script script file to execute.
   * @param out output stream (null to suppress output).
   * @return true if accepted for execution, false if busy.
   */
  public boolean exec(File script, ScriptOutputStream out);

  /**
   * Execute a script file. The method does not wait for execution to be completed
   * but returns immediately.
   *
   * @param script script file to execute.
   * @param args arguments to pass to script.
   * @param out output stream (null to suppress output).
   * @return true if accepted for execution, false if busy.
   */
  public boolean exec(File script, List<String> args, ScriptOutputStream out);
  
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
   * Get last script/command return value.
   * 
   * @return return value from last script/command.
   */
  public Object getResult();
  
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

