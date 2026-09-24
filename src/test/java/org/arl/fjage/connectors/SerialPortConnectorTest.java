/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.IOException;
import org.junit.Test;

public class SerialPortConnectorTest {

  @Test(expected = IOException.class)
  public void missingPortFailsToOpen() throws IOException {
    new SerialPortConnector("/dev/fjage-no-such-port", 9600, null);
  }

}
