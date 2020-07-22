package org.arl.fjage.groovy

import org.arl.fjage.*

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
 *     after(1.seconds) {             // every time we enter the TICK state,
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
 *       after(1.second) {           // in this form, the delay is recomputed every
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
 */
class FSMBuilder extends FSMBehavior {

  static FSMBehavior build(@DelegatesTo(FSMBuilder) Closure c) {
    FSMBuilder fsm = new FSMBuilder()
    c.delegate = fsm
    c()
    return fsm
  }

  class StateBuilder {

    FSMState state

    void onEnter(@DelegatesTo(value=FSMState, strategy=Closure.DELEGATE_FIRST) Closure c) {
      c.delegate = state
      c.resolveStrategy = Closure.DELEGATE_FIRST
      state.onEnterClosure = c
    }

    void onEvent(def name, @DelegatesTo(value=FSMState, strategy=Closure.DELEGATE_FIRST) Closure c) {
      c.delegate = state
      c.resolveStrategy = Closure.DELEGATE_FIRST
      state.onEventClosures[name] = c
    }

    WakerBehavior after(double delay, @DelegatesTo(value=FSMState, strategy=Closure.DELEGATE_FIRST) Closure c) {
      long millis = Math.round(delay*1000)
      c.delegate = state
      c.resolveStrategy = Closure.DELEGATE_FIRST
      def timer = new WakerBehavior(millis, { c() })
      state.timers << timer
      return timer
    }

    void action(@DelegatesTo(value=FSMState, strategy=Closure.DELEGATE_FIRST) Closure c) {
      c.delegate = state
      c.resolveStrategy = Closure.DELEGATE_FIRST
      state.actionClosure = c
    }

    void onExit(@DelegatesTo(value=FSMState, strategy=Closure.DELEGATE_FIRST) Closure c) {
      c.delegate = state
      c.resolveStrategy = Closure.DELEGATE_FIRST
      state.onExitClosure = c
    }

  } // StateBuilder

  class FSMState extends FSMBehavior.State {

    Closure onEnterClosure, actionClosure, onExitClosure
    Map<Object,Closure> onEventClosures = [:]
    def timers = []
    def tmpTimers = []

    FSMState(name) {
      super(name)
    }

    @Override
    void onEnter() {
      timers.each {
        it.reset()
        agent.add(it)
      }
      if (onEnterClosure) onEnterClosure()
    }

    @Override
    void action() {
      if (actionClosure) actionClosure()
      else block()
    }

    @Override
    void onExit() {
      timers.each { it.stop() }
      tmpTimers.each { it.stop() }
      tmpTimers.clear()
      if (onExitClosure) onExitClosure()
    }

    @Override
    void onEvent(event, info) {
      Closure c = onEventClosures[event]
      if (c) c(info)
    }

    WakerBehavior after(double delay, @DelegatesTo(value=FSMState, strategy=Closure.DELEGATE_FIRST) Closure c) {
      long millis = Math.round(delay*1000)
      c.delegate = FSMState.this
      c.resolveStrategy = Closure.DELEGATE_FIRST
      def timer = new WakerBehavior(millis) {
        @Override
        void onWake() {
          c()
        }
      }
      tmpTimers << timer
      agent.add(timer)
      return timer
    }

  } // FSMState

  FSMBehavior.State state(def name, @DelegatesTo(value=StateBuilder, strategy=Closure.DELEGATE_FIRST) Closure c) {
    def s = new FSMState(name)
    c.delegate = new StateBuilder(state: s)
    c.resolveStrategy = Closure.DELEGATE_FIRST
    c()
    add(s)
    return s
  }

}
