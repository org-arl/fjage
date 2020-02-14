/******************************************************************************

Copyright (c) 2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.param;

/**
 * Interface to define parameters.
 */
public interface Parameter {

  /**
   * Gets the ordinal, provided by {@link Enum} automatically.
   *
   * @return ordinal value
   */
  public int ordinal();

}
