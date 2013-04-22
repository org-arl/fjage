/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.*;

/**
 * Utility class for random number generation in simulations.
 *
 * This should be used in preference to java.util.Random to ensure that
 * simulations are repeatable by setting a random number seed.
 */
public class RandomNumberGenerator {

  private static Random root = new Random();
  private static Map<Thread,Random> rng = Collections.synchronizedMap(new HashMap<Thread,Random>());

  /**
   * Set root random number generator seed. This should be set once, at the start of the simulation
   * if repeatable random number sequences are desired.
   *
   * @param seed random number seed.
   */
  public static void setSeed(long seed) {
    root.setSeed(seed);
    rng.clear();
  }

  private static Random getThreadRNG() {
    Thread tid = Thread.currentThread();
    Random r = rng.get(tid);
    if (r == null) {
      r = new Random();
      r.setSeed(root.nextInt());
      rng.put(tid, r);
    }
    return r;
  }

  // random number generation functions

  /**
   * Generate a random double between 0 and 1.
   */
  public static double nextDouble() {
    return getThreadRNG().nextDouble();
  }

  /**
   * Generate a random double.
   *
   * @param min minimum value to generate.
   * @param max maximum value to generate.
   */
  public static double nextDouble(double min, double max) {
    return getThreadRNG().nextDouble()*(max-min) + min;
  }

  /**
   * Generate a random integer between 0 and n-1.
   *
   * @param n maximum integer.
   */
  public static int nextInt(int n) {
    return getThreadRNG().nextInt(n);
  }

  /**
   * Generate a Gaussian random number with mean 0 and standard deviation of 1.
   */
  public static double nextGaussian() {
    return getThreadRNG().nextGaussian();
  }

  /**
   * Generate a Gaussian random number.
   *
   * @param mu mean of the distribution.
   * @param sigma standard deviation of the distribution.
   */
  public static double nextGaussian(double mu, double sigma) {
    return getThreadRNG().nextGaussian()*sigma + mu;
  }

  /**
   * Generate an exponentially distributed random number with mean 1.
   */
  public static double nextExp() {
    return -Math.log(getThreadRNG().nextDouble());
  }

  /**
   * Generate an exponentially distributed random number.
   *
   * @param mean mean of the distribution.
   */
  public static double nextExp(double mean) {
    return -Math.log(getThreadRNG().nextDouble())*mean;
  }

}
