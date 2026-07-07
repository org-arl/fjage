package org.arl.fjage.groovy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.arl.fjage.Agent;
import org.arl.fjage.FSMBehavior;
import org.arl.fjage.WakerBehavior;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Finite state machine (FSM) builder.
 * <p>
 * While fjåge provides a {@link org.arl.fjage.FSMBehavior} class to help with making FSMs,
 * this class adds some Groovy functionality to make it simpler to describe FSMs in Groovy.
 * <p>
 * The usage of this class is best described through an example FSM:
 * <pre>
 * enum State { TICK, TOCK }
 * enum Event { RESET }
 *
 * FSMBehavior fsm = FSMBuilder.build {
 *
 *   long count = 0
 *
 *   state(State.TICK) {
 *     onEnter {
 *       println 'TICK'
 *     }
 *     after(1) {                     // every time we enter the TICK state,
 *       setNextState(State.TOCK)     //   switch to TOCK after 1 second
 *     }
 *     action {
 *       println "CLOCK says $count seconds"
 *       block()
 *     }
 *     onEvent(Event.RESET) {         // reset count if RESET trigger received
 *       count = 0
 *     }
 *   }
 *
 *   state(State.TOCK) {
 *     onEnter {
 *       println 'TOCK'
 *       after(1) {                  // in this form, the delay is recomputed every
 *         setNextState(State.TICK)  //   time the TOCK state is entered
 *       }
 *     }
 *     onEvent(Event.RESET) {         // reset count if RESET trigger received
 *       count = 0                    //   and also go back to TICK state
 *       setNextState(State.TICK)
 *     }
 *     onExit {                       // increment count every time we leave
 *       count++                      //   the TICK state
 *     }
 *   }
 *
 * }
 * </pre>
 * The DSL may also be used in statically compiled ({@code @CompileStatic}) Groovy code.
 * The delay passed to {@code after()} is a number of seconds.
 * <p>
 * This class is implemented in Java (rather than Groovy) intentionally: the Groovy compiler
 * adds synthetic {@code propertyMissing}/{@code methodMissing} bridge methods to nested
 * Groovy classes, and the static type checker then resolves any unqualified property in a
 * delegating closure against those bridges (as {@code Object}), breaking access to fields
 * of the enclosing class in statically compiled FSM definitions.
 */
public class FSMBuilder extends FSMBehavior {

  public static FSMBehavior build(@DelegatesTo(FSMBuilder.class) Closure<?> c) {
    FSMBuilder fsm = new FSMBuilder();
    c.setDelegate(fsm);
    c.call();
    return fsm;
  }

  public static class StateBuilder {

    public FSMState state;

    public StateBuilder() {
      // for use with property-style initialization
    }

    public StateBuilder(FSMState state) {
      this.state = state;
    }

    public void onEnter(@DelegatesTo(value=FSMState.class, strategy=Closure.DELEGATE_FIRST) Closure<?> c) {
      c.setDelegate(state);
      c.setResolveStrategy(Closure.DELEGATE_FIRST);
      state.onEnterClosure = c;
    }

    public void onEvent(Object name, @DelegatesTo(value=FSMState.class, strategy=Closure.DELEGATE_FIRST) Closure<?> c) {
      c.setDelegate(state);
      c.setResolveStrategy(Closure.DELEGATE_FIRST);
      state.onEventClosures.put(name, c);
    }

    public WakerBehavior after(Number delay, @DelegatesTo(value=FSMState.class, strategy=Closure.DELEGATE_FIRST) final Closure<?> c) {
      long millis = Math.round(delay.doubleValue()*1000);
      c.setDelegate(state);
      c.setResolveStrategy(Closure.DELEGATE_FIRST);
      WakerBehavior timer = new WakerBehavior(millis, new Runnable() {
        @Override
        public void run() {
          c.call();
        }
      });
      state.timers.add(timer);
      return timer;
    }

    public void action(@DelegatesTo(value=FSMState.class, strategy=Closure.DELEGATE_FIRST) Closure<?> c) {
      c.setDelegate(state);
      c.setResolveStrategy(Closure.DELEGATE_FIRST);
      state.actionClosure = c;
    }

    public void onExit(@DelegatesTo(value=FSMState.class, strategy=Closure.DELEGATE_FIRST) Closure<?> c) {
      c.setDelegate(state);
      c.setResolveStrategy(Closure.DELEGATE_FIRST);
      state.onExitClosure = c;
    }

  } // StateBuilder

  public static class FSMState extends FSMBehavior.State {

    public Closure<?> onEnterClosure, actionClosure, onExitClosure;
    public Map<Object,Closure<?>> onEventClosures = new HashMap<Object,Closure<?>>();
    public List<WakerBehavior> timers = new ArrayList<WakerBehavior>();
    public List<WakerBehavior> tmpTimers = new ArrayList<WakerBehavior>();

    public FSMState(Object name) {
      super(name);
    }

    @Override
    public void onEnter() {
      Agent agent = getAgent();
      for (WakerBehavior timer: timers) {
        timer.reset();
        agent.add(timer);
      }
      if (onEnterClosure != null) onEnterClosure.call();
    }

    @Override
    public void action() {
      if (actionClosure != null) actionClosure.call();
      else block();
    }

    @Override
    public void onExit() {
      for (WakerBehavior timer: timers)
        timer.stop();
      for (WakerBehavior timer: tmpTimers)
        timer.stop();
      tmpTimers.clear();
      if (onExitClosure != null) onExitClosure.call();
    }

    @Override
    public void onEvent(Object event, Object info) {
      Closure<?> c = onEventClosures.get(event);
      if (c != null) c.call(info);
    }

    public WakerBehavior after(Number delay, @DelegatesTo(value=FSMState.class, strategy=Closure.DELEGATE_FIRST) final Closure<?> c) {
      long millis = Math.round(delay.doubleValue()*1000);
      c.setDelegate(this);
      c.setResolveStrategy(Closure.DELEGATE_FIRST);
      WakerBehavior timer = new WakerBehavior(millis) {
        @Override
        public void onWake() {
          c.call();
        }
      };
      tmpTimers.add(timer);
      getAgent().add(timer);
      return timer;
    }

  } // FSMState

  public FSMBehavior.State state(Object name, @DelegatesTo(value=StateBuilder.class, strategy=Closure.DELEGATE_FIRST) Closure<?> c) {
    FSMState s = new FSMState(name);
    c.setDelegate(new StateBuilder(s));
    c.setResolveStrategy(Closure.DELEGATE_FIRST);
    c.call();
    add(s);
    return s;
  }

}
