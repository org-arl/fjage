/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.AgentID;
import org.arl.fjage.Message;
import org.arl.fjage.Performative;

/**
 * Request to check shell command/script syntax without executing it.
 */
public class ShellCheckReq extends Message {

  private static final long serialVersionUID = 1L;

  private String command = null;
  private String filename = null;

  /**
   * Create an empty request for shell syntax check.
   */
  public ShellCheckReq() {
    super(Performative.REQUEST);
  }

  /**
   * Create an empty request for shell syntax check.
   *
   * @param to shell agent id.
   */
  public ShellCheckReq(AgentID to) {
    super(to, Performative.REQUEST);
  }

  /**
   * Create request for shell command syntax check.
   *
   * @param cmd command to check.
   */
  public ShellCheckReq(String cmd) {
    super(Performative.REQUEST);
    this.command = cmd;
  }

  /**
   * Create request for shell command syntax check.
   *
   * @param to shell agent id.
   * @param cmd command to check.
   */
  public ShellCheckReq(AgentID to, String cmd) {
    super(to, Performative.REQUEST);
    this.command = cmd;
  }


  /**
   * Set the command to check.
   *
   * @param cmd command to check.
   */
  public void setCommand(String cmd) {
    if (cmd != null && filename != null) throw new UnsupportedOperationException("ShellCheckReq can either have a command or filename, but not both");
    this.command = cmd;
  }

  /**
   * Get the command to check.
   *
   * @return command to check, null if none.
   */
  public String getCommand() {
    return command;
  }

  /**
   * Set the script to check.
   *
   * @param script script file to check.
   */
  /**
   * Set the filename of the script to check.
   *
   * @param filename script filename to check.
   */
  public void setFilename(String filename) {
    if (filename != null && command != null) throw new UnsupportedOperationException("ShellCheckReq can either have a command or filename, but not both");
    this.filename = filename;
  }

  /**
   * Get the filename of the script to check.
   *
   * @return script filename to check, null if none.
   */
  public String getFilename() {
    return filename;
  }

  /**
   * Check if the request is for a script file.
   *
   * @return true if request is for a script file, false if it is for a command.
   */
  public boolean isScript() {
    return filename != null;
  }

}