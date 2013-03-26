/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.File;
import java.util.List;

import org.arl.fjage.AgentID;
import org.arl.fjage.Message;
import org.arl.fjage.Performative;

/**
 * Request to execute shell command/script.
 */
public class ShellExecReq extends Message {
  
  private static final long serialVersionUID = 1L;

  private String cmd;
  private File script;
  private List<String> args;

  /**
   * Create request for shell command.
   * 
   * @param to shell agent id.
   * @param cmd command to execute.
   */
  public ShellExecReq(AgentID to, String cmd) {
    super(to, Performative.REQUEST);
    this.cmd = cmd;
    this.script = null;
    this.args = null;
  }

  /**
   * Create request to execute shell script.
   * 
   * @param to shell agent id.
   * @param script script file to execute.
   */
  public ShellExecReq(AgentID to, File script) {
    super(to, Performative.REQUEST);
    this.cmd = null;
    this.script = script;
    this.args = null;
  }

  /**
   * Create request to execute shell script with arguments.
   * 
   * @param to shell agent id.
   * @param script script file to execute.
   * @param args arguments to pass to script.
   */
  public ShellExecReq(AgentID to, File script, List<String> args) {
    super(to, Performative.REQUEST);
    this.cmd = null;
    this.script = script;
    this.args = args;
  }

  /**
   * Get the command to execute.
   * 
   * @return command to execute, null if none.
   */
  public String getCommand() {
    return cmd;
  }
  
  /**
   * Get the script to execute.
   * 
   * @return script to execute, null if none.
   */
  public File getScriptFile() {
    return script;
  }
  
  /**
   * Get the script arguments.
   * 
   * @return script arguments, null if none.
   */
  public List<String> getScriptArgs() {
    return args;
  }

  /**
   * Check if the request is for a script.
   * 
   * @return true if request is for a script, false if it is for a command.
   */
  public boolean isScript() {
    return script != null;
  }

}

