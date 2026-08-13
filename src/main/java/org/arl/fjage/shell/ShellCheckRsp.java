/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.Message;
import org.arl.fjage.Performative;

/**
 * Response to a ShellCheckReq, with syntax validation result and diagnostics.
 */
public class ShellCheckRsp extends Message {

  private static final long serialVersionUID = 1L;

  private boolean valid = false;
  private String diagnostics = null;

  /**
   * Create an empty response.
   */
  public ShellCheckRsp() {
    super(Performative.INFORM);
  }

  /**
   * Create a response to the ShellCheckReq.
   *
   * @param inReplyTo message to which this is a response.
   */
  public ShellCheckRsp(ShellCheckReq inReplyTo) {
    super(inReplyTo, Performative.INFORM);
  }

  /**
   * Checks if the command/script is syntactically valid.
   *
   * @return true if valid, false otherwise.
   */
  public boolean isValid() {
    return valid;
  }

  /**
   * Checks if the command/script is syntactically valid.
   *
   * @return true if valid, false otherwise.
   */
  public boolean getValid() {
    return valid;
  }

  /**
   * Marks the command/script as syntactically valid or invalid.
   *
   * @param valid true if valid, false otherwise.
   */
  public void setValid(boolean valid) {
    this.valid = valid;
  }

  /**
   * Get JSON diagnostics for an invalid command/script.
   *
   * @return JSON diagnostics string, or null if valid.
   */
  public String getDiagnostics() {
    return diagnostics;
  }

  /**
   * Set JSON diagnostics for an invalid command/script.
   *
   * @param diagnostics JSON diagnostics string.
   */
  public void setDiagnostics(String diagnostics) {
    this.diagnostics = diagnostics;
  }

}