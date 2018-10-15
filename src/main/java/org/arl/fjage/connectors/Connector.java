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
public interface Connector {

  /**
   * Get the input stream to read data over.
   */
  public InputStream getInputStream();

  /**
   * Get the output stream to write data to.
   */
  public OutputStream getOutputStream();

}
