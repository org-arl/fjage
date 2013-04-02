/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * Represents any class that can provide a timestamp.
 *
 * @author  Mandar Chitre
 */
public interface TimestampProvider {

  /**
   * Gets the current platform time in milliseconds. For real-time platforms,
   * this time is epoch time. For simulation platforms, this time is simulation
   * time.
   *
   * @return time in milliseconds.
   */
  public long currentTimeMillis();

  /**
   * Gets the current platform time in nanoseconds. For real-time platforms,
   * this time is epoch time. For simulation platforms, this time is simulation
   * time. The time is nanosecond precision but not necessarily nanosecond accuracy.
   * 
   * @return time in nanoseconds.
   */
  public long nanoTime();

}

