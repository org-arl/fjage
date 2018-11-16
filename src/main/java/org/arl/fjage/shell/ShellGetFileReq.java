/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.AgentID;
import org.arl.fjage.Message;
import org.arl.fjage.Performative;

/**
 * Request to read a file or a directory.
 *
 * If the filename specified represents a directory, then the contents of the
 * directory (list of files) are returned as a tab separated string with:
 *   filename, file size, last modification time.
 *
 * The time is represented as epoch time (milliseconds since 1 Jan 1970).
 */
public class ShellGetFileReq extends Message {

  private static final long serialVersionUID = 1L;

  private String filename = null;

  /**
   * Create an empty request for file/directory.
   */
  public ShellGetFileReq() {
    super(Performative.REQUEST);
  }

  /**
   * Create an empty request for file/directory.
   *
   * @param to shell agent id.
   */
  public ShellGetFileReq(AgentID to) {
    super(to, Performative.REQUEST);
  }

  /**
   * Create request for file/directory.
   *
   * @param to shell agent id.
   * @param filename name of the file/directory to read.
   */
  public ShellGetFileReq(AgentID to, String filename) {
    super(to, Performative.REQUEST);
    this.filename = filename;
  }

  /**
   * Get the name of the file/directory.
   *
   * @return name of the file/directory.
   */
  public String getFilename() {
    return filename;
  }

  /**
   * Set the name of the file/directory.
   *
   * @param filename name of the file/directory.
   */
  public void setFilename(String filename) {
    this.filename = filename;
  }

}
