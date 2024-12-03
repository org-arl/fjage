/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.AgentID;
import org.arl.fjage.Message;
import org.arl.fjage.Performative;

import java.io.File;
import java.util.List;

/**
 * Request to execute shell command/script.
 */
public class ShellExecReq extends Message {

  private static final long serialVersionUID = 1L;

  private String command = null;
  private File script = null;
  private List<String> scriptArgs = null;
  private boolean ans = false;

  /**
   * Create an empty request for shell command.
   */
  public ShellExecReq() {
    super(Performative.REQUEST);
  }

  /**
   * Create an empty request for shell command.
   *
   * @param to shell agent id.
   */
  public ShellExecReq(AgentID to) {
    super(to, Performative.REQUEST);
  }

  /**
   * Create request for shell command.
   *
   * @param cmd command to execute.
   */
  public ShellExecReq(String cmd) {
    super(Performative.REQUEST);
    this.command = cmd;
  }

  /**
   * Create request for shell command.
   *
   * @param to shell agent id.
   * @param cmd command to execute.
   */
  public ShellExecReq(AgentID to, String cmd) {
    super(to, Performative.REQUEST);
    this.command = cmd;
  }

  /**
   * Create request to execute shell script.
   *
   * @param script script file to execute.
   */
  public ShellExecReq(File script) {
    super(Performative.REQUEST);
    this.script = script;
  }

  /**
   * Create request to execute shell script.
   *
   * @param to shell agent id.
   * @param script script file to execute.
   */
  public ShellExecReq(AgentID to, File script) {
    super(to, Performative.REQUEST);
    this.script = script;
  }

  /**
   * Create request to execute shell script with arguments.
   *
   * @param script script file to execute.
   * @param args arguments to pass to script.
   */
  public ShellExecReq(File script, List<String> args) {
    super(Performative.REQUEST);
    this.script = script;
    this.scriptArgs = args;
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
    this.script = script;
    this.scriptArgs = args;
  }

  /**
   * Set the command to execute.
   *
   * @param cmd command to execute.
   */
  public void setCommand(String cmd) {
    if (cmd != null && script != null) throw new UnsupportedOperationException("ShellExecReq can either have a command or script, but not both");
    this.command = cmd;
  }

  /**
   * Get the command to execute.
   *
   * @return command to execute, null if none.
   */
  public String getCommand() {
    return command;
  }

  /**
   * Set the command to execute.
   *
   * @param script script file to execute.
   */
  public void setScript(File script) {
    if (script != null && command != null) throw new UnsupportedOperationException("ShellExecReq can either have a command or script, but not both");
    this.script = script;
  }

  /**
   * Set the command to execute.
   *
   * @param script script file to execute.
   * @param args arguments to pass to script.
   */
  public void setScript(File script, List<String> args) {
    if (script != null && command != null) throw new UnsupportedOperationException("ShellExecReq can either have a command or script, but not both");
    this.script = script;
    this.scriptArgs = args;
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
    return scriptArgs;
  }

  /**
   * Check if the request is for a script.
   *
   * @return true if request is for a script, false if it is for a command.
   */
  public boolean isScript() {
    return script != null;
  }

  /**
   * Set a flag to indicate that the output from the command should be returned in the response,
   * when possible.
   *
   * @param ans true to get output, false otherwise
   */
  public void setAns(boolean ans) {
    this.ans = ans;
  }

  /**
   * Get the flag that indicates whether the output from the command should be returned in the response,
   * when possible.
   *
   * @return true to get output, false otherwise
   */
  public boolean getAns() {
    return ans;
  }

}

