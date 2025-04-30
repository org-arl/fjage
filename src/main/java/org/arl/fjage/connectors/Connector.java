/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;

/**
 * Any data transport service implemets this interface.
 */
public interface Connector extends Closeable {

  /**
   * Get a string representation of the connection.
   */
  public String getName();

  /**
   * Get the input stream to read data over.
   */
  public InputStream getInputStream();

  /**
   * Get the output stream to write data to.
   */
  public OutputStream getOutputStream();

  /**
   * Check if a connection is relaible. A reliable connection
   * throws an exception if data written to the output stream cannot
   * be delivered.
   */
  public boolean isReliable();

  /**
   * Wait until the output buffer is empty.
   *
   * @param timeout timeout in milliseconds.
   * @return true if output buffer empty, false on timeout or error.
   */
  public boolean waitOutputCompletion(long timeout);

  /**
   * Set a connection state listener.
   *
   * @param listener listener to call for connection/disconnection,
   *                 or null to disable listener.
   */
  public void setConnectionListener(ConnectionListener listener);
  
  /**
   * Gets a list of all known active connections
   *
   * @return an array of all known active connections
   */
  public String[] connections();

  /**
   * Close the connection.
   */
  @Override
  public void close();

}
