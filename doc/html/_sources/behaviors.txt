Agents and Behaviors
====================

.. highlight:: groovy

Agent lifecycle
---------------

When an `Agent`_ is added to a container, it starts in the INIT state. When the platform is running, agents in the INIT state are initialized by calling their `init()` method. An typical agent overrides this method and adds new behaviors to itself.

After initialization, the agent moves to a RUNNING state. In this state, active behaviors of the agent are scheduled to run. A typical agent is associated with one agent thread, and various behaviors are cooperatively scheduled on this thread. Due to the cooperative nature of the behaviors, a poorly written behavior may block execution of all other behaviors of that agent. Developers should avoid long-running or blocking code in behaviors; if such code is needed, it is best to create a separate thread to run that code.

A behavior that is not ready to run may be in a blocked state (e.g. a behavior that is to be executed at a specified later time). An agent may make a behavior as blocked, by explicitly calling the `block()` method on that behavior. If there are no active behaviors for an agent, the agent goes into an IDLE state. Behaviors may be activated due to timer events, message delivery events or by explicit `restart()` method calls. When one or more behaviors become active, the agent goes back into a RUNNING state.

When an agent is killed or the platform is shutdown, the agent is placed in a FINISHING state. Agents in this state are given a chance to cleanup via a call to their `shutdown()` method. An agent may override this method if a cleanup is required. After the cleanup, the agent is terminated and placed in a FINISHED state, and removed from the container.

An example skeleton agent is shown below:: 

    class MyAgent extends Agent {
      
      void init() {
        // agent is in INIT state
        log.info 'Agent init'
        add new OneShotBehavior({
          // behavior will be executed after all agents are initialized
          // agent is in RUNNING state
          log.info 'Agent ready'
        })
      }

      void shutdown() {
        log.info 'Agent shutdown'
      }

    }

.. tip:: `println()` from an agent is mapped to `log.info()`, and can be used interchangably. However, the `log` object provides more flexibility (e.g. `log.warning()`, `log.fine()`, etc).

The *traditional* behavior creation style may be used by Java and Groovy agents::

    add(new OneShotBehavior() {
      public void action() {
        // do something
      }
    });

The method to override depends on the behavior (e.g. `action()` for most behaviors, but `onTick()` for `TickerBehavior`, and `onWake()` for `WakerBehavior`).

Groovy agents support a simpler alternative syntax if the `GroovyExtensions` are enabled::

    add new OneShotBehavior({
      // do something
    })

Both variants are identical in function. With this syntax, the appropriate method is automatically overridden to call the defined closure. For the examples in the rest of this chapter, we will adopt the simpler Groovy syntax.

One-shot behavior
-----------------

A `OneShotBehavior`_ is run only once at the earliest opportunity. After execution, the behavior is automatically removed. We have seen an example of the one-shot behavior above.

Cyclic behavior
---------------

A `CyclicBehavior`_ is run repeatedly as long as it is active. The behavior may be blocked and restarted as necessary. ::

    class MyAgent extends Agent {
      int n = 0
      void init() {
        // a cyclic behavior that runs 5 times and then marks itself as blocked
        add new CyclicBehavior({
          agent.n++
          println "n = ${agent.n}"
          if (agent.n >= 5) block()
        })
      }
    }

.. tip:: Although it may be possible in some cases to access agent methods and fields directly from a behavior method or closure, it is safer to always use an `agent.` qualifier to access them. Without the qualifier, the closure's delegation strategy causes the behavior methods and fields to be checked first; this can lead to bugs that are difficult to track.

.. note:: Since behaviors are cooperatively scheduled, they should not block.  Hence `Behavior.block()` is not a blocking call; it simply marks the behavior as blocked and removes it from the list of active behaviors to be scheduled, and continues.

Waker behavior
--------------

A `WakerBehavior`_ is run after a specified delay in milliseconds. ::

    add new WakerBehavior(1000, {
      // invoked 1 second later
      println '1000 ms have elapsed!'
    })

Ticker behavior
---------------

A `TickerBehavior`_ is run repeated with a specified delay between invocations. The ticker behavior may be terminated by calling `stop()` at any time. ::

    add new TickerBehavior(5000, {
      // called at intervals of 5 seconds
      println 'tick!'
    })

Backoff behavior
----------------

A `BackoffBehavior`_ is similar to a waker behavior, but allows the wakeup time to be extended dynamically. This is typically useful to implement backoff or retry timeouts. ::

    add new BackoffBehavior(5000, {     // first attempt after 5 seconds
      // make some request, and if it fails, try again after 3 seconds
      def rsp = request(req)
      if (rsp == null || rsp.performative == Performative.FAILURE) backoff(3000)
    })

Poisson behavior
----------------

A `PoissonBehavior`_ is similar to a ticker behavior, but the interval between invocations is an exponentially distributed random variable. This simulates a Poisson arrival process. ::

    add new PoissonBehavior(5000, {
      // called at an average rate of once every 5 seconds
      println 'arrival!'
    })

.. _msgbehavior:

Message behavior
----------------

A `MessageBehavior`_ is invoked when a message is received by the agent. A message behavior may specify what kind of message it is interested in. If multiple message behaviors admit a received message, any one of the behaviors may be invoked for that message.

A message behavior that accepts any message can be added as follows::

    add new MessageBehavior({ msg ->
      println "Incoming message from ${msg.sender}"
    })

If we were only interested in messages of class `MyMessage`, we could set up a behavior accordingly::

    add new MessageBehavior(MyMessage, { msg ->
      println "Incoming message of class ${msg.class} from ${msg.sender}"
    })

Let us next consider a more complex case where we are interested in message of a specific class and from a specific sender::

    def filter = { it instanceof MyMessage && it.sender.name == 'myFriend' } as MessageFilter
    add new MessageBehavior(filter, { msg ->
      println "Incoming message of class ${msg.class} from ${msg.sender}"
    })

Finite state machine behavior
-----------------------------

Finite state machines can easily be implemented using the `FSMBehavior`_ class. These machines are composed out of multiple states, each of which is like a `CyclicBehavior`. State transitions are managed using the `nextState` property.

For example, we can create a grandfather clock using a `FSMBehavior`::

    def b = add new FSMBehavior()
    b.add new FSMBehavior.State('tick') {
      void action() {
        println 'tick!'
        nextState = 'tock'
        fsm.block 1000
      }
    }
    b.add new FSMBehavior.State('tock') {
      void action() {
        println 'tock!'
        nextState = 'tick'
        fsm.block 1000
      }
    }

Test behavior
-------------

The `TestBehavior`_ is a special behavior that helps with development of unit tests. Any `AssertionError` thrown in the behavior is stored and thrown when the test ends. A typical usage for a test case is shown below::

    import org.arl.fjage.*

    def platform = new RealTimePlatform()
    def container = new Container(platform)
    def agent = new Agent()
    container.add agent
    platform.start()

    TestBehavior test = new TestBehavior({
      assert 1+1 == 2 : 'Simple math failed'
      def aid = agent.getAgentID()
      assert aid != null : 'AgentID undefined'
      assert agent.send(new Message(aid)) : 'Message could not be sent'
    })
    test.runOn(agent)

    platform.shutdown()

Custom behaviors
----------------

Although the above behaviors meet most needs, there are times when you need a behavior that isn't already available. In such cases, you can simply extend the `Behavior`_ class to implement your own behavior. This typically involves overriding the `onStart()`, `action()`, `done()` and `onEnd()` methods.

An example two-shot behavior is shown below::

    class TwoShotBehavior extends Behavior {
      int fired
      void onStart() {
        fired = 0
      }
      void action() {
        fired++
        log.info 'Bang!'
      }
      boolean done() {
        fired >= 2
      }
      void onEnd() {
        log.info 'You are dead!'
      }
    }

.. Javadoc links
.. -------------
..
.. _Agent: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/Agent.html
.. _Behavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/Behavior.html
.. _OneShotBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/OneShotBehavior.html
.. _CyclicBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/CyclicBehavior.html
.. _WakerBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/WakerBehavior.html
.. _TickerBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/TickerBehavior.html
.. _BackoffBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/BackoffBehavior.html
.. _PoissonBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/PoissonBehavior.html
.. _MessageBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/MessageBehavior.html
.. _FSMBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/FSMBehavior.html
.. _TestBehavior: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/TestBehavior.html
