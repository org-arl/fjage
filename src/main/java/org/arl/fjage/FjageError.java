/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * Java agent framework error.
 *
 * @author  Mandar Chitre
 */
public class FjageError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public FjageError(String message) {
    super(message);
  }

}
