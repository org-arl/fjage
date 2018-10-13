/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.concurrent.Callable;
import org.arl.fjage.*;

/**
 * Shell agent runs in a container and allows execution of shell commands and scripts.
 */
public class ShellAgent extends Agent {

  ////// public constants

  public static final String ABORT = ".abort";

  ////// private attributes

  protected Shell shell = null;
  protected ScriptEngine engine = null;
  protected Callable<Void> exec = null;
  protected CyclicBehavior executor = null;
  protected List<MessageListener> listeners = new ArrayList<MessageListener>();

  ////// interface methods

  public ShellAgent(Shell shell, ScriptEngine engine) {
    this.shell = shell;
    this.engine = engine;
    if (shell != null) shell.init(engine);
    if (engine != null) engine.bind(shell);
  }

  @Override
  public void init() {
    log.info("Agent "+getName()+" init");
    register(Services.SHELL);

    // behavior to exec in agent's thread
    executor = new CyclicBehavior() {
      @Override
      public synchronized void action() {
        if (exec != null) {
          try {
            exec.call();
          } catch (Exception ex) {
            log.log(Level.WARNING, "Exception in script", ex);
            if (shell != null) shell.error(ex.toString());
          }
          exec = null;
        }
        block();
      }
    };
    add(executor);

    // behavior to manage user interaction
    if (shell != null) {
      Thread t = new Thread(getName()+":console") {
        @Override
        public void run() {
          String s = null;
          while (true) {
            String prompt = null;
            if (engine != null) prompt = engine.getPrompt();
            s = shell.readLine(prompt, s);
            if (s == null) {
              shutdownPlatform();
              return;
            } else if (s.equals(Shell.ABORT)) {
              boolean aborted = true;
              if (engine != null && engine.isBusy()) {
                engine.abort();
                aborted = true;
              }
              synchronized(executor) {
                if (exec != null) {
                  exec = null;
                  aborted = true;
                }
              }
              if (aborted) {
                log.info("ABORT");
                shell.error("ABORT");
              }
              s = null;
            } else {
              if (engine == null) s = null;
              else {
                if (exec != null || engine.isBusy()) shell.error("BUSY");
                else {
                  final String cmd = s.trim();
                  synchronized(executor) {
                    exec = new Callable<Void>() {
                      @Override
                      public Void call() {
                        log.info("> "+cmd);
                        engine.exec(cmd);
                        return null;
                      }
                    };
                    executor.restart();
                  }
                  s = null;
                }
              }
            }
          }
        }
      };
      t.setDaemon(true);
      t.start();
    }

    // behavior to manage incoming messages
    add(new MessageBehavior() {
      @Override
      public void onReceive(Message msg) {
        if (msg instanceof ShellExecReq) handleReq((ShellExecReq)msg);
        else {
          log.info(msg.getSender()+" > "+msg.toString());
          if (engine != null) engine.deliver(msg);
          for (MessageListener ml: listeners)
            ml.onReceive(msg);
        }
      }
    });

  }

  @Override
  public void shutdown() {
    log.info("Agent "+getName()+" shutdown");
    if (shell != null) shell.shutdown();
    if (engine != null) engine.shutdown();
  }

  ////// public methods

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

  ////// private methods

  private void shutdownPlatform() {
    getPlatform().shutdown();
    stop();
  }

  private void handleReq(final ShellExecReq req) {
    Message rsp = null;
    if (engine == null || engine.isBusy()) rsp = new Message(req, Performative.REFUSE);
    else {
      if (req.isScript()) {
        boolean ok = false;
        final File file = req.getScriptFile();
        synchronized(executor) {
          if (exec == null && file != null && file.exists()) {
            ok = true;
            exec = new Callable<Void>() {
              @Override
              public Void call() {
                log.info("run "+file.getName());
                engine.exec(file, req.getScriptArgs());
                return null;
              }
            };
            executor.restart();
          }
        }
        if (ok) rsp = new Message(req, Performative.AGREE);
        else rsp = new Message(req, Performative.REFUSE);
      } else {
        String cmd = req.getCommand();
        if (cmd != null && cmd.equals(ABORT)) {
          engine.abort();
          rsp = new Message(req, Performative.AGREE);
        } else {
          boolean ok = false;
          if (cmd != null) ok = engine.exec(cmd);
          if (ok) rsp = new Message(req, Performative.AGREE);
          else rsp = new Message(req, Performative.REFUSE);
        }
      }
    }
    if (rsp != null) send(rsp);
  }

}
