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

  private byte[] buf;
  private int head, tail, cnt;

  /**
   * Create an output stream with a backing buffer of 1024 bytes.
   */
  public PseudoOutputStream() {
    buf = new byte[1024];
    head = tail = cnt = 0;
  }

  /**
   * Create an output stream with a specified backing buffer size.
   */
  public PseudoOutputStream(int bufsize) {
    buf = new byte[bufsize];
    head = tail = cnt = 0;
  }

  /**
   * Clear the stream buffer.
   */
  public synchronized void clear() {
    head = tail = cnt = 0;
  }

  @Override
  public synchronized void write(int c) throws IOException {
    if (buf == null) throw new IOException("Stream is closed");
    if (cnt == buf.length) throw new IOException("Stream buffer full");
    buf[head] = (byte)c;
    if (++head >= buf.length) head = 0;
    cnt++;
    notify();
  }

  /**
   * Read a byte from the stream buffer. Blocks if none available.
   *
   * @return byte read, or -1 on error (if stream is closed).
   */
  public synchronized int read() {
    if (buf == null) return -1;
    try {
      if (head == tail) wait();
    } catch (InterruptedException ex) {
      Thread.interrupted();
    }
    if (cnt == 0) return -1;
    int c = buf[tail];
    if (c < 0) c += 256;
    if (++tail >= buf.length) tail = 0;
    cnt--;
    return c;
  }

  /**
   * Get the number of bytes available in stream to read.
   */
  public synchronized int available() {
    return cnt;
  }

  @Override
  public synchronized void close() {
    buf = null;
    head = tail = cnt = 0;
    notify();
  }

}
