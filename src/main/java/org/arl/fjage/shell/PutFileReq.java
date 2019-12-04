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
public class PutFileReq extends Message {

  private static final long serialVersionUID = 1L;

  private String filename = null;
  private byte[] contents = null;
  private long ofs = 0;

  /**
   * Create an empty request for file write.
   */
  public PutFileReq() {
    super(Performative.REQUEST);
  }

  /**
   * Create an empty request for file write.
   *
   * @param to shell agent id.
   */
  public PutFileReq(AgentID to) {
    super(to, Performative.REQUEST);
  }

  /**
   * Create request for file write.
   *
   * @param filename name of the file to write.
   * @param contents contents to write to the file (null to delete file).
   */
  public PutFileReq(String filename, byte[] contents) {
   this(filename, contents, 0);
  }

  /**
   * Create request for file write.
   *
   * @param filename name of the file to write.
   * @param contents contents to write to the file (null to delete file).
   * @param ofs offset within the file to write the contents to
   */
  public PutFileReq(String filename, byte[] contents, long ofs) {
    super(Performative.REQUEST);
    this.filename = filename;
    this.contents = contents;
    this.ofs = ofs;
  }

  /**
   * Create request for file write.
   *
   * @param to shell agent id.
   * @param filename name of the file to write.
   * @param contents contents to write to the file (null to delete file).
   */
  public PutFileReq(AgentID to, String filename, byte[] contents) {
    this(to, filename, contents, 0);
  }

  /**
   * Create request for file write.
   *
   * @param to shell agent id.
   * @param filename name of the file to write.
   * @param contents contents to write to the file (null to delete file).
   * @param ofs offset within the file to write the contents to.
   */
  public PutFileReq(AgentID to, String filename, byte[] contents, long ofs) {
    super(to, Performative.REQUEST);
    this.filename = filename;
    this.contents = contents;
    this.ofs = ofs;
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

  /**
   * Get the start location in file to write to.
   *
   * @return start locaion in file (negative for offset relative to end of file).
   */
  public long getOffset() {
    return ofs;
  }

  /**
   * Set the start location in file to write from.
   *
   * @param ofs start location in file (negative for offset relative to end of file).
   */
  public void setOffset(long ofs) {
    this.ofs = ofs;
  }

}
