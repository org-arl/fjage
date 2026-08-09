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
 * Request to delete a file, or a directory (if the file name ends with a path separator).
 */
public class DeleteFileReq extends Message {

  private static final long serialVersionUID = 1L;

  private String filename = null;

  /**
   * Create an empty request for file deletion.
   */
  public DeleteFileReq() {
    super(Performative.REQUEST);
  }

  /**
   * Create an empty request for file deletion.
   *
   * @param to shell agent id.
   */
  public DeleteFileReq(AgentID to) {
    super(to, Performative.REQUEST);
  }

  /**
   * Create a request to delete a file.
   *
   * @param filename name of the file to delete.
   */
  public DeleteFileReq(String filename) {
    super(Performative.REQUEST);
    this.filename = filename;
  }

  /**
   * Create a request to delete a file.
   *
   * @param to shell agent id.
   * @param filename name of the file to delete.
   */
  public DeleteFileReq(AgentID to, String filename) {
    super(to, Performative.REQUEST);
    this.filename = filename;
  }

  /**
   * Get the name of the file.
   *
   * @return name of the file.
   */
  public String getFilename() {
    return filename;
  }

  /**
   * Set the name of the file.
   *
   * @param filename name of the file.
   */
  public void setFilename(String filename) {
    this.filename = filename;
  }
}
