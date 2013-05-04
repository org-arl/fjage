Simulation
==========

.. highlight:: groovy

Discrete Event Simulation
-------------------------

When used in the `Discrete event simulation <http://en.wikipedia.org/wiki/Discrete_event_simulation>`_ mode, fjåge allows agents to be tested rapidly through the notion of *virtual time*. The passage of virtual time is simulated such that computation and processing does not take any virtual time, while scheduling requests are met accurately in virtual time. The virtual time advances in discrete steps such that the time when no agent is active is effectively skipped. This potentially allows for simulation of hours of virtual time within seconds.

In order to use the discrete event simulation mode, a few conditions have to be met. The first two of these conditions are related to timing functions and were introduced in the ":ref:`intro`" chapter. The last condition is related to agents deployed in slave containers.

1. Agents must not use any system timing functions directly. Rather than use `System.currentTimeMillis()` or `System.nanoTime()`, the agents should use `Agent.currentTimeMillis()` and `Agent.nanoTime()`.

2. Agents must not use any system scheduling functions directly. Rather than use `Thread.sleep()`, the agent should use `Agent.sleep()`.

3. All agents must be deployed in a single container for testing. Distributed containers (master or slave) are currently not supported by the discrete event simulator.

To run the agents in the discrete event simulation mode, the use of the `RealTimePlatform` in `etc/initrc.groovy` is replaced by `DiscreteEventSimulator`::

    import org.arl.fjage.*

    platform = new DiscreteEventSimulator()
    container = new Container(platform)
    // add agents to the container here
    platform.start()

Now running `fjage.sh` should run the agents in the discrete event simulation mode. You can verify this by looking at the `logs/log-0.txt` file; the time entries in this file will start at 0, since all simulations start at time 0. When all agents become idle with no further events in the system, the discrete event simulator automatically terminates.

.. note:: It is recommended that you do not run interactive agents such as `ShellAgent` in this mode, as the interaction with the real world is not compatible with virtual time.

.. Random Number Generation
.. ------------------------

.. Repeatable generation of random numbers is a common requirement in discrete event simulation. To ensure repeatability, it is important to guarantee deterministic ordering of calls to random number generators used in different threads, or to use individual random number generators for each thread. In the later case, one has to manage the seeds of all random number generators used in the simulation.

.. To help with this task, fjåge provides support for inbuilt random number generation using the `RandomNumberGenerator`_ class. This class maintains a set of random number generators for each thread. Once the root random number generator seed is initialized, the thread-bound random number generator seeds are derived from it. One has to ensure deterministic ordering of first call from each thread to the random number generator. For this, it is recommended that each agent requiring the services of the random number generator initialize it by simply generating one random number in the constructor::

..    import org.arl.fjage.*

..    class MyAgent {
..      MyAgent() {
..        RandomNumberGenerator.nextDouble()
..      }
..    }

.. Subsequent calls to the static methods of the `RandomNumberGenerator` use the thread-bound random number generator automatically.

.. Javadoc links
.. -------------
..
.. _RandomNumberGenerator: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/RandomNumberGenerator.html
