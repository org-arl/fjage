/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;
import java.security.AccessController;
import java.security.PrivilegedAction;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Serial port connector.
 */
public class SerialPortConnector implements Connector {

  protected SerialPort com;

  /**
   * Open a serial port.
   *
   * @param devname device name of the serial port.
   * @param baud baud rate for the serial port.
   * @param settings serial port settings (null for defaults, or "N81" for no parity, 8 bits, 1 stop bit).
   */
  public SerialPortConnector(String devname, int baud, String settings) throws IOException {
    if (settings != null && settings != "N81") throw new IOException("Bad serial port settings");
    com = AccessController.doPrivileged(new PrivilegedAction<SerialPort>() {
      public SerialPort run() {
        SerialPort c = SerialPort.getCommPort(devname);
        c.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        c.openPort();
        c.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
        return c;
      }
    });
  }

  /**
   * Get the underlying serial port.
   */
  public SerialPort getSerialPort() {
    return com;
  }

  @Override
  public String getName() {
    if (com == null) return "serial://[closed]";
    return "serial://"+com.getSystemPortName();
  }

  @Override
  public InputStream getInputStream() {
    if (com == null) return null;
    return com.getInputStream();
  }

  @Override
  public OutputStream getOutputStream() {
    if (com == null) return null;
    return com.getOutputStream();
  }

  @Override
  public void setConnectionListener(ConnectionListener listener) {
    listener.connected(this);
  }

  @Override
  public void close() {
    if (com == null) return;
    com.closePort();
    com = null;
  }

  @Override
  public boolean isReliable() {
    return false;
  }

  @Override
  public boolean waitOutputCompletion(long timeout) {
    long t = System.currentTimeMillis() + timeout;
    while (com.bytesAwaitingWrite() > 0) {
      if (System.currentTimeMillis() > t) return false;
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return true;
  }

  @Override
  public String[] connections() {
    return new String[] { "?" };
  }

  @Override
  public String toString() {
    return getName();
  }

}
