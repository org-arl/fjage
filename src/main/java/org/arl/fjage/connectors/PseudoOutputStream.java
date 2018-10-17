/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;

/**
 * An output stream backed by a byte buffer that can be read from.
 */
public class PseudoOutputStream extends OutputStream {

  protected BlockingByteQueue q = new BlockingByteQueue();

  /**
   * Clear the stream buffer.
   */
  public void clear() {
    if (q != null) q.clear();
  }

  @Override
  public void write(int c) throws IOException {
    if (q == null) throw new IOException("Stream is closed");
    q.write(c);
  }

  @Override
  public void write(byte[] buf) throws IOException {
    if (q == null) throw new IOException("Stream is closed");
    q.write(buf);
  }

  @Override
  public void write(byte[] buf, int ofs, int len) throws IOException {
    if (q == null) throw new IOException("Stream is closed");
    if (ofs == 0 && buf.length == len) q.write(buf);
    else {
      byte[] tmp = new byte[len];
      System.arraycopy(buf, ofs, tmp, 0, len);
      q.write(tmp);
    }
  }

  /**
   * Read a byte from the stream buffer. Blocks if none available.
   *
   * @return byte read, or -1 on error (if stream is closed or interrupt).
   */
  public int read() {
    if (q == null) return -1;
    return q.read();
  }

  /**
   * Read a byte buffer from the stream buffer. Blocks if no data available.
   * May return less data than the buffer size.
   *
   * @return number of bytes read, or -1 on error (if stream is closed or interrupt).
   */
  public int read(byte[] buf) {
    if (q == null) return -1;
    return q.read(buf);
  }

  /**
   * Reads all available data from the stream buffer. Blocks if no data available.
   *
   * @return bytes array on success, null on failure (if stream is closed or interrupt).
   */
  public byte[] readAvailable() {
    if (q == null) return null;
    return q.readAvailable();
  }

  /**
   * Reads a line of text from the stream buffer. Blocks if no data available.
   *
   * @return text string on success, null on failure (if stream is closed or interrupt).
   */
  public String readLine() {
    if (q == null) return null;
    return new String(q.readDelimited((byte)10));
  }

  /**
   * Get the number of bytes available in stream to read.
   *
   * @return number of bytes available, -1 on error (if stream is closed).
   */
  public int available() {
    if (q == null) return -1;
    return q.available();
  }

  @Override
  public void close() {
    if (q == null) return;
    q.clear();
    q = null;
  }

}
