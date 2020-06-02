/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.io.Serializable;
import java.util.*;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.arl.fjage.persistence.Store;

/**
 * Base class to be extended by all agents. An agent must be added to a container
 * in order to run. An agent usually overrides the {@link #init()} and {@link #shutdown()}
 * methods to provide the appropriate behavior.
 * <p>
 * A simple example agent is shown below:
 * <pre>
 * import org.arl.fjage.*;
 *
 * public class MyAgent extends Agent {
 *
 *   {@literal @}Override
 *   public void init() {
 *     log.info("MyAgent is starting");
 *     add(new MessageBehavior() {
 *       {@literal @}Override
 *       public void onReceive(Message msg) {
 *         log.info("Got a message!");
 *         // do things with the message
 *       }
 *     });
 *     // add other behaviors
 *   }
 *
 * }
 * </pre>
 *
 * @author  Mandar Chitre
 */
public class Agent implements Runnable, TimestampProvider, Messenger {

  /////////////////////// Constants

  /**
   * Represents a non-blocking timeout of 0 seconds. Can be used with all
   * Agent methods that take a timeout parameter.
   */
  public static final long NON_BLOCKING = 0;

  /**
   * Represents a blocking timeout of infinite time. Can be used with all
   * Agent methods that take a timeout parameter.
   */
  public static final long BLOCKING = -1;

  /////////////////////// Log levels

  protected static final Level ALL = Level.ALL;
  protected static final Level FINEST = Level.FINEST;
  protected static final Level FINER = Level.FINER;
  protected static final Level FINE = Level.FINE;
  protected static final Level INFO = Level.INFO;
  protected static final Level WARNING = Level.WARNING;
  protected static final Level SEVERE = Level.SEVERE;
  protected static final Level OFF = Level.OFF;

  /////////////////////// Private attributes

  private AgentID aid = null;
  private volatile AgentState state = AgentState.INIT;
  private volatile AgentState oldState = AgentState.NONE;
  private Queue<Behavior> newBehaviors = new ArrayDeque<>();
  private Queue<Behavior> activeBehaviors = new PriorityQueue<>();
  private Queue<Behavior> blockedBehaviors = new ArrayDeque<>();
  private Stack<MessageFilter> exclusions = new Stack<>();
  private volatile boolean restartBehaviors = false;
  private boolean unblocked = false;
  private Platform platform = null;
  private Container container = null;
  private MessageQueue queue = new MessageQueue(256);
  private boolean yieldDuringReceive = false;
  protected long tid = -1;
  protected Thread thread = null;
  protected boolean ignoreExceptions = false;

  /////////////////////// Attributes available to agents

  /**
   * Logger for the agent to log messages to.
   */
  protected Logger log = Logger.getLogger(getClass().getName());

  /////////////////////// Methods to be overridden by agents

  /**
   * Called by the container when the agent is started. This method is usually
   * overridden by a class implementing an agent to initialize the agent and
   * its behaviors.
   */
  protected void init() {
    // do nothing
  }

  /**
   * Called by the container when the agent is terminated. This method may
   * optionally be overridden to provide a clean up before termination.
   */
  protected void shutdown() {
    // do nothing
  }

  /**
   * Called by the container if the agent terminates abnormally. This method may
   * be optionally overridden to provide special handling of malfunctioning agents.
   * This method is called before the shutdown() method is called to terminate
   * the agent. The behaviors of the agent are no longer active once this method
   * is called.
   *
   * @param ex exception that caused the agent to die.
   */
  protected void die(Throwable ex) {
    // do nothing
  }

  /////////////////////// Interface methods

  /**
   * Gets the agent id.
   *
   * @return the agent id for this agent.
   */
  public AgentID getAgentID() {
    return aid;
  }

  /**
   * Gets the name of the agent.
   *
   * @return the name of the agent.
   */
  public String getName() {
    if (aid == null) return null;
    return aid.getName();
  }

  /**
   * Changes the logging level for the agent.
   *
   * @param level log level
   * @see java.util.logging.Level
   */
  public void setLogLevel(Level level) {
    log.setLevel(level);
  }

