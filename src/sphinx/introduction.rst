.. _intro:

Introduction
============

Fjåge provides a **lightweight** and **easy-to-learn** platform for `agent-oriented software development <http://en.wikipedia.org/wiki/Agent-oriented_programming>`_ in Java and `Groovy <http://groovy.codehaus.org/>`_.

Why fjåge?
----------

Several frameworks exist for agent-oriented software development. For Java programmers, `JADE <http://jade.tilab.com/>`_ provides a `FIPA <http://www.fipa.org/>`_-compliant framework for multi-agent systems. The `API <http://org-arl.github.com/fjage/javadoc/>`_ for fjåge is largely based on the API available in JADE, and so any developer familiar with JADE should have very little difficulty learning to develop using fjåge. However, there are some significant differences between the philosophy between the two projects. The key advantages of fjåge are:

* Fjåge is designed to be very **lightweight and fast**, and is suitable for Java-capable embedded systems.
* The API for fjåge is kept very simple with a view to making it **easy to learn**, and having very little scaffolding code. This enables a **quick agent development cycle**.
* Fjåge can be run in realtime mode, or in a **discrete event simulation mode**. This makes it ideally suited for development of simulators, and allows rapid testing of production code in simulated environments.
* Fjåge has excellent **Groovy support** that makes agent development easy to learn and enjoyable, and the resulting code very readable.
* Fjåge provides an **interactive shell and scripting support**, making development, debugging and remote management easy.

On the flip side, although fjåge follows many of the ideas from FIPA, it is not fully FIPA-compliant and cannot directly interact with other FIPA-compliant multiagent systems.

Java and Groovy support
-----------------------

Although most of the functionality of the framework can be used in pure-Java projects, the adoption of Groovy in the project simplifies development immensely. In this guide, most of the code examples are in Groovy. Writing equivalent Java code is mostly trivial, though there are cases where the mapping may not be obvious. In such cases, we provide Java examples alongside the Groovy ones.

.. Developers wishing to develop Java agents are advised to read the chapter ":ref:`java`".

Key concepts
------------

A multi-agent system developed in fjåge consists of several software *agents* that communicate with each other using *messages*. Each instance of an agent is identified by a unique *AgentID* that can be used to send messages to that agent. Agents have *behaviors* that are invoked on events, and allow the agents to take actions. The behaviors may be invoked at a specified time, at a specified rate, on reception of a message, occurance of some event, or even continously. The agents reside in one or more *containers* that provide agent management, directory and messaging services. Various containers may run on the same node, or on different nodes in a network. Each container runs on a *platform* that provides container management, timing and scheduling services.

There are two kinds of platforms available:

* **RealTimePlatform** -- This platform provides timing and scheduling services such that the timing requirements of an application are met on a best-effort basis. For example, if an agent asks to have a behavior be activated every 500 ms, the platform tries its best (within the limits of what the operating system and hardware allows) to invoke the behavior every 500 ms. The time returned by the time-related functions is based on the operating system's real-time clock.
* **DiscreteEventSimulator** -- This platform allows agents to be tested in a `discrete event simulation <http://en.wikipedia.org/wiki/Discrete_event_simulation>`_ mode. Essentially, all the time-related functions and scheduling use a *virtual time*. The passage of virtual time is simulated such that computation and processing does not take any virtual time, while scheduling requests are met accurately in virtual time. The virtual time advances in discrete steps such that the time when no agent is active is effectively skipped. This potentially allows for simulation of hours of virtual time within seconds.

To switch between the two platforms, the agent code does not require any changes as long as a couple of simple rules are followed while developing the agents:

* Agents must not use any system timing functions directly. Rather than use `System.currentTimeMillis()` or `System.nanoTime()`, the agents should use `Agent.currentTimeMillis()` and `Agent.nanoTime()`.
* Agents must not use any system scheduling functions directly. Rather than use `Thread.sleep()`, the agent should use `Agent.sleep()`.

Following these rules guarantees that the agents transparently switch between the real-time or virtual time that the platform provides.

Agents may choose to provide one or more *services*. Rather than having to know the AgentID of an agent in advance, agents requiring the services of another agent may look for agents providing specific services via the *directory service*. Agents may choose to subscribe to and send messages to *topics*. All agents subscribing to a topic receive each message sent to that topic.

That's pretty much it for the concepts that you need to understand to get started. If all of this seems a bit abstract at the moment, don't worry about it -- things will become clear shortly as we go through some examples.

License
-------

Fjåge is released under the open source `simplified (3-clause) BSD license <http://github.com/org-arl/fjage/blob/master/LICENSE.txt>`_.

Availability
------------

Fjåge is available as a binary release via Maven central:

.. parsed-literal::

    <dependency>
      <groupId> com.github.org-arl </groupId>
      <artifactId> fjage </artifactId>
      <version> |version| </version>
    </dependency>

Its source code is available via `GitHub <http://github.com/org-arl/fjage>`_::

    git clone git@github.com:org-arl/fjage.git
