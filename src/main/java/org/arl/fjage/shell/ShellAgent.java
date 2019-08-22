/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

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

  protected class InputStreamCacheEntry {
    InputStream is;
    long lastUsed;
    long pos;
    InputStreamCacheEntry(InputStream is, long pos) {
      this.is = is;
      this.pos = pos;
      this.lastUsed = currentTimeMillis();
    }
  }

  ////// private attributes

  protected Shell shell;
  protected Thread consoleThread = null;
  protected ScriptEngine engine;
  protected Callable<Void> exec = null;
  protected CyclicBehavior executor = null;
  protected List<MessageListener> listeners = new ArrayList<MessageListener>();
  protected List<InitScript> initScripts = new ArrayList<InitScript>();
  protected Map<String,InputStreamCacheEntry> isCache = new HashMap<String,InputStreamCacheEntry>();
  protected boolean quit = false;
  protected boolean ephemeral = false;
  protected boolean enabled = true;

  ////// interface methods

  /**
   * Create a shell agent without a user console. This is typically useful
   * for provinding shell services to other agents.
   *
   * @param engine script engine to use
   */
  public ShellAgent(ScriptEngine engine) {
    shell = null;
    this.engine = engine;
    if (engine != null) engine.setVariable("__agent__", this);
  }

  /**
   * Create a transient shell agent without a user console. This is typically
   * used for initialization or short-lived scripts. This shell automatically
   * terminates when all initrc scripts are executed.
   *
   * @param engine script engine to use
   * @param ephemeral true for transient shell, false for long-lived shell
   */
  public ShellAgent(ScriptEngine engine, boolean ephemeral) {
    shell = null;
    this.ephemeral = ephemeral;
    this.engine = engine;
    if (engine != null) engine.setVariable("__agent__", this);
  }

  /**
   * Create a shell for user interaction.
   *
   * @param shell user console
   * @param engine script engine to use
   */
  public ShellAgent(Shell shell, ScriptEngine engine) {
    this.shell = shell;
    this.engine = engine;
    if (shell != null) shell.init(engine);
    if (engine != null) {
      engine.bind(shell);
      engine.setVariable("__agent__", this);
      engine.setVariable("__shell__", shell);
      engine.setVariable("__script_engine__", engine);
    }
    addInitrc("cls://org.arl.fjage.shell.ShellDoc");
  }

  @Override
  public void init() {
    log.info("Agent "+getName()+" init");
    if (!ephemeral) register(Services.SHELL);

    // behavior to exec in agent's thread
    executor = new CyclicBehavior() {
      @Override
      public synchronized void action() {
        if (exec != null) {
          try {
            exec.call();
          } catch (Throwable ex) {
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
          long backoff = 0;
          while (!quit) {
            if (!enabled) {
              try {
                Thread.sleep(100);
              } catch (InterruptedException ex) {
                interrupt();
              }
              continue;
            }
            String prompt1 = null;
            String prompt2 = null;
            if (engine != null) {
              prompt1 = engine.getPrompt(false);
              prompt2 = engine.getPrompt(true);
            }
            if (s != null) {
              // this usually means that the engine is still processing
              // earlier command so give it time to finish
              try {
                if (backoff > 0) Thread.sleep(backoff);
              } catch (InterruptedException ex) {
                interrupt();
              }
              if (backoff == 0) backoff = 1;
              else if (backoff < 256) backoff *= 2;
            }
            s = shell.readLine(prompt1, prompt2, s);
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
                log.fine("ABORT");
              }
              s = null;
            } else {
              if (engine == null) s = null;
              else if (exec == null && !engine.isBusy() && enabled) {
                backoff = 0;
                final String cmd = s.trim();
                final String p1 = prompt1==null ? "" : prompt1;
                final String p2 = prompt2==null ? "\n" : "\n"+prompt2;
                s = null;
                if (cmd.length() > 0) {
                  synchronized(executor) {
                    exec = () -> {
                      log.fine("> "+cmd);
                      shell.input(p1+cmd.replaceAll("\n", p2));
                      try {
                        engine.exec(cmd);
                      } catch (Throwable ex) {
                        log.warning("Exec failure: "+ex.toString());
                      }
                      return null;
                    };
                    executor.restart();
                  }
                }
              } else if (engine.offer(s)) s = null;
            }
          }
        }
      };
      consoleThread.setDaemon(true);
    }

    // behavior to manage incoming messages
    if (!ephemeral) add(new MessageBehavior() {
      @Override
      public void onReceive(Message msg) {
        if (msg instanceof ShellExecReq) handleExecReq((ShellExecReq)msg);
        else if (msg instanceof GetFileReq) handleGetFileReq((GetFileReq)msg);
        else if (msg instanceof PutFileReq) handlePutFileReq((PutFileReq)msg);
        else if (msg.getPerformative() == Performative.REQUEST) send(new Message(msg, Performative.NOT_UNDERSTOOD));
        else {
          log.fine(msg.getSender()+" > "+msg.toString());
          for (MessageListener ml: listeners)
            try {
              if (ml.onReceive(msg)) return;
            } catch (Throwable ex) {
              log.warning("MessageListener: "+ex.toString());
            }
          if (engine != null) engine.deliver(msg);
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
        } catch (Throwable ex) {
          log.warning("Init script failure: "+ex.toString());
        }
        if (ephemeral) stop();
        else if (consoleThread != null) consoleThread.start();
      }
    });

    // behavior to manage cached input streams (idle timeout after 60 seconds)
    add(new TickerBehavior(60000) {
      @Override
      public void onTick() {
        long t = currentTimeMillis() - 60000;
        Iterator<Map.Entry<String,InputStreamCacheEntry>> it = isCache.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry<String,InputStreamCacheEntry> pair = it.next();
          if (pair.getValue().lastUsed < t) {
            try {
              pair.getValue().is.close();
            } catch (IOException ex) {
              // do nothing
            }
            it.remove();
          }
        }
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
   * Adds a message listener for incoming notifications.
   *
   * @param ml message listener.
   */
  public void addMessageListener(MessageListener ml) {
    listeners.add(ml);
  }

  /**
   * Removes a message listener.
   *
   * @param ml message listener.
   */
  public void removeMessageListener(MessageListener ml) {
    listeners.remove(ml);
  }

  /**
   * Removes all message listeners.
   */
  public void clearMessageListeners() {
    listeners.clear();
  }

  /**
   * Enable/disable user interaction in shell.
   *
   * @param b true to enable, false to disable.
   */
  public void enable(boolean b) {
    enabled = b;
  }

  /**
   * Checks if user interaction in shell is enabled.
   *
   * @return true if enabled, false if disabled.
   */
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public String toString() {
    if (shell == null || engine == null) return super.toString();
    return super.toString()+": "+shell.toString()+" ["+engine.getClass().getSimpleName()+"]";
  }

  ////// private methods

  private void shutdownPlatform() {
    Platform platform = getPlatform();
    if (platform != null) platform.shutdown();
    stop();
  }

  private void handleExecReq(final ShellExecReq req) {
    Message rsp;
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
                log.fine("run "+file.getName());
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
          if (ok) {
            Object ans = req.getAns() ? engine.getVariable("ans") : null;
            if (ans == null) rsp = new Message(req, Performative.AGREE);
            else {
              rsp = new GenericMessage(req, Performative.AGREE);
              ((GenericMessage)rsp).put("ans", ans);
            }
          }
          else rsp = new Message(req, Performative.REFUSE);
        }
      }
    }
    send(rsp);
  }

  private void handleGetFileReq(final GetFileReq req) {
    String filename = req.getFilename();
    if (filename == null) {
      send(new Message(req, Performative.REFUSE));
      return;
    }
    if (filename.endsWith("/") || filename.endsWith(File.separator))
      filename = filename.substring(0, filename.length()-1);
    File f = new File(filename);
    GetFileRsp rsp = null;
    InputStream is = null;
    try {
      if (f.isDirectory()) {
        File[] files = f.listFiles();
        StringBuilder sb = new StringBuilder();
        if (files != null){
          for (File file : files) {
            sb.append(file.getName());
            if (file.isDirectory()) sb.append('/');
            sb.append('\t');
            sb.append(file.length());
            sb.append('\t');
            sb.append(file.lastModified());
            sb.append('\n');
          }
        }
        rsp = new GetFileRsp(req);
        rsp.setDirectory(true);
        rsp.setContents(sb.toString().getBytes());
      } else if (f.canRead()) {
        long ofs = req.getOffset();
        long len = req.getLength();
        long length = f.length();
        if (ofs > length) {
          send(new Message(req, Performative.REFUSE));
          return;
        }
        if (len <= 0) len = length-ofs;
        else if (ofs+len > length) len = length-ofs;
        if (len > Integer.MAX_VALUE) throw new IOException("File is too large!");
        byte[] bytes = new byte[(int)len];
        InputStreamCacheEntry isce = isCache.get(filename);
        if (isce != null) {
          if (isce.pos == ofs) {
            isce.lastUsed = currentTimeMillis();
            isce.pos += len;
            is = isce.is;
          } else {
            isce.is.close();
            isCache.remove(filename);
          }
        }
        int offset = 0;
        int numRead;
        if (is == null) {
          is = new FileInputStream(f);
          if (ofs > 0) is.skip(ofs);
          else if (ofs < 0) is.skip(length-ofs);
        }
        while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length-offset)) >= 0)
          offset += numRead;
        if (offset < bytes.length) throw new IOException("File read incomplete!");
        rsp = new GetFileRsp(req);
        rsp.setOffset(ofs);
        rsp.setContents(bytes);
        if (ofs != 0) {
          if (!isCache.containsKey(filename))
            isCache.put(filename, new InputStreamCacheEntry(is, ofs+bytes.length));
          is = null;
        }
      }
    } catch (IOException ex) {
      log.warning(ex.toString());
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException ex) {
          // do nothing
        }
        isCache.remove(filename);
      }
    }
    if (rsp != null) send(rsp);
    else send(new Message(req, Performative.FAILURE));
  }

  private void handlePutFileReq(final PutFileReq req) {
    String filename = req.getFilename();
    if (filename == null) {
      send(new Message(req, Performative.REFUSE));
      return;
    }
    byte[] contents = req.getContents();
    File f = new File(filename);
    Message rsp = null;
    FileOutputStream os = null;
    try {
      if (contents == null) {
        if (filename.endsWith("/") || filename.endsWith(File.separator)){
          Path pathToBeDeleted = Paths.get(filename);

          Files.walk(pathToBeDeleted)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);

          rsp = new Message(req, Performative.AGREE);
        }
        else if (f.delete()) rsp = new Message(req, Performative.AGREE);
      } else if (filename.endsWith("/") || filename.endsWith(File.separator)){
        if(!f.exists()){
          f.mkdir();
          rsp = new Message(req, Performative.AGREE);
        }
      } else {
        os = new FileOutputStream(f);
        os.write(contents);
        os.flush();
        os.getFD().sync();
        rsp = new Message(req, Performative.AGREE);
      }
    } catch (IOException ex) {
      log.warning(ex.toString());
    } finally {
      if (os != null) {
        try {
          os.close();
        } catch (IOException ex) {
          // do nothing
        }
      }
    }
    if (rsp == null) rsp = new Message(req, Performative.FAILURE);
    send(rsp);
  }

}
