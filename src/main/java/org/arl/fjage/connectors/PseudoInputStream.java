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
    if (!q.write(c)) throw new IOException("Stream is closed");
  }

  /**
   * Write a byte buffer to the stream buffer.
   */
  public void write(byte[] buf) throws IOException {
    if (!q.write(buf)) throw new IOException("Stream is closed");
  }

  @Override
  public int read() {
    return q.read();
  }

  @Override
  public int read(byte[] buf, int ofs, int len) {
    if (ofs == 0 && buf.length == len) return q.read(buf);
    byte[] tmp = new byte[len];
    int n = q.read(tmp);
    System.arraycopy(tmp, 0, buf, ofs, n);
    return n;
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
