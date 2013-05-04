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

    Closure bcon0 = { Class<Behavior> cls, Closure c ->
      def b = cls.getDeclaredConstructor().newInstance()
      b.action = c as Callback
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    OneShotBehavior.metaClass.constructor << bcon0.curry(OneShotBehavior)
    CyclicBehavior.metaClass.constructor << bcon0.curry(CyclicBehavior)
    TestBehavior.metaClass.constructor << bcon0.curry(TestBehavior)
    MessageBehavior.metaClass.constructor << bcon0.curry(MessageBehavior)

    def bcon1 = { Class<Behavior> cls, long param, Closure c ->
      def b = cls.getDeclaredConstructor(long).newInstance(param)
      b.action = c as Callback
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    WakerBehavior.metaClass.constructor << bcon1.curry(WakerBehavior)
    TickerBehavior.metaClass.constructor << bcon1.curry(TickerBehavior)
    PoissonBehavior.metaClass.constructor << bcon1.curry(PoissonBehavior)
    BackoffBehavior.metaClass.constructor << bcon1.curry(BackoffBehavior)

    MessageBehavior.metaClass.constructor << { Class<?> msg, Closure c ->
      def b = new MessageBehavior(msg)
      b.action = c as Callback
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    MessageBehavior.metaClass.constructor << { MessageFilter filter, Closure c ->
      def b = new MessageBehavior(filter)
      b.action = c as Callback
      c.delegate = b
      c.resolveStrategy = Closure.DELEGATE_FIRST
      return b
    }

    AgentID.metaClass.leftShift = { Message msg ->
      request(msg);
    }

  }
}
