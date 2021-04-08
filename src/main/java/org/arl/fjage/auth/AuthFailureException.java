/******************************************************************************

Copyright (c) 2021, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.auth;

/**
 * Authentication or authorization failure unchecked exception.
 *
 * @author  Mandar Chitre
 */
public class AuthFailureException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AuthFailureException() {
    super();
  }

  public AuthFailureException(String message) {
    super(message);
  }

  @Override
  public String toString() {
    String s = getMessage();
    if (s != null) return s;
    return getClass().getName();
  }

}