  /**
   * Blocks the agent until its woken up by a message or a call to the
   * {@link #wake()} method.
   */
  protected synchronized void block() {
    if (state == AgentState.FINISHING) return;
    if (!unblocked) {
      unblocked = true;
      if (restartBehaviors) return;
      for (Behavior b: blockedBehaviors)
        if (!b.isBlocked()) return;
    }
    unblocked = false;
    oldState = state;
    state = AgentState.IDLE;
    container.reportIdle(aid);
    try {
      wait();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    if (state == AgentState.IDLE) {
      log.info("block() interrupted");
      if (oldState != AgentState.NONE) {
        state = oldState;
        if (container != null) container.reportBusy(aid);
        oldState = AgentState.NONE;
      }
    }
  }

  /**
   * Blocks the agent until its woken up by a message or a call to the
   * {@link #wake()} method or until timeout, whichever occurs earlier.
   *
   * @param millis timeout in milliseconds
   */
  protected void block(long millis) {
    platform.schedule(new TimerTask() {
        @Override
        public void run() {
          wake();
        }
      }, millis);
    block();
  }

  /**
   * Blocks the agent for a specified period. This method should be used
   * in preference to {@link java.lang.Thread#sleep} as this method provides
   * measures correct delay time for the hosting platform, be it a real-time
   * or a simulated discrete-time platform.
   *
   * @param millis period to delay in milliseconds.
   */
  protected void delay(long millis) {
    long t = currentTimeMillis() + millis;
    long dt = millis;
    while (dt > 0) {
      block(dt);
      dt = t - currentTimeMillis();
    }
  }

  /**
   * Wakes up the agent if it was blocked using {@link #block}.
   */
  public synchronized void wake() {
    if (oldState != AgentState.NONE) {
      state = oldState;
      if (container != null) container.reportBusy(aid);
      oldState = AgentState.NONE;
    }
    notify();
  }

  /**
   * Requests an agent to terminate.
   */
  public void stop() {
    if (state == AgentState.FINISHED  || state == AgentState.FINISHING) return;
    state = oldState = AgentState.FINISHING;
    if (container != null) container.reportBusy(aid);
    wake();
  }

  /**
   * Adds a behavior to the agent. The {@link Behavior#onStart()} method of
   * the behavior is immediately called once its added to the agent.
   *
   * @param b behavior to be added.
   * @return the behavior (same as input b)
   */
  public synchronized Behavior add(Behavior b) {
    b.setOwner(this);
    newBehaviors.add(b);
    wake();
    return b;
  }

  /**
   * Gets the state of the agent.
   *
   * @return the state of the agent.
   */
  public AgentState getState() {
    return state;
  }

  /**
   * Gets the platform hosting the agent.
   *
   * @return the platform hosting the agent, null if the agent is not bound to
   *         a container yet.
   */
  public Platform getPlatform() {
    return platform;
  }

  /**
   * Gets the container hosting the agent.
   *
   * @return the container hosting the agent, null if the agent is not bound to
   *         a container yet.
   */
  public Container getContainer() {
    return container;
  }

  /**
   * Gets the current platform time in milliseconds. This method should be used by the
   * agent to get the current time in preference to {@link java.lang.System#currentTimeMillis()}
   * as this method returns the real time or the simulated discrete time depending on
   * the platform that the agent is running on.
   *
   * @return current platform time in milliseconds.
   */
  @Override
  public long currentTimeMillis() {
    return platform.currentTimeMillis();
  }

  /**
   * Gets the current platform time in nanoseconds. This method should be used by the
   * agent to get the current time in preference to {@link java.lang.System#nanoTime()}
   * as this method returns the real time or the simulated discrete time depending on
   * the platform that the agent is running on.
   *
   * @return current platform time in nanoseconds.
   */
  @Override
  public long nanoTime() {
    return platform.nanoTime();
  }

  /**
   * Convenience method to create agent id for the named agent.
   *
   * @return agent id for the named agent.
   */
  public AgentID agent(String name) {
    return new AgentID(name, this);
  }

  /**
   * Returns an object representing the named topic.
   *
   * @param topic name of the topic.
   * @return object representing the topic.
   */
  public AgentID topic(String topic) {
    return new AgentID(topic, true, this);
  }

  /**
   * Returns an object representing the named topic.
   *
   * @param topic name of the topic.
   * @return object representing the topic.
   */
  public AgentID topic(Enum<?> topic) {
    return new AgentID(topic.getClass().getName()+"."+topic.toString(), true, this);
  }

  /**
   * Returns an object representing the notification topic for an agent.
   *
   * @param agent agent to get notification topic for.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID agent) {
    if (agent.isTopic()) return agent;
    return new AgentID(agent.getName()+"__ntf", true, this);
  }

  /**
   * Returns an object representing a named notification topic for an agent.
   *
   * @param agent agent to get notification topic for.
   * @param topic name for the notification topic.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID agent, String topic) {
    return new AgentID(agent.getName()+"__"+topic+"__ntf", true, this);
  }

  /**
   * Returns an object representing a named notification topic for an agent.
   *
   * @param agent agent to get notification topic for.
   * @param topic name for the notification topic.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID agent, Enum<?> topic) {
    return new AgentID(agent.getName()+"__"+topic.getClass().getName()+"."+topic.toString()+"__ntf", true, this);
  }

  /**
   * Returns an object representing the notification topic for this agent.
   *
   * @return object representing the topic.
   */
  public AgentID topic() {
    if (aid == null) return null;
    return new AgentID(aid.getName()+"__ntf", true, this);
  }

  @Override
  public boolean send(final Message m) {
    if (container == null) return false;
    m.setSender(aid);
    return container.send(m);
  }

  /**
   * Prepares a request to be sent.
   *
   * @param request Request message.
   * @return Request sender.
   */
  public RequestSender prepareRequest(Message request) {
    return new InternalRequestSender(request);
  }

  /**
   * Sends a message to the recipient indicated in the message on all containers
   * running on the current platform.  The recipient may be another agent or a
   * topic.  This method is useful in simulations where multiple containers with
   * the same agents run on the same platform.
   *
   * @param m message to be sent.
   */
  public void platformSend(Message m) {
    m.setSender(aid);
    for (Container c: platform.getContainers())
      c.send(m);
  }

  /**
   * Enables/disables processing of messages during a blocking receive().
   *
   * Experimental implementation. Disabled by default for fjåge 1.7, except in the
   * shell agent. In fjåge 1.8, we expect this functionality to be fully enabled.
   *
   * @param b true to process messages while waiting, false to disable processing.
   */
  protected void setYieldDuringReceive(boolean b) {
    yieldDuringReceive = b;
  }

  /**
   * Checks if messages will be processed during a blocking receive().
   * @see #setYieldDuringReceive(boolean)
   */
  protected boolean getYieldDuringReceive() {
    return yieldDuringReceive;
  }

  @Override
  public Message receive(MessageFilter filter, long timeout) {
    if (Thread.currentThread().getId() != tid)
      throw new FjageException("receive() should only be called from agent thread");
    long deadline = 0;
    if (timeout != NON_BLOCKING) queue.commit(exclusions);
    Message m = queue.get(filter);
    if (m == null && timeout != NON_BLOCKING) {
      if (timeout != BLOCKING) deadline = currentTimeMillis() + timeout;
      do {
        exclusions.push(filter);
        if (timeout == BLOCKING) {
          if (!yieldDuringReceive || !executeBehavior()) block();
        } else if (!yieldDuringReceive || !executeBehavior()) {
          long t = deadline - currentTimeMillis();
          block(t);
        }
        exclusions.pop();
        if (Thread.interrupted()) return null;
        if (state == AgentState.FINISHING) return null;
        queue.commit(exclusions);
        m = queue.get(filter);
      } while (m == null && (timeout == BLOCKING || currentTimeMillis() < deadline));
    }
    return m;
  }

  @Override
  public Message receive(MessageFilter filter) {
    return receive(filter, NON_BLOCKING);
  }

  @Override
  public Message receive() {
    return receive((MessageFilter)null, NON_BLOCKING);
  }

  @Override
  public Message receive(long timeout) {
    return receive((MessageFilter)null, timeout);
  }

  @Override
  public Message receive(final Class<?> cls) {
    return receive(cls, NON_BLOCKING);
  }

  @Override
  public Message receive(final Class<?> cls, long timeout) {
    return receive(m -> cls.isInstance(m), timeout);
  }

  @Override
  public Message receive(final Message m) {
    return receive(m, NON_BLOCKING);
  }

  @Override
  public Message receive(final Message m, long timeout) {
    return receive(new MessageFilter() {
      private String mid = m.getMessageID();
      @Override
      public boolean matches(Message m) {
        String s = m.getInReplyTo();
        if (s == null) return false;
        return s.equals(mid);
      }
    }, timeout);
  }

  @Override
  public Message request(final Message msg, long timeout) {
    if (Thread.currentThread().getId() != tid)
      throw new FjageException("request() should only be called from agent thread "+tid+", but called from "+Thread.currentThread().getId());
    if (!send(msg)) return null;
    return receive(msg, timeout);
  }

  /**
   * Sends a request and waits for a response. This method blocks until a default
   * timeout of 1 second, if no response is received.
   *
   * @param msg message to send.
   * @return received response message, null on timeout.
   */
  @Override
  public Message request(final Message msg) {
    return request(msg, 1000);
  }

  /**
   * Sets the maximum length of the incoming message queue for the agent.
   * If the queue overflows, the oldest messages are dropped.
   *
   * @param size maximum number of messages in the message queue.
   */
  public void setQueueSize(int size) {
    queue.setSize(size);
  }

  /**
   * Subscribes the agent to receive all messages sent to the given topic.
   *
   * @param topic the topic to subscribe to.
   * @return true if the subscription is successful, false otherwise.
   */
  public boolean subscribe(AgentID topic) {
    return container.subscribe(aid, topic);
  }

  /**
   * Unsubscribes the agent from a given topic.
   *
   * @param topic the topic to unsubscribe.
   * @return true if the unsubscription is successful, false otherwise.
   */
  public boolean unsubscribe(AgentID topic) {
    return container.unsubscribe(aid, topic);
  }

  /**
   * Registers the agent with the directory service as a provider of a named
   * service.
   *
   * @param service the named service that the agent provides.
   * @return true if the registration was successful, false otherwise.
   */
  public boolean register(String service) {
    return container.register(aid, service);
  }

  /**
   * Registers the agent with the directory service as a provider of a named
   * service.
   *
   * @param service the named service that the agent provides.
   * @return true if the registration was successful, false otherwise.
   */
  public boolean register(Enum<?> service) {
    return container.register(aid, service.getClass().getName()+"."+service.toString());
  }

  /**
   * De-registers the agent from the directory service as a provider of a named
   * service.
   *
   * @param service the named service that the agent no longer provides.
   * @return true if the de-registration was successful, false otherwise.
   */
  public boolean deregister(String service) {
    return container.deregister(aid, service);
  }

  /**
   * De-registers the agent from the directory service as a provider of a named
   * service.
   *
   * @param service the named service that the agent no longer provides.
   * @return true if the de-registration was successful, false otherwise.
   */
  public boolean deregister(Enum<?> service) {
    return container.deregister(aid, service.getClass().getName()+"."+service.toString());
  }

  /**
   * Finds an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param service the named service of interest.
   * @return an agent id for an agent that provides the service.
   */
  public AgentID agentForService(String service) {
    AgentID a = container.agentForService(service);
    if (a != null) a = new AgentID(a, this);
    return a;
  }

  /**
   * Finds an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param service the named service of interest.
   * @return an agent id for an agent that provides the service.
   */
  public AgentID agentForService(Enum<?> service) {
    AgentID a = container.agentForService(service.getClass().getName()+"."+service.toString());
    if (a != null) a = new AgentID(a, this);
    return a;
  }

  /**
   * Finds all agents that provides a named service.
   *
   * @param service the named service of interest.
   * @return an array of agent ids representing all agent that provide the service.
   */
  public AgentID[] agentsForService(String service) {
    AgentID[] a = container.agentsForService(service);
    if (a != null) {
      for (int i = 0; i < a.length; i++)
        a[i] = new AgentID(a[i], this);
    }
    return a;
  }

  /**
   * Finds all agents that provides a named service.
   *
   * @param service the named service of interest.
   * @return an array of agent ids representing all agent that provide the service.
   */
  public AgentID[] agentsForService(Enum<?> service) {
    AgentID[] a = container.agentsForService(service.getClass().getName()+"."+service.toString());
    if (a != null) {
      for (int i = 0; i < a.length; i++)
        a[i] = new AgentID(a[i], this);
    }
    return a;
  }

  /**
   * Logs a message at an INFO level.
   *
   * @param msg message to log.
   */
  public void println(Object msg) {
    log.info(msg.toString());
  }

  /**
   * Gets a persistent store for the agent.
   */
  public Store getStore() {
    return Store.getInstance(this);
  }

  /**
   * Deep clones an object. This is typically used to explicitly clone a message for
   * modification when autocloning is not enabled.
   *
   * @param obj object to clone.
   * @return cloned object.
   */
  public <T extends Serializable> T clone(T obj) {
    return container.clone(obj);
  }

  /////////////// Standard Java methods to customize

  /**
   * Gets a string representation of the agent.
   *
   * @return string representation of the agent.
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    if (aid != null) return aid.toString();
    return getClass().getName() + "@" + hashCode();
  }

  /////////////////////// Private methods

  /**
   * Assigns an agent to a container, and give it an id.
   * Called by the container as needed.
   */
  final void bind(AgentID aid, Container container) {
    this.aid = aid;
    this.container = container;
    platform = (container == null) ? null : container.getPlatform();
    if (container != null) {
      String cname = container.getName();
      if (cname != null && !cname.startsWith("@"))
        log = Logger.getLogger(getClass().getName()+"/"+container.getName());
    }
    LogHandlerProxy.install(platform, log);
  }

  /**
   * Delivers a message to the agent.
   * Called by the container as needed.
   */
  final void deliver(Message m) {
    if (container == null) return;
    log.finer("MSG "+m.getSender()+" > "+aid+"@"+tid+" : "+m.toString());
    queue.add(container.autoclone(m));
    synchronized (this) {
      restartBehaviors = true;
      unblocked = false;
      wake();
    }
  }

  // returns false if no pending behaviors, true otherwise
  private boolean executeBehavior() {
    // restart necessary blocked behaviors
    if (restartBehaviors) {
      synchronized (this) {
        restartBehaviors = false;
        activeBehaviors.addAll(blockedBehaviors);
        blockedBehaviors.clear();
        queue.commit(exclusions);
      }
    } else {
      Iterator<Behavior> iterator = blockedBehaviors.iterator();
      while (iterator.hasNext()) {
        Behavior b = iterator.next();
        if (!b.isBlocked()) {
          iterator.remove();
          activeBehaviors.add(b);
        }
      }
    }
    try {
      // assimilate any new behaviors
      Behavior b = newBehaviors.poll();
      if (b != null) {
        activeBehaviors.add(b);
        b.onStart();
        return true;
      }
      // execute an active behavior
      b = activeBehaviors.poll();
      if (b != null) {
        b.unblock();
        b.action();
        if (b.done()) {
          b.onEnd();
          b.setOwner(null);
        } else {
          if (b.isBlocked()) blockedBehaviors.add(b);
          else activeBehaviors.add(b);
        }
        return true;
      }
    } catch (Throwable ex) {
      if (ignoreExceptions) log.log(Level.WARNING, "Exception in agent: "+aid, ex);
      else throw(ex);
    }
    synchronized (this) {
      return (newBehaviors.size() > 0);
    }
  }

  /**
   * Lifecycle of the agent. Called by the container as needed.
   *
   * @see java.lang.Runnable#run()
   */
  @Override
  public final void run() {
    thread = Thread.currentThread();
    tid = thread.getId();
    state = AgentState.RUNNING;
    container.reportBusy(aid);
    try {
      init();
      while (!container.isRunning()) {
        block();
        Thread.interrupted(); // interrupts used for disrupting timeouts only
      }
      while (state != AgentState.FINISHING) {
        if (!executeBehavior()) block();
        Thread.interrupted(); // interrupts used for disrupting timeouts only
      }
    } catch (Throwable ex) {
      log.log(Level.SEVERE, "Exception in agent: "+aid, ex);
      die(ex);
    }
    state = AgentState.RUNNING;
    container.reportBusy(aid);
    try {
      shutdown();
    } catch (Throwable ex) {
      log.log(Level.SEVERE, "Exception in agent: "+aid, ex);
    }
    state = AgentState.FINISHED;
    container.reportIdle(aid);
    container.kill(aid);
    AgentLocalRandom.unbind();
    container = null;
    platform = null;
  }

  private class InternalRequestSender
      implements RequestSender {

    private final Message request;
    private List<Consumer<Message>> onAgreeList;
    private List<Consumer<Message>> onRefuseList;
    private List<Consumer<Message>> onFailureList;
    private List<Consumer<Message>> onInformList;
    private List<Consumer<Message>> otherwiseList;
    private List<TimeoutEntry> onTimeoutList;

    private List<WakerBehavior> timeoutBehaviorList;
    private StoppableMessageBehavior messageBehavior;

    private volatile Message message;
    private volatile boolean done = false;
    private volatile boolean cancelled = false;

    private InternalRequestSender(Message request) {
      super();

      this.request = request;
    }

    private <T> List<T> addHandler(List<T> handlerList, T handler) {
      if (handlerList == null) {
        handlerList = new ArrayList<>();
      }
      handlerList.add(handler);
      return handlerList;
    }

    @Override
    public RequestSender onAgree(Consumer<Message> consumer) {
      onAgreeList = addHandler(onAgreeList, consumer);
      return this;
    }

    @Override
    public RequestSender onRefuse(Consumer<Message> consumer) {
      onRefuseList = addHandler(onRefuseList, consumer);
      return this;
    }

    @Override
    public RequestSender onFailure(Consumer<Message> consumer) {
      onFailureList = addHandler(onFailureList, consumer);
      return this;
    }

    @Override
    public RequestSender onInform(Consumer<Message> consumer) {
      onInformList = addHandler(onInformList, consumer);
      return this;
    }

    @Override
    public RequestSender otherwise(Consumer<Message> consumer) {
      otherwiseList = addHandler(otherwiseList, consumer);
      return this;
    }

    @Override
    public RequestSender onTimeout(long timeout, Runnable runnable) {
      if (onTimeoutList == null) {
        onTimeoutList = new ArrayList<>();
      }
      onTimeoutList.add(new TimeoutEntry(timeout, runnable));
      return this;
    }

    @Override
    public Future<Message> send() {
      if ((onTimeoutList != null) && !onTimeoutList.isEmpty()) {
        timeoutBehaviorList = new ArrayList<>();
        for (final TimeoutEntry timeoutEntry : onTimeoutList) {
          final WakerBehavior timeoutBehavior = new WakerBehavior(timeoutEntry.getTimeout()) {

            @Override
            public void onWake() {
              if (done) {
                return;
              }
              done = true;
              try {
                timeoutEntry.getRunnable().run();
              } catch (Throwable t) {
                log.log(Level.WARNING, "Exception", t);
              }
              stopBehaviors();
            }
          };
          timeoutBehaviorList.add(timeoutBehavior);
          add(timeoutBehavior);
        }
      }

      messageBehavior = new StoppableMessageBehavior(new ReplyMessageFilter(request)) {

        @Override
        public void onReceive(Message message) {
          if (done) {
            return;
          }
          if (done()) {
            return;
          }
          switch (message.getPerformative()) {
            case AGREE:
              consumeMessageAndMarkAsDone(onAgreeList, message);
              break;
            case REFUSE:
              consumeMessageAndMarkAsDone(onRefuseList, message);
              break;
            case FAILURE:
              consumeMessageAndMarkAsDone(onFailureList, message);
              break;
            case INFORM:
              consumeMessageAndMarkAsDone(onInformList, message);
              break;
            default:
              consumeMessageAndMarkAsDone(otherwiseList, message);
              break;
          }
        }
      };

      add(messageBehavior);

      Agent.this.send(request);

      return new MessageFuture();
    }

    @Override
    public Message sendAndWait() {
      final Future<Message> future = send();
      try {
        return future.get();
      } catch (ExecutionException | InterruptedException e) {
        return null;
      }
    }

    private void stopBehaviors() {
      if (timeoutBehaviorList != null) {
        for (final WakerBehavior timeoutBehavior : timeoutBehaviorList) {
          timeoutBehavior.stop();
        }
      }
      if (messageBehavior != null) {
        messageBehavior.stop();
      }
    }

    private void consumeMessageAndMarkAsDone(List<Consumer<Message>> consumers, Message message) {
      if (done || (consumers == null) || consumers.isEmpty()) {
        return;
      }
      InternalRequestSender.this.message = message;
      done = true;
      consumeMessage(consumers, message);
    }

    private void consumeMessage(List<Consumer<Message>> consumers, Message message) {
      if ((consumers == null) || consumers.isEmpty()) {
        return;
      }
      for (final Consumer<Message> consumer : consumers) {
        if (consumer != null) {
          try {
            consumer.accept(message);
          } catch (Throwable t) {
            log.log(Level.WARNING, "Exception", t);
          }
        }
      }
    }

    private class MessageFuture implements Future<Message> {

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        if (done) {
          return false;
        }
        try {
          stopBehaviors();
          return true;
        } finally {
          cancelled = true;
          done = true;
        }
      }

      @Override
      public boolean isCancelled() {
        return cancelled;
      }

      @Override
      public boolean isDone() {
        return done;
      }

      @Override
      public Message get()
          throws InterruptedException, ExecutionException {
        try {
          return doGet(0, null);
        } catch (TimeoutException e) {
          // should not be thrown
          throw new RuntimeException(e);
        }
      }

      @Override
      public Message get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null) {
          throw new NullPointerException();
        }
        return doGet(timeout, unit);
      }

