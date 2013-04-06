Discrete Event Simulation
=========================

.. highlight:: groovy

When used in the `Discrete event simulation <http://en.wikipedia.org/wiki/Discrete_event_simulation>`_ mode, fjåge allows agents to be tested rapidly through the notion of *virtual time*. The passage of virtual time is simulated such that computation and processing does not take any virtual time, while scheduling requests are met accurately in virtual time. The virtual time advances in discrete steps such that the time when no agent is active is effectively skipped. This potentially allows for simulation of hours of virtual time within seconds.

In order to use the discrete event simulation mode, a few conditions have to be met. The first two of these conditions are related to timing functions and were introduced in the ":ref:`intro`" chapter. The last condition is related to agents deployed in slave containers.

1. Agents must not use any system timing functions directly. Rather than use `System.currentTimeMillis()` or `System.nanoTime()`, the agents should use `Agent.currentTimeMillis()` and `Agent.nanoTime()`.

2. Agents must not use any system scheduling functions directly. Rather than use `Thread.sleep()`, the agent should use `Agent.sleep()`.

3. All agents must be deployed in a single container for testing. Distributed containers (master or slave) are currently not supported by the discrete event simulator.

To run the agents in the discrete event simulation mode, the use of the `RealTimePlatform` in `etc/initrc.groovy` is replaced by `DiscreteEventSimulator`::

    import org.arl.fjage.*

    GroovyAgentExtensions.enable()
    platform = new DiscreteEventSimulator()
    container = new Container(platform)
    // add agents to the container here
    platform.start()

Now running `fjage.sh` should run the agents in the discrete event simulation mode. You can verify this by looking at the `logs/log-0.txt` file; the time entries in this file will start at 0, since all simulations start at time 0. When all agents become idle with no further events in the system, the discrete event simulator automatically terminates.

.. note:: It is recommended that you do not run interactive agents such as `ShellAgent` in this mode, as the interaction with the real world is not compatible with virtual time.
