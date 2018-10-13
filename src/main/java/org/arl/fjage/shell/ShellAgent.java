/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
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

  ////// private classes

  protected class InitScript {
    String name;
    File file;
    Reader reader;
    Class<?> cls;
    InitScript(File file) {
      this.name = null;
      this.file = file;
      this.reader = null;
    }
    InitScript(String name, Reader reader) {
      this.name = name;
      this.file = null;
      this.reader = reader;
    }
    InitScript(Class<?> cls) {
      this.cls = cls;
    }
  }

  ////// private attributes

  protected Shell shell = null;
  protected Thread consoleThread = null;
  protected ScriptEngine engine = null;
  protected Callable<Void> exec = null;
  protected CyclicBehavior executor = null;
  protected List<MessageListener> listeners = new ArrayList<MessageListener>();
  protected List<InitScript> initScripts = new ArrayList<InitScript>();
  protected boolean quit = false;

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
            log.warning("Exec failure: "+ex.toString());
          }
          exec = null;
        }
        block();
      }
    };
    add(executor);

    // behavior to manage user interaction
    if (shell != null) {
      consoleThread = new Thread(getName()+":console") {
        @Override
        public void run() {
          String s = null;
          while (!quit) {
            String prompt = null;
            if (engine != null) prompt = engine.getPrompt();
            s = shell.readLine(prompt, s);
            if (s == null) {
              shutdownPlatform();
              break;
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
              }
              s = null;
            } else {
              if (engine == null) s = null;
              else {
                final String cmd = s.trim();
                s = null;
                if (cmd.length() > 0) {
                  if (exec != null || engine.isBusy()) shell.error("BUSY");
                  else {
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
                  }
                }
              }
            }
          }
          shell.shutdown();
        }
      };
      consoleThread.setDaemon(true);
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

    // behavior to manage init scripts
    add(new OneShotBehavior() {
      @Override
      public void action() {
        try {
          for (InitScript script: initScripts) {
            if (script.file != null) engine.exec(script.file);
            else if (script.reader != null) engine.exec(script.reader, script.name);
            else if (script.cls != null) engine.exec(script.cls);
          }
        } catch (Exception ex) {
          log.warning("Init script failure: "+ex.toString());
        }
        if (consoleThread != null) consoleThread.start();
      }
    });

  }

  @Override
  public void shutdown() {
    log.info("Agent "+getName()+" shutdown");
    quit = true;
    if (consoleThread != null) consoleThread.interrupt();
    if (engine != null) engine.shutdown();
    if (shell != null) shell.shutdown();
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
    } else if (script.startsWith("cls://")) {
      try {
        initScripts.add(new InitScript(Class.forName(script.substring(6))));
      } catch (ClassNotFoundException ex) {
        log.warning(script+" not found");
      }
    } else {
      initScripts.add(new InitScript(new File(script)));
    }
  }

  /**
   * Adds a initialization script file to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   *
   * @param script script file.
   */
  public void addInitrc(File script) {
    initScripts.add(new InitScript(script));
  }

  /**
   * Adds a initialization script from a reader to setup the console environment. This
   * method should only be called before the agent is added to a running container.
   *
   * @param name name of the reader.
   * @param reader script reader.
   */
  public void addInitrc(String name, Reader reader) {
    initScripts.add(new InitScript(name, reader));
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
