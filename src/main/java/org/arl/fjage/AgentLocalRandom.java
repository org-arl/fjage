/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.*;
import java.util.logging.Logger;

/**
 * Utility class for random number generation in simulations.
 *
 * This should be used in preference to java.util.Random to ensure that
 * simulations are repeatable by setting a random number seed.
 */
public class AgentLocalRandom extends Random {

  //// static stuff

  private static Logger log = Logger.getLogger(AgentLocalRandom.class.getName());

  private static AgentLocalRandom root = new AgentLocalRandom();
  private static Map<Object,AgentLocalRandom> rng = Collections.synchronizedMap(new HashMap<Object,AgentLocalRandom>());

  /**
   * Returns the current agent's AgentLocalRandom.
   *
   * @return current agent's random number generator.
   */
  public static AgentLocalRandom current() {
    Thread tid = Thread.currentThread();
    AgentLocalRandom r = rng.get(tid);
    if (r != null) return r;
    return root;
  }

  //// private operations for agent/container to use

  static void bind(Agent agent) {
    AgentLocalRandom r = new AgentLocalRandom();
    long seed = root.nextLong();
    r.setSeed(seed);
    rng.put(agent, r);
  }

  static void bind(Agent agent, Thread tid) {
    AgentLocalRandom r = rng.get(agent);
    if (r != null) {
      rng.put(tid, r);
      rng.remove(agent);
    }
  }

  static void unbind(Thread tid) {
    rng.remove(tid);
  }

  static void unbind() {
    unbind(Thread.currentThread());
  }

  /**
   * Sets root random number generator seed. This should be set once, at the start of the simulation
   * if repeatable random number sequences are desired.
   *
   * @param seed random number seed.
   */
  public static void setRootSeed(long seed) {
    root.setSeed(seed);
    rng.clear();
  }

  //// instance methods

  /**
   * Generates a random double.
   *
   * @param min minimum value to generate.
   * @param max maximum value to generate.
   * @return random number.
   */
  public double nextDouble(double min, double max) {
    return nextDouble()*(max-min) + min;
  }

  /**
   * Generates an exponentially distributed random number with mean 1/lambda.
   *
   * @param lambda rate parameter.
   * @return random number.
   */
  public double nextExp(double lambda) {
    return nextExp()/lambda;
  }

  /**
   * Generates an exponentially distributed random number with unit mean.
   *
   * @return random number.
   */
  public double nextExp() {
    double r = -Math.log(nextDouble());
    return r;
  }

  /**
   * Generates a Gaussian distributed random number.
   *
   * @param mu mean of the distribution.
   * @param sigma2 variance of the distribution.
   * @return random number.
   */
  public double nextGaussian(double mu, double sigma2) {
    return nextGaussian()*Math.sqrt(sigma2) + mu;
  }

  /**
   * Generates a Rayleigh distributed random number.
   *
   * @param sigma scale parameter.
   * @return random number.
   */
  public double nextRayleigh(double sigma) {
    return Math.sqrt(-2*Math.log(nextDouble()))*sigma;
  }

  /**
   * Generates a Rician distributed random number.
   *
   * @param nu location parameter.
   * @param sigma scale parameter.
   * @return random number.
   */
  public double nextRician(double nu, double sigma) {
    return Math.sqrt(Math.pow(nextGaussian()*sigma+nu,2) + Math.pow(nextGaussian()*sigma,2));
  }

}
