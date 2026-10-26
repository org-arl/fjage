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
   * <p>
   * On real-time platforms, this clock may be stepped forwards or backwards when
   * the system clock is adjusted. Use it for timestamps, and use {@link #nanoTime()}
   * to measure elapsed time or to compute deadlines.
   *
   * @return time in milliseconds.
   */
  long currentTimeMillis();

  /**
   * Gets the current platform time in nanoseconds, for measuring elapsed time.
   * For real-time platforms, this time is monotonic and has an arbitrary origin,
   * so only differences between two values are meaningful. For simulation
   * platforms, this time is simulation time. The time is nanosecond precision
   * but not necessarily nanosecond accuracy.
   * <p>
   * Timeouts and timed behaviors measure elapsed time using this clock, so it must
   * advance at the same rate as delays given to {@link Platform#schedule}. Compare
   * values by subtraction (e.g. {@code t1 - t0 > 0}), as they may overflow.
   *
   * @return time in nanoseconds.
   */
  long nanoTime();

}

