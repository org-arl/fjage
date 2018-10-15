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

  private byte[] buf;
  private int head, tail, cnt;

  /**
   * Create an input stream with a backing buffer of 1024 bytes.
   */
  public PseudoInputStream() {
    buf = new byte[1024];
    head = tail = cnt = 0;
  }

  /**
   * Create an input stream with a specified backing buffer size.
   */
  public PseudoInputStream(int bufsize) {
    buf = new byte[bufsize];
    head = tail = cnt = 0;
  }

  /**
   * Clear the stream buffer.
   */
  public synchronized void clear() {
    head = tail = cnt = 0;
  }

  /**
   * Write a byte to the stream buffer.
   */
  public synchronized void write(int c) throws IOException {
    if (buf == null) throw new IOException("Stream is closed");
    if (cnt == buf.length) throw new IOException("Stream buffer full");
    buf[head] = (byte)c;
    if (++head >= buf.length) head = 0;
    cnt++;
    notify();
  }

  @Override
  public synchronized int read() {
    if (buf == null) return -1;
    try {
      if (cnt == 0) wait();
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

  @Override
  public synchronized int read(byte[] buf, int ofs, int len) {
    if (this.buf == null) return -1;
    try {
      if (cnt == 0) wait();
    } catch (InterruptedException ex) {
      Thread.interrupted();
    }
    if (cnt == 0) return -1;
    int i = 0;
    for (i = 0; i < len && cnt > 0; i++) {
      buf[i] = this.buf[tail];
      if (++tail >= buf.length) tail = 0;
      cnt--;
    }
    return i;
  }

  @Override
  public int read(byte[] buf) {
    return read(buf, 0, buf.length);
  }

  @Override
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
