/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.Message;
import org.arl.fjage.Performative;

/**
 * Response to a GetFileReq, with the contents of the file or the directory.
 */
public class GetFileRsp extends Message {

  private static final long serialVersionUID = 1L;

  private String filename;
  private long offset = 0;
  private byte[] contents;
  private boolean directory = false;

  /**
   * Create an empty response.
   */
  public GetFileRsp() {
    super(Performative.INFORM);
  }

    /**
   * Create a response to the GetFileReq.
   *
   * @param inReplyTo message to which this is a response.
   */
  public GetFileRsp(GetFileReq inReplyTo) {
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
   * Get the contents of the file. For directories, the content consists of a
   * list of files (one file per line). Each line starts with the filename
   * (with a trailing "/" if it is a directory), "\t", file size in bytes,
   * "\t", and last modification date.
   *
   * @return the contents of the file.
   */
  public byte[] getContents() {
    return contents;
  }

  /**
   * Set the contents of the file.
   *
   * @param contents the contents of the file.
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
    return directory;
  }

  /**
   * Checks if the file being returned is a directory.
   *
   * @return true for directory, false for ordinary file.
   */
  public boolean getDirectory() {
    return directory;
  }

  /**
   * Marks the file being returned as an ordinary file or directory.
   *
   * @param dir true for directory, false for ordinary file.
   */
  public void setDirectory(boolean dir) {
    this.directory = dir;
  }

  /**
   * Get the start location in file.
   *
   * @return start locaion in file.
   */
  public long getOffset() {
    return offset;
  }

  /**
   * Set the start location in file.
   *
   * @param ofs start location in file.
   */
  public void setOffset(long ofs) {
    this.offset = ofs;
  }

}
