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
public class GetFileReq extends Message {

  private static final long serialVersionUID = 1L;

  private String filename = null;
  private long ofs = 0;
  private long len = 0;

  /**
   * Create an empty request for file/directory.
   */
  public GetFileReq() {
    super(Performative.REQUEST);
  }

  /**
   * Create an empty request for file/directory.
   *
   * @param to shell agent id.
   */
  public GetFileReq(AgentID to) {
    super(to, Performative.REQUEST);
  }

  /**
   * Create request for file/directory.
   *
   * @param filename name of the file/directory to read.
   */
  public GetFileReq(String filename) {
    super(Performative.REQUEST);
    this.filename = filename;
  }

  /**
   * Create request for file/directory.
   *
   * @param to shell agent id.
   * @param filename name of the file/directory to read.
   */
  public GetFileReq(AgentID to, String filename) {
    super(to, Performative.REQUEST);
    this.filename = filename;
  }

  /**
   * Create request for partial file.
   *
   * @param filename name of the file/directory to read.
   * @param ofs start location in file (negative for offset relative to end of file).
   * @param len maximum number of bytes to read (0 for unlimited).
   */
  public GetFileReq(String filename, long ofs, long len) {
    super(Performative.REQUEST);
    this.filename = filename;
    this.ofs = ofs;
    this.len = len;
  }

  /**
   * Create request for partial file.
   *
   * @param to shell agent id.
   * @param filename name of the file/directory to read.
   * @param ofs start location in file (negative for offset relative to end of file).
   * @param len maximum number of bytes to read (0 for unlimited).
   */
  public GetFileReq(AgentID to, String filename, long ofs, long len) {
    super(to, Performative.REQUEST);
    this.filename = filename;
    this.ofs = ofs;
    this.len = len;
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
   * Get the start location in file to read from.
   *
   * @return start locaion in file (negative for offset relative to end of file).
   */
  public long getOffset() {
    return ofs;
  }

  /**
   * Set the start location in file to read from.
   *
   * @param ofs start location in file (negative for offset relative to end of file).
   */
  public void setOffset(long ofs) {
    this.ofs = ofs;
  }

  /**
   * Get number of bytes to read from file.
   *
   * @return number of bytes to read, 0 for no limit.
   */
  public long getLength() {
    return len;
  }

  /**
   * Set number of bytes to read from file.
   *
   * @param len number of bytes to read, 0 for no limit.
   */
  public void setLength(long len) {
    this.len = len;
  }

}
