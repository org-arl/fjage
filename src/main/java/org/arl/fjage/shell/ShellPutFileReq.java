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
 * Request to write contents to a file, or delete a file.
 */
public class ShellPutFileReq extends Message {

  private static final long serialVersionUID = 1L;

  private String filename = null;
  private byte[] contents = null;

  /**
   * Create an empty request for file write.
   */
  public ShellPutFileReq() {
    super(Performative.REQUEST);
  }

  /**
   * Create an empty request for file write.
   *
   * @param to shell agent id.
   */
  public ShellPutFileReq(AgentID to) {
    super(to, Performative.REQUEST);
  }

  /**
   * Create request for file write.
   *
   * @param to shell agent id.
   * @param filename name of the file to write.
   * @param contents contents to write to the file (null to delete file).
   */
  public ShellPutFileReq(AgentID to, String filename, byte[] contents) {
    super(to, Performative.REQUEST);
    this.filename = filename;
    this.contents = contents;
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

  /**
   * Get the contents to write to the file.
   *
   * @return the contents to write to the file.
   */
  public byte[] getContents() {
    return contents;
  }

  /**
   * Set the contents to write to the file.
   *
   * @param contents the contents to write to the file (null to delete file).
   */
  public void setContents(byte[] contents) {
    this.contents = contents;
  }

}
