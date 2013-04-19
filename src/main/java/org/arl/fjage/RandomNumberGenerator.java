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

  public static double nextDouble() {
    return getThreadRNG().nextDouble();
  }

  public static double nextDouble(double min, double max) {
    return getThreadRNG().nextDouble()*(max-min) + min;
  }

  public static int nextInt(int n) {
    return getThreadRNG().nextInt(n);
  }

  public static double nextGaussian() {
    return getThreadRNG().nextGaussian();
  }

  public static double nextGaussian(double mu, double sigma) {
    return getThreadRNG().nextGaussian()*sigma + mu;
  }

  public static double nextExp() {
    return -Math.log(getThreadRNG().nextDouble());
  }

  public static double nextExp(double mean) {
    return -Math.log(getThreadRNG().nextDouble())*mean;
  }

}
