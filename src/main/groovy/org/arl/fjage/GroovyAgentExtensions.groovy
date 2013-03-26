/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage

/**
 * Groovy agent extensions to mixin into Agent class.  This provides Groovy
 * agents with neater syntax.  To enable:
 * <pre>
 * GroovyAgentExtensions.enable()
 * </pre>
 */
class GroovyAgentExtensions {

  OneShotBehavior oneShotBehavior(Closure<?> c) {
    return new OneShotBehavior() {
      @Override
      void action() {
        c.call()
      }
    }
  }

  CyclicBehavior cyclicBehavior(Closure<?> c) {
    return new CyclicBehavior() {
      @Override
      void action() {
        c.call()
      }
    }
  }

  WakerBehavior wakerBehavior(def millis, Closure<?> c) {
    return new WakerBehavior(millis) {
      @Override
      void onWake() {
        c.call()
      }
    }
  }

  TickerBehavior tickerBehavior(def millis, Closure<?> c) {
    return new TickerBehavior(millis) {
      @Override
      void onTick() {
        c.call()
      }
    }
  }

  PoissonBehavior poissonBehavior(def millis, Closure<?> c) {
    return new PoissonBehavior(millis) {
      @Override
      void onTick() {
        c.call()
      }
    }
  }

  MessageBehavior messageBehavior(Closure<?> c) {
    return new MessageBehavior() {
      @Override
      void onReceive(Message msg) {
        c.call(msg)
      }
    }
  }

  MessageBehavior messageBehavior(def filter, Closure<?> c) {
    return new MessageBehavior(filter) {
      @Override
      void onReceive(Message msg) {
        c.call(msg)
      }
    }
  }

  String toString() {
    return this as String
  }

}

