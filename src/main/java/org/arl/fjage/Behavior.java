/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Base class for agent behaviors. Commonly used behaviors are provided by subclasses:
 * <ul>
 * <li>{@link CyclicBehavior} for continuous behaviors
 * <li>{@link OneShotBehavior} for one time behaviors
 * <li>{@link WakerBehavior} for behaviors to be executed once after a specified period
 * <li>{@link TickerBehavior} for behaviors to be executed periodically
 * <li>{@link PoissonBehavior} for simulating Poisson arrival processes
 * <li>{@link MessageBehavior} for behaviors to process incoming messages
 * <li>{@link TestBehavior} for unit testing
 * <li>{@link FSMBehavior} for implementing finite state machines
 * </ul>
 * Additionally, developers may create their own behaviors by extending this class or one
 * of its subclasses.
 *
 * @author  Mandar Chitre
 */
public abstract class Behavior {

  ////////////// Attributes accessible to all behaviors

  /**
   * Agent owning this behavior. This attribute should not be modified
   * by the behavior.
   */
  protected Agent agent;

  /**
   * Logger for the behavior to log messages to. This logger defaults to the same
   * logger as the owning agent's logger.
   */
  protected Logger log;

  ////////////// Private attributes

  private boolean blocked = false;

  ////////////// Methods for behaviors to override

  /**
   * This method is called when a behavior is added to an agent. A behavior may
   * customize this by overriding it.
   */
  public void onStart() {
    // do nothing
  }

  /**
   * This method is called when a behavior is completed. A behavior may
   * customize this by overriding it.
   */
  public void onEnd() {
    // do nothing
  }

  /**
   * This method is repeatedly called during the life of a behavior. A behavior
   * must override this to provide appropriate functionality.
   */
  public abstract void action();

  /**
   * This method should return true if the behavior is completed, false otherwise.
   * A behavior must override this.
   *
   * @return true if behavior is completed, false otherwise.
   */
  public abstract boolean done();

  ////////////// Interface methods

  /**
   * Blocks the behavior. Blocking a behavior simply puts it in a blocked state as
   * soon as the current {@link #action()} is completed. In a blocked state, the
   * {@link #action()} method is no longer called until the behavior is unblocked by
   * a call to {@link #restart()}. Unlike {@link Agent#block()}, this method is non-blocking.
   */
  public void block() {
    blocked = true;
  }

  /**
   * Blocks the behavior for a specified period of time. Blocking a behavior simply puts
   * it in a blocked state as soon as the current {@link #action()} is completed. In a
   * blocked state, the {@link #action()} method is no longer called until the behavior is
   * unblocked after the specified period of time or by a call to {@link #restart()}.
   * Unlike {@link Agent#block()}, this method is non-blocking.
   *
   * @param millis number of milliseconds before the behavior should be unblocked.
   */
  public void block(long millis) {
    blocked = true;
    agent.getPlatform().schedule(new TimerTask() {
      @Override
      public void run() {
        restart();
      }
    }, millis);
  }

  /**
   * Unblocks the behavior if it was blocked.
   *
   * @see #block()
   */
  public void restart() {
    blocked = false;
    if (agent != null) agent.wake();
  }

  /**
   * Returns true if the behavior is blocked, false otherwise.
   *
   * @return true if the behavior is blocked, false otherwise.
   */
  public boolean isBlocked() {
    return blocked;
  }

  /**
   * Resets a behavior to its initial state.
   */
  public void reset() {
    blocked = false;
  }

  /**
   * Convenience method to create an owned agent id for the named agent.
   * 
   * @return agent id for the named agent.
   */
  public AgentID agent(String name) {
    return new AgentID(name, agent);
  }
  
  /**
   * Convenience method to find an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param service the named service of interest.
   * @return an agent id for an agent that provides the service.
   */
  public AgentID agentForService(String service) {
    return agent.agentForService(service);
  }
  
  /**
   * Convenience method to find an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param service the named service of interest.
   * @return an agent id for an agent that provides the service.
   */
  public AgentID agentForService(Enum<?> service) {
    return agent.agentForService(service);
  }
  
  /**
   * Convenience method to find all agents that provides a named service.
   *
   * @param service the named service of interest.
   * @return an array of agent ids representing all agent that provide the service.
   */
  public AgentID[] agentsForService(String service) {
    return agent.agentsForService(service);
  }

  /**
   * Convenience method to find all agents that provides a named service.
   *
   * @param service the named service of interest.
   * @return an array of agent ids representing all agent that provide the service.
   */
  public AgentID[] agentsForService(Enum<?> service) {
    return agent.agentsForService(service);
  }

  /**
   * Log a message at an INFO level.
   * 
   * @param msg message to log.
   */
  public void println(String msg) {
    log.info(msg);
  }

  ////////////// Private methods

  /**
   * Sets the owner of the agent. Called by the agent when the behavior is added.
   */
  void setOwner(Agent agent) {
    this.agent = agent;
    this.log = (agent == null) ? null : agent.log;
  }

}

