/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.*;
import java.io.File;

/**
 * Shell agent runs in a container and allows execution of shell commands and scripts.
 * The current version supports Groovy commands or scripts only. The requets can be
 * made using the ShellExecReq message, or through a TCP/IP socket-based console.
 */
public class ShellAgent extends Agent {
  
  ////// private attributes
  
  private ScriptEngine engine;
  private Shell shell;
  private File initScript = null;
  private Message rsp;
  private Object sync = new Object();
  private MessageBehavior msgBehavior;
  
  ////// agent methods
  
  public ShellAgent(Shell shell, ScriptEngine engine) {
    this.shell = shell;
    this.engine = engine;
    engine.setVariable("agent", this);
  }

  @Override
  public void init() {
    log.info(getClass().getName()+" init");
    register(Services.SHELL);
    engine.setVariable("container", getContainer());
    engine.setVariable("platform", getPlatform());
    shell.start(engine);
    msgBehavior = new MessageBehavior() {
      @Override
      public void onReceive(Message msg) {
        if (msg instanceof ShellExecReq) handleReq((ShellExecReq)msg);
        else if (msg.getInReplyTo() != null) handleRsp(msg);
        else handleNtf(msg);
      }
    };
    add(msgBehavior);
    add(new OneShotBehavior() {
      @Override
      public void action() {
        if (initScript != null) engine.exec(initScript, null);
      }
    });
  }
  
  @Override
  public void shutdown() {
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
    initScript = new File(script);
  }

  /**
   * Sets the initialization script file to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   * 
   * @param script script file.
   */
  public void setInitrc(File script) {
    initScript = script;
  }
  
  ////// overriden methods to allow external threads to call receive/request directly

  @Override
  public Message receive(final MessageFilter filter, final long timeout) {
    if (Thread.currentThread().getId() == tid)
      return super.receive(filter, timeout);
    synchronized (sync) {
      rsp = null;
      try {
        msgBehavior.block();
        add(new OneShotBehavior() {
          @Override
          public void action() {
            rsp = receive(filter, timeout);
            synchronized (sync) {
              sync.notify();
            }
          }
        });
        sync.wait();
      } catch (InterruptedException ex) {
        // ignore exception
      } finally {
        msgBehavior.restart();
      }
      return rsp;
    }
  }
  
  @Override
  public Message request(final Message msg, final long timeout) {
    if (Thread.currentThread().getId() == tid)
      return super.request(msg, timeout);
    synchronized (sync) {
      rsp = null;
      try {
        msgBehavior.block();
        add(new OneShotBehavior() {
          @Override
          public void action() {
            rsp = request(msg, timeout);
            synchronized (sync) {
              sync.notify();
            }
          }
        });
        sync.wait();
      } catch (InterruptedException ex) {
        // ignore exception
      } finally {
        msgBehavior.restart();
      }
      return rsp;
    }
  }

  ////// private methods
  
  private void handleReq(final ShellExecReq req) {
    Message rsp = null;
    if (req.isScript()) {
      boolean ok = engine.exec(req.getScriptFile(), req.getScriptArgs(), null);
      if (ok) rsp = new Message(req, Performative.AGREE);
      else rsp = new Message(req, Performative.REFUSE);
    } else {
      String cmd = req.getCommand();
      if (cmd.equals(".abort")) {
        engine.abort();
        rsp = new Message(req, Performative.AGREE);
      } else {
        boolean ok = engine.exec(req.getCommand(), null);
        if (ok) rsp = new Message(req, Performative.AGREE);
        else rsp = new Message(req, Performative.REFUSE);
      }
    }
    if (rsp != null) send(rsp);
  }
  
  private void handleRsp(Message rsp) {
    engine.setVariable("rsp", rsp);
    if (shell != null) {
      Term term = shell.getTerm();
      shell.println(term.response(rsp.toString()));
    }
  }
  
  private void handleNtf(Message ntf) {
    engine.setVariable("ntf", ntf);
    if (shell != null) {
      Term term = shell.getTerm();
      shell.println(term.notification(ntf.toString()));
    }
  }
  
}

