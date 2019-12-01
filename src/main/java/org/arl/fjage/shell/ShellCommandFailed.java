/******************************************************************************

Copyright (c) 2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.FjageException;

/**
 * Unchecked exception to be thrown by shell commands to indicate
 * graceful command failure.
 *
 * @author  Mandar Chitre
 */
public class ShellCommandFailed extends FjageException {

  private static final long serialVersionUID = 1L;

  public ShellCommandFailed(String message) {
    super(message);
  }

}