      private Message doGet(long timeout, TimeUnit unit)
          throws TimeoutException {
        if (Thread.currentThread().getId() != tid) {
          throw new FjageException("receive() should only be called from agent thread");
        }
        final Long timeoutMs = (unit != null) ? TimeUnit.MILLISECONDS.convert(timeout, unit) : null;
        final Long deadline = (timeoutMs != null) ? currentTimeMillis() + timeoutMs : null;
        while ((getState() != AgentState.FINISHING)
            && !done
            && ((deadline == null) || (currentTimeMillis() < deadline))) {
          if (!executeBehavior()) {
            block();
          }
          Thread.interrupted(); // interrupts used for disrupting timeouts only
        }
        if ((deadline != null) && (currentTimeMillis() >= deadline)) {
          throw new TimeoutException();
        }
        return message;
      }
    }

    private class TimeoutEntry {

      private final long timeout;
      private final Runnable runnable;

      public TimeoutEntry(long timeout, Runnable runnable) {
        super();

        this.timeout = timeout;
        this.runnable = runnable;
      }

      public long getTimeout() {
        return timeout;
      }

      public Runnable getRunnable() {
        return runnable;
      }
    }

    private class ReplyMessageFilter
        implements MessageFilter {

      private final String messageId;

      public ReplyMessageFilter(Message request) {
        super();

        messageId = request.getMessageID();
        if (messageId == null) {
          throw new IllegalArgumentException("Message does not have an ID");
        }
      }

      @Override
      public boolean matches(Message message) {
        return ((message.getInReplyTo() != null) && message.getInReplyTo().equals(messageId));
      }
    }

    private class StoppableMessageBehavior
        extends Behavior {

      private final MessageFilter filter;
      private boolean quit = false;

      public StoppableMessageBehavior() {
        this((MessageFilter) null);
      }

      public StoppableMessageBehavior(final Class<?> cls) {
        this(cls::isInstance);
      }

      public StoppableMessageBehavior(final MessageFilter filter) {
        super();

        this.filter = filter;
      }

      public boolean accepts(Message msg) {
        if (filter == null) {
          return true;
        }
        return filter.matches(msg);
      }

      public void onReceive(Message msg) {
        if (action != null) {
          action.call(msg);
        }
      }

      @Override
      public final void action() {
        final Message msg;
        if (filter == null) {
          msg = agent.receive();
        } else {
          msg = agent.receive(filter, 0);
        }
        if (msg == null) {
          block();
        } else {
          onReceive(msg);
        }
      }

      public final void stop() {
        quit = true;
      }

      @Override
      public final boolean done() {
        return quit;
      }

      @Override
      public int getPriority() {
        if (filter != null) {
          return -100;
        }
        return 0;
      }
    }
  }
}
