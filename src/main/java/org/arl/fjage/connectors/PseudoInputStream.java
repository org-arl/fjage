/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;

/**
 * An input stream backed by a byte buffer that can be dynamically written to.
 */
public class PseudoInputStream extends InputStream {

  protected final BlockingByteQueue q = new BlockingByteQueue();

  /**
   * Clear the stream buffer.
   */
  public void clear() {
    q.clear();
  }

  /**
   * Write a byte to the stream buffer.
   */
  public void write(int c) throws IOException {
    if (!q.write(c)) throw new IOException("Stream is closed or buffer limit exceeded");
  }

  /**
   * Write a byte buffer to the stream buffer.
   */
  public void write(byte[] buf) throws IOException {
    if (!q.write(buf)) throw new IOException("Stream is closed or buffer limit exceeded");
  }

  /** Writes a slice atomically, failing if the stream is closed or its buffer is full. */
  public void write(byte[] buf, int offset, int length) throws IOException {
    if (!q.write(buf, offset, length)) throw new IOException("Stream is closed or buffer limit exceeded");
  }

  @Override
  public int read() {
    return q.read();
  }

  @Override
  public int read(byte[] buf, int ofs, int len) {
    return q.read(buf, ofs, len);
  }

  @Override
  public int read(byte[] buf) {
    return q.read(buf);
  }

  @Override
  public int available() {
    return q.available();
  }

  @Override
  public void close() {
    q.close();
  }

}
