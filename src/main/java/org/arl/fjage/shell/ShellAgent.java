/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.*;
import org.arl.fjage.*;

/**
 * Shell agent runs in a container and allows execution of shell commands and scripts.
 * The current version supports Groovy commands or scripts only. The requests can be
 * made using the ShellExecReq message, or through a TCP/IP socket-based console.
 */
public class ShellAgent extends Agent {

  ////// public constants

  public static final String ABORT = ".abort";
  
  ////// private classes

  private class Script {
    String name;
    File file;
    Reader reader;
    Script(File file) {
      this.name = null;
      this.file = file;
      this.reader = null;
    }
    Script(String name, Reader reader) {
      this.name = name;
      this.file = null;
      this.reader = reader;
    }
  }
  
  ////// private attributes
  
  private ScriptEngine engine;
  private Shell shell;
  private List<Script> initScripts = new ArrayList<Script>();
  private MessageBehavior msgBehavior;
  private MessageQueue mq = new MessageQueue();
  private List<MessageListener> listeners = new ArrayList<MessageListener>();
  
  ////// agent methods
  
  /**
   * Creates a shell agent with no user interface. This is typically used for
   * executing scripts. This shell has no default initrc.
   *
   * @param engine scripting engine
   */
  public ShellAgent(ScriptEngine engine) {
    this.shell = null;
    this.engine = engine;
    engine.setVariable("agent", this);
  }

  /**
   * Creates a shell agent with a specified user interface and defailt initrc.
   *
   * @param shell user interface
   * @param engine scripting engine
   */
  public ShellAgent(Shell shell, ScriptEngine engine) {
    this.shell = shell;
    this.engine = engine;
    engine.setVariable("agent", this);
    addInitrc("res://org/arl/fjage/shell/fshrc.groovy");
  }

  @Override
  public void init() {
    log.info(getClass().getName()+" init");
    register(Services.SHELL);
    engine.setVariable("container", getContainer());
    engine.setVariable("platform", getPlatform());
    if (shell != null) shell.start(engine);
    msgBehavior = new MessageBehavior() {
      @Override
      public void onReceive(Message msg) {
        if (msg instanceof ShellExecReq) handleReq((ShellExecReq)msg);
        else {
          log.info(msg.getSender()+" > "+msg.toString());
          engine.setVariable((msg.getInReplyTo()==null)?"ntf":"rsp", msg);
          if (shell != null) shell.println(msg, OutputType.RECEIVED);
          if (!engine.isBusy()) display(msg);
          else {
            synchronized (mq) {
              mq.add(msg);
              mq.notifyAll();
            }
          }
        }
      }
    };
    add(msgBehavior);
    add(new TickerBehavior(250) {
      @Override
      public void onTick() {
        if (!engine.isBusy() && mq.length() > 0) {
          Message m = mq.get();
          while (m != null) {
            display(m);
            m = mq.get();
          }
        }
      }
    });
    add(new OneShotBehavior() {
      @Override
      public void action() {
        for (Script script: initScripts) {
          if (engine.isBusy()) engine.waitUntilCompletion();
          if (script.file != null) engine.exec(script.file, null);
          else engine.exec(script.reader, script.name, null);
        }
      }
    });
  }
  
  @Override
  public void shutdown() {
    if (shell != null) shell.shutdown();
    engine.shutdown();
  }
  
  ////// public methods

  /**
   * Sets the name of the initialization script to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   * 
   * @param script script name.
   */
  public void setInitrc(String script) {
    initScripts.clear();
    addInitrc(script);
  }

  /**
   * Sets the initialization script file to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   * 
   * @param script script file.
   */
  public void setInitrc(File script) {
    initScripts.clear();
    addInitrc(script);
  }
  
  /**
   * Sets the initialization script from a reader to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   * 
   * @param name name of the reader.
   * @param reader script reader.
   */
  public void setInitrc(String name, Reader reader) {
    initScripts.clear();
    addInitrc(name, reader);
  }
  
  /**
   * Adds a name of the initialization script to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   * 
   * @param script script name.
   */
  public void addInitrc(String script) {
    if (script.startsWith("res:/")) {
      InputStream inp = getClass().getResourceAsStream(script.substring(5));
      if (inp == null) {
        log.warning(script+" not found");
        return;
      }
      addInitrc(script, new InputStreamReader(inp));
    } else {
      initScripts.add(new Script(new File(script)));
    }
  }

  /**
   * Adds a initialization script file to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   * 
   * @param script script file.
   */
  public void addInitrc(File script) {
    initScripts.add(new Script(script));
  }
  
  /**
   * Adds a initialization script from a reader to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   * 
   * @param name name of the reader.
   * @param reader script reader.
   */
  public void addInitrc(String name, Reader reader) {
    initScripts.add(new Script(name, reader));
  }

  /**
   * Adds a message monitor for displayed messages.
   *
   * @param ml message listener.
   */
  public void addMessageMonitor(MessageListener ml) {
    listeners.add(ml);
  }
  
  /**
   * Removes a message monitor.
   *
   * @param ml message listener.
   */
  public void removeMessageMonitor(MessageListener ml) {
    listeners.remove(ml);
  }

  /**
   * Removes all message monitors.
   */
  public void clearMessageMonitors() {
    listeners.clear();
  }
  
  ////// overriden methods to allow external threads to call receive/request directly

  @Override
  public Message receive(final MessageFilter filter, final long timeout) {
    if (Thread.currentThread().getId() == tid)
      return super.receive(filter, timeout);
    long t = currentTimeMillis();
    long t1 = t+timeout;
    while (t < t1) {
      synchronized (mq) {
        Message m = mq.get(filter);
        if (m != null) return m;
        try {
          mq.wait(t1-t);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
      t = currentTimeMillis();
    }
    return null;
  }
  
  @Override
  public Message request(final Message msg, final long timeout) {
    if (Thread.currentThread().getId() == tid)
      return super.request(msg, timeout);
    if (!send(msg)) return null;
    Message rsp = receive(msg, timeout);
    return rsp;
  }

  @Override
  public boolean send(Message msg) {
    log.info(msg.getRecipient()+" < "+msg.toString());
    if (shell != null) shell.println(msg, OutputType.SENT);
    return super.send(msg);
  }

  ////// private methods
  
  private void handleReq(final ShellExecReq req) {
    Message rsp = null;
    if (req.isScript()) {
      File file = req.getScriptFile();
      boolean ok = false;
      if (file != null) ok = engine.exec(file, req.getScriptArgs(), null);
      if (ok) rsp = new Message(req, Performative.AGREE);
      else rsp = new Message(req, Performative.REFUSE);
    } else {
      String cmd = req.getCommand();
      if (cmd != null && cmd.equals(ABORT)) {
        engine.abort();
        rsp = new Message(req, Performative.AGREE);
      } else {
        boolean ok = false;
        if (cmd != null) ok = engine.exec(cmd, null);
        if (ok) rsp = new Message(req, Performative.AGREE);
        else rsp = new Message(req, Performative.REFUSE);
      }
    }
    if (rsp != null) send(rsp);
  }

  private void display(Message msg) {
    if (shell != null) shell.println(msg, OutputType.NOTIFY);
    for (MessageListener ml: listeners)
      ml.onReceive(msg);
  }
  
}
