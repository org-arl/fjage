/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage

/**
 * Groovy agent extensions for fjage classes.  This provides Groovy
 * agents with neater syntax.  To enable:
 * <pre>
 * GroovyExtensions.enable();
 * </pre>
 */
class GroovyExtensions {
  static void enable() {

    Agent.metaClass.oneShotBehavior = { Closure c ->
      def b = new OneShotBehavior() {
        @Override
        void action() {
          c.call()
        }
      }
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    Agent.metaClass.addOneShotBehavior = { Closure c ->
      add oneShotBehavior(c)
    }

    Agent.metaClass.cyclicBehavior = { Closure c ->
      def b = new CyclicBehavior() {
        @Override
        void action() {
          c.call()
        }
      }
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    Agent.metaClass.addCyclicBehavior = { Closure c ->
      add cyclicBehavior(c)
    }

    Agent.metaClass.wakerBehavior = { millis, Closure c ->
      def b = new WakerBehavior(millis) {
        @Override
        void onWake() {
          c.call()
        }
      }
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    Agent.metaClass.addWakerBehavior = { millis, Closure c ->
      add wakerBehavior(millis, c)
    }

    Agent.metaClass.tickerBehavior = { millis, Closure c ->
      def b = new TickerBehavior(millis) {
        @Override
        void onTick() {
          c.call()
        }
      }
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    Agent.metaClass.addTickerBehavior = { millis, Closure c ->
      add tickerBehavior(millis, c)
    }

    Agent.metaClass.poissonBehavior = { millis, Closure c ->
      def b = new PoissonBehavior(millis) {
        @Override
        void onTick() {
          c.call()
        }
      }
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    Agent.metaClass.addPoissonBehavior = { millis, Closure c ->
      add poissonBehavior(millis, c)
    }

    Agent.metaClass.messageBehavior = { Closure c ->
      def b = new MessageBehavior() {
        @Override
        void onReceive(Message msg) {
          c.call(msg)
        }
      }
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    Agent.metaClass.addMessageBehavior = { Closure c ->
      add messageBehavior(c)
    }

    Agent.metaClass.messageBehavior = { filter, Closure c ->
      def b = new MessageBehavior(filter) {
        @Override
        void onReceive(Message msg) {
          c.call(msg)
        }
      }
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    Agent.metaClass.addMessageBehavior = { filter, Closure c ->
      add messageBehavior(filter, c)
    }

    Agent.metaClass.messageFilter = { Closure c ->
      new MessageFilter() {
        @Override
        boolean matches(Message msg) {
          return c.call(msg)
        }
      }
    }

    Agent.metaClass.fsmBehaviorState = { state, Closure c ->
      new FSMBehavior.State(state) {
        @Override
        void action() {
          c.delegate = this
          c.resolveStrategy = Closure.DELEGATE_FIRST
          c.call()
        }
      }
    }

  }
}
