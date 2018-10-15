/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;
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
    SerialPort com = SerialPort.getCommPort(devname);
    com.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
    com.openPort();
  }

  @Override
  public String getName() {
    if (com == null) return "serial:[closed]";
    return "serial:["+com.getDescriptivePortName​()+"]";
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
  public void close() {
    if (com == null) return;
    com.closePort();
    com = null;
  }

  @Override
  public String toString() {
    return getName();
  }

}
