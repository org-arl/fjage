/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.*;
import org.arl.fjage.param.ParameterMessageBehavior;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Level;

/**
 * Shell agent runs in a container and allows execution of shell commands and scripts.
 */
public class ShellAgent extends Agent {

  ////// public constants

  public static final String ABORT = ".abort";

  ////// private classes

  protected static class InitScript {
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
    if (engine != null) {
      engine.setVariable("__agent__", this);
      engine.setVariable("__script_engine__", engine);
    }
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
    if (engine != null) {
      engine.setVariable("__agent__", this);
      engine.setVariable("__script_engine__", engine);
    }
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
    this.ignoreExceptions = true;
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
    if (!ephemeral) {
      register(Services.SHELL);
      setYieldDuringReceive(true);
    }

    // support parameters
    add(new ParameterMessageBehavior(ShellParam.class));

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
              if (engine != null && engine.isBusy()) {
                engine.abort();
              }
              synchronized(executor) {
                if (exec != null) {
                  exec = null;
                }
              }
              log.fine("ABORT");
              s = null;
            } else {
              if (engine == null) s = null;
              else if (exec == null && !engine.isBusy() && enabled) {
                backoff = 0;
                final String cmd = s.trim();
                final String p1 = prompt1==null ? "" : prompt1;
                final String p2 = prompt2==null ? "\n" : "\n"+prompt2;
                s = null;
                if (!cmd.isEmpty()) {
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

  /**
   * Get supported script language.
   *
   * @return supported language, or null if unknown.
   */
  public String getLanguage() {
    if (engine == null) return null;
    String lang = engine.getClass().getSimpleName();
    if (lang.endsWith("ScriptEngine")) lang = lang.substring(0, lang.length()-"ScriptEngine".length());
    return lang;
  }

  /**
   * Get title of the shell agent.
   *
   * @return title.
   */
  public String getTitle() {
    return getName();
  }

  /**
   * Get description of the shell agent.
   *
   * @return description.
   */
  public String getDescription() {
    StringBuilder sb = new StringBuilder();
    if (shell != null) sb.append("Interactive ");
    else if (ephemeral) sb.append("Ephemeral ");
    else sb.append("Non-interactive ");
    String lang = getLanguage();
    if (lang != null) sb.append(lang).append(" ");
    sb.append("shell");
    if (!enabled) sb.append(" (disabled)");
    return sb.toString();
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
  Path path = Paths.get(filename);
  GetFileRsp rsp = null;
  try {
    if (Files.isDirectory(path)) {
      StringBuilder sb = new StringBuilder();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
        for (Path entry : stream) {
          if (Files.isSymbolicLink(entry)) continue;
          sb.append(entry.getFileName().toString());
          if (Files.isDirectory(entry)) sb.append('/');
          sb.append('\t');
          sb.append(Files.size(entry));
          sb.append('\t');
          sb.append(Files.getLastModifiedTime(entry).toMillis());
          sb.append('\n');
        }
      }
      rsp = new GetFileRsp(req);
      rsp.setDirectory(true);
      rsp.setContents(sb.toString().getBytes());
    } else if (Files.isReadable(path)) {
      long ofs = req.getOffset();
      long len = req.getLength();
      long length = Files.size(path);
      if (ofs > length) {
        send(new Message(req, Performative.REFUSE));
        return;
      }
      if (len <= 0) len = length - ofs;
      else if (ofs + len > length) len = length - ofs;
      if (len > Integer.MAX_VALUE) throw new IOException("File is too large!");
      byte[] bytes = new byte[(int)len];
      try (SeekableByteChannel channel = Files.newByteChannel(path)) {
        channel.position(ofs);
        int offset = 0;
        int numRead;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (offset < bytes.length && (numRead = channel.read(buffer)) > 0) {
          offset += numRead;
        }
        if (offset < bytes.length) throw new IOException("File read incomplete!");
      }
      rsp = new GetFileRsp(req);
      rsp.setOffset(ofs);
      rsp.setContents(bytes);
    }
  } catch (IOException ex) {
    log.log(Level.WARNING, "GetFileReq failure", ex);
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
    Path path = Paths.get(filename);
    Message rsp = null;
    long ofs = req.getOffset();
    try {
      boolean fileIsDir = filename.endsWith("/") || filename.endsWith(File.separator);
      if (fileIsDir) {
        if (ofs == 0) {
          if (contents == null) {
            if (Files.exists(path)) {
              Files.walk(path)
                  .sorted(Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
            }
            rsp = new Message(req, Performative.AGREE);
          } else {
            if (!Files.exists(path)) {
              Files.createDirectory(path);
              rsp = new Message(req, Performative.AGREE);
            }
          }
        }
      } else {
        if (contents == null && ofs == 0) {
          if (isCache.containsKey(filename)) {
            isCache.get(filename).is.close();
            isCache.remove(filename);
          }
          if (Files.deleteIfExists(path)) rsp = new Message(req, Performative.AGREE);
        } else if (contents == null) {
          try (SeekableByteChannel channel = Files.newByteChannel(path, java.nio.file.StandardOpenOption.WRITE)) {
            channel.truncate(ofs);
          }
          rsp = new Message(req, Performative.AGREE);
        } else {
          if (Files.exists(path) && ofs == Files.size(path)) {
            // Append if ofs != 0
            try (SeekableByteChannel channel = Files.newByteChannel(path, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.APPEND)) {
              channel.write(ByteBuffer.wrap(contents));
            }
          } else {
            // Overwrite if ofs == 0 or random write
            try (SeekableByteChannel channel = Files.newByteChannel(path, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.CREATE)) {
              if (ofs >= 0) channel.truncate(ofs + contents.length);
              if (ofs > 0) channel.position(ofs);
              else if (ofs < 0) channel.position(Files.size(path) + ofs);
              channel.write(ByteBuffer.wrap(contents));
            }
          }
          rsp = new Message(req, Performative.AGREE);
        }
      }
    } catch (IOException ex) {
      log.log(Level.WARNING, "PutFileReq failure", ex);
    }
    if (rsp == null) rsp = new Message(req, Performative.FAILURE);
    send(rsp);
  }

}
