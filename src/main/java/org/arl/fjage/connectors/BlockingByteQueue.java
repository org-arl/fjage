/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;

/**
 * Byte queue that allows streaming of byte data.
 */
public class BlockingByteQueue {

  protected final static int BLOCK_SIZE = 16384;

  protected BlockingQueue<byte[]> queue;
  protected byte[] wbuf, rbuf;
  protected int bytes, wlen, rlen, rpos;

  public BlockingByteQueue() {
    queue = new LinkedBlockingQueue<byte[]>();
    wbuf = new byte[BLOCK_SIZE];
    wlen = 0;
    bytes = 0;
    rbuf = wbuf;
    rlen = 0;
    rpos = 0;
  }

  /**
   * Clears the queue.
   */
  public synchronized void clear() {
    queue.clear();
    rbuf = wbuf;
    wlen = 0;
    rlen = 0;
    rpos = 0;
    bytes = 0;
    notify();
  }

  /**
   * Writes a byte to the queue.
   */
  public synchronized void write(int c) {
    if (wlen == BLOCK_SIZE) {
      if (bytes > 0) {
        if (rbuf != wbuf) queue.add(wbuf);
        wbuf = new byte[BLOCK_SIZE];
      }
      wlen = 0;
    }
    wbuf[wlen++] = (byte)c;
    if (rbuf == wbuf) rlen = wlen;
    bytes++;
    notify();
  }

  /**
   * Writes a byte array to the queue.
   */
  public synchronized void write(byte[] buf) {
    bytes += buf.length;
    if (wlen == 0 && buf.length > BLOCK_SIZE) {
      queue.add(buf);
      notify();
      return;
    }
    if (wlen+buf.length < BLOCK_SIZE) {
      System.arraycopy(buf, 0, wbuf, wlen, buf.length);
      wlen += buf.length;
      if (rbuf == wbuf) rlen = wlen;
      notify();
      return;
    }
    int len1 = BLOCK_SIZE - wlen;
    System.arraycopy(buf, 0, wbuf, wlen, len1);
    if (rbuf == wbuf) rlen = BLOCK_SIZE;
    queue.add(wbuf);
    int len2 = buf.length - len1;
    if (len2 > BLOCK_SIZE) {
      wbuf = new byte[len2];
      System.arraycopy(buf, len1, wbuf, 0, len2);
      queue.add(wbuf);
      wbuf = new byte[BLOCK_SIZE];
      wlen = 0;
      notify();
      return;
    }
    wbuf = new byte[BLOCK_SIZE];
    System.arraycopy(buf, len1, wbuf, 0, len2);
    wlen = len2;
    notify();
  }

  /**
   * Reads a byte from the queue. Blocks if no data available.
   *
   * @return byte on success, -1 on failure (interrupt).
   */
  public synchronized int read() {
    try {
      if (bytes == 0) wait();
    } catch (InterruptedException ex) {
      Thread.interrupted();
    }
    if (bytes == 0) return -1;
    if (rpos >= rlen) {
      //queue.remove(rbuf);
      rbuf = queue.poll();
      if (rbuf != null) rlen = rbuf.length;
      else {
        rbuf = wbuf;
        rlen = wlen;
      }
      rpos = 0;
    }
    int c = rbuf[rpos++];
    if (c < 0) c += 256;
    bytes--;
    return c;
  }

  /**
   * Reads a byte array from the queue. Blocks if no data available,
   * but may return a partial buffer if insufficient data available to fill
   * the buffer.
   *
   * @return the number of bytes read.
   */
  public synchronized int read(byte[] buf) {
    for (int i = 0; i < buf.length; i++) {
      if (i > 0 && bytes == 0) return i;
      int c = read();
      if (c < 0) return i;
      buf[i] = (byte)c;
    }
    return buf.length;
  }

  /**
   * Reads all available data from the queue. Blocks if no data available.
   *
   * @return bytes array on success, null on failure (interrupt).
   */
  public synchronized byte[] readAvailable() {
    try {
      if (bytes == 0) wait();
    } catch (InterruptedException ex) {
      Thread.interrupted();
    }
    if (bytes == 0) return null;
    byte[] buf = new byte[bytes];
    read(buf);
    return buf;
  }

  /**
   * Reads data from the queue until a delimiter is encountered. Blocks until
   * delimiter is encountered. The returned data includes the delimiter.
   *
   * @param delimiter delimiter byte.
   * @return bytes array on success, null on failure (interrupt).
   */
  public synchronized byte[] readDelimited(byte delimiter) {
    int c = -1;
    int d = delimiter;
    if (d < 0) d += 256;
    ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes);
    while (c != d) {
      c = read();
      if (c < 0) {
        if (baos.size() == 0) return null;
        break;
      }
      baos.write(c);
    }
    return baos.toByteArray();
  }

  /**
   * Gets the number of bytes available in the buffer.
   */
  public synchronized int available() {
    return bytes;
  }

}
