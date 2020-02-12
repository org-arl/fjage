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
 * Finite state machine (FSM) behavior. This behavior implements a simple FSM
 * with cyclic behavior-like states.
 * <p>
 * An example FSM is shown below:
 * <pre>
 * FSMBehavior fsm = new FSMBehavior();
 * fsm.add(new FSMBehavior.State("state1") {
 *   public void action() {
 *     log.info("In State #1");
 *     setNextState("state2");
 *   }
 * });
 * fsm.add(new FSMBehavior.State("state2") {
 *   public void action() {
 *     log.info("In State #2");
 *     terminate();
 *   }
 * });
 * agent.add(fsm);
 * </pre>
 *
 * @author  Mandar Chitre
 */
public class FSMBehavior extends Behavior {

  /////////////// Special states

  private final static State INIT = new State("#INIT#");
  private final static State FINAL = new State("#FINAL#");
  private final static State REENTER = new State("#REENTER#");

  private Map<Object,State> states = new HashMap<Object,State>();
  private State initial = FINAL;
  private State state = INIT;
  private State next = INIT;
  private State old = INIT;

  ///////////// Public interface

  /**
   * Creates a FSM behavior.
   */
  public FSMBehavior() {
    super();
  }

  /**
   * Creates a FSM behavior.
   *
   * @param states States to be added to the FSM behavior.
   */
  public FSMBehavior(FSMBehavior.State... states) {
    for (final FSMBehavior.State state : states) {
      add(state);
    }
  }

  //////////// Overridden methods from superclass

  @Override
  public void action() {
    if (state == INIT) state = next = initial;
    if (old == state) state.action();
    else {
      if (state == REENTER) state = next = old;
      old.onExit();
      state.onEnter();
    }
    old = state;
    state = next;
    if (old != state) restart();
  }

  @Override
  public boolean done() {
    return state == FINAL;
  }

  /**
   * Resets the FSM. If the FSM is running, the current state is discarded
   * without {@link State#onExit()} being called. The states added to the FSM
   * are retained.
   *
   * @see org.arl.fjage.Behavior#reset()
   */
  @Override
  public void reset() {
    state = next = INIT;
  }

  @Override
  void setOwner(Agent agent) {
    super.setOwner(agent);
    for (State s: states.values())
      s.log = log;
  }

  //////////// Interface methods

  /**
   * Clears the FSM states. If the FSM is running, the current state is discarded
   * without {@link State#onExit()} being called.
   */
  public void clear() {
    reset();
    states.clear();
    initial = FINAL;
  }

  /**
   * Adds a state to the FSM. The name of the state is taken from the state object.
   * The first state added automatically becomes the initial state that the
   * FSM starts in.
   *
   * @param state state behavior.
   */
  public void add(State state) {
    if (states.containsKey(state.name)) throw new FjageException("Duplicate state name: "+state.name);
    states.put(state.getName(), state);
    state.fsm = this;
    state.log = log;
    if (initial == FINAL) initial = state;
  }

  /**
   * Sets the initial state to start FSM in. If this method is not called, the
   * first state added to the FSM is assumed to be the initial state.
   *
   * @param name initial state object or name.
   */
  public void setInitialState(Object name) {
    State state = (name instanceof State) ? (State)name : states.get(name);
    if (state == null) throw new FjageException("Unknown state: "+name);
    initial = state;
  }

  /**
   * Sets the next state of the FSM.
   *
   * @param name name of the state.
   */
  public void setNextState(Object name) {
    State state = (name instanceof State) ? (State)name : states.get(name);
    if (state == null) throw new FjageException("Unknown state: "+name);
    next = state;
    restart();
  }

  /**
   * Request re-entry of the current state in the FSM. This causes the state
   * to be exited, and re-entered.
   */
  public void reenterState() {
    next = REENTER;
    restart();
  }

  /**
   * Gets the current state name.
   *
   * @return current state name.
   */
  public Object getCurrentState() {
    return state.getName();
  }

  /**
   * Terminates the FSM by setting the next state to be FINAL.
   */
  public void terminate() {
    setNextState(FINAL);
  }

  /**
   * Trigger an event on the FSM.
   *
   * @param event an object naming the event.
   */
  public void trigger(Object event) {
    state.onEvent(event, null);
  }

  /**
   * Trigger an event on the FSM.
   *
   * @param event an object naming the event.
   * @param eventInfo an object providing extra information on the event.
   */
  public void trigger(Object event, Object eventInfo) {
    state.onEvent(event, eventInfo);
  }

  /////////// Public class: FSM State

  /**
   * FSM state behavior.
   *
   * @author  Mandar Chitre
   */
  public static class State {

    /////////// Private attributes

    private Object name;

    /**
     * Parent FSM.
     */
    protected FSMBehavior fsm = null;

    /**
     * Logger for the behavior to log messages to. This logger defaults to the same
     * logger as the owning agent's logger.
     */
    protected Logger log = null;

    private Runnable runnable;

    /////////// Constructor

    /**
     * Creates a named state. The name may later be reassigned when the state is
     * added to the FSM.
     *
     * @param name name of the state.
     */
    public State(Object name) {
      this.name = name;
    }

    /**
     * Creates a named state. The name may later be reassigned when the state is
     * added to the FSM.
     *
     * @param name name of the state.
     * @param runnable Runnable to run.
     */
    public State(Object name, Runnable runnable) {
      this(name);
      this.runnable = runnable;
    }

    /////////// Methods for subclass to override

    /**
     * This method is repeatedly called when the state is active. The default
     * implementation simply blocks the behavior unless a runnable was provided during instantiation.
     */
    public void action() {
      if (runnable != null) {
        runnable.run();
      } else {
        block();
      }
    }

    /**
     * This method is called when the state is entered.
     */
    public void onEnter() {
      // do nothing
    }

    /**
     * This method is called when the state is exited.
     */
    public void onExit() {
      // do nothing
    }

    /**
     * This method is called when an event is triggered.
     *
     * @param event an object naming the event.
     * @param eventInfo an object providing extra information on the event.
     */
    public void onEvent(Object event, Object eventInfo) {
      // do nothing
    }

    /////////// Interface methods

    /**
     * Gets the name of the FSM state.
     *
     * @return name of the state.
     */
    public Object getName() {
      return name;
    }

    /**
     * Returns a string representation of the state.
     *
     * @see java.lang.Object#toString()
     */
    public String toString() {
      return name.toString();
    }

    /////////// Methods delegated to parent FSM

    /**
     * Changes the FSM state to the specified state.
     *
     * @param name name of the state to change to.
     */
    protected void setNextState(Object name) {
      if (fsm != null) fsm.setNextState(name);
    }

    /**
     * Terminates the FSM.
     */
    protected void terminate() {
      if (fsm != null) fsm.terminate();
    }

    /**
     * Blocks the behavior.
     * @see org.arl.fjage.Behavior#block()
     */
    protected void block() {
      if (fsm != null) fsm.block();
    }

    /**
     * Blocks the behavior.
     * @see org.arl.fjage.Behavior#block(long)
     */
    protected void block(long millis) {
      if (fsm != null) fsm.block(millis);
    }

    /**
     * Restarts the behavior.
     * @see org.arl.fjage.Behavior#restart()
     */
    protected void restart() {
      if (fsm != null) fsm.restart();
    }

    /**
     * Checks if the behavior is blocked.
     * @see org.arl.fjage.Behavior#isBlocked()
     */
    protected boolean isBlocked() {
      if (fsm == null) return false;
      return fsm.isBlocked();
    }
  }
}
