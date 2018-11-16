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
 * Response to a ShellGetFileReq, with the contents of the file or the directory.
 */
public class ShellGetFileRsp extends Message {

  private static final long serialVersionUID = 1L;

  private String filename;
  private byte[] contents = null;
  private boolean dir = false;

  /**
   * Create a response to the ShellGetFileReq.
   *
   * @param inReplyTo message to which this is a response.
   */
  public ShellGetFileRsp(ShellGetFileReq inReplyTo) {
    super(inReplyTo, Performative.INFORM);
    this.filename = inReplyTo.getFilename();
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

  /**
   * Get the contents of the file.
   *
   * @return the contents of the file.
   */
  public byte[] getContents() {
    return contents;
  }

  /**
   * Set the contents of the file.
   *
   * @param contents the contents of the file (null to delete file).
   */
  public void setContents(byte[] contents) {
    this.contents = contents;
  }

  /**
   * Checks if the file being returned is a directory.
   *
   * @return true for directory, false for ordinary file.
   */
  public boolean isDirectory() {
    return dir;
  }

  /**
   * Marks the file being returned as an ordinary file or directory.
   *
   * @param dir true for directory, false for ordinary file.
   */
  public void setDirectory(boolean dir) {
    this.dir = dir;
  }

}
