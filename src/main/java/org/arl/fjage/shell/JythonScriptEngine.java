/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import org.arl.fjage.*;
import org.python.util.PythonInterpreter;
import org.python.core.PyCode;
import org.python.core.PyFunction;
import org.python.core.PyNone;

/**
 * Implements a Jython 2.7 script engine.
 */
public class JythonScriptEngine implements ScriptEngine {

  static {
    Properties prop = new Properties();
    prop.setProperty("python.import.site", "false");
    PythonInterpreter.initialize(System.getProperties(), prop, new String[0]);
  }

  private Shell shell = null;
  private ShellOutputStream sos = null;
  private Thread busy = null;
  private PythonInterpreter py;
  private Logger log = Logger.getLogger(getClass().getName());

  public JythonScriptEngine() {
    py = new PythonInterpreter();
    py.exec("import sys\n"+
            "def __allow_interrupt__(a,b,c):\n"+
            "  import time\n"+
            "  time.sleep(0.000001)\n"+
            "  return __allow_interrupt__\n");
    InputStream init = getClass().getResourceAsStream("/org/arl/fjage/shell/fshrc.py");
    if (init == null) log.warning("fshrc.py not found!");
    else {
      try {
        InputStreamReader reader = new InputStreamReader(init);
        py.exec(read(reader));
      } catch (IOException ex) {
        shell.error(ex.toString());
        log.warning(ex.toString());
      }
    }
  }

  protected void println(String s) {
    if (shell != null) shell.println(s);
  }

  @Override
  public String getPrompt(boolean cont) {
    return cont ? "- " : "> ";
  }

  @Override
  public boolean isComplete(String cmd) {
    if (cmd.startsWith("<")) return true;
    if (cmd.endsWith("\n")) return true;
    if (cmd.contains("\n")) return false;
    try {
      py.compile(cmd);
    } catch (Exception ex) {
      return false;
    }
    return true;
  }

  @Override
  public void bind(Shell shell) {
    this.shell = shell;
    sos = new ShellOutputStream(shell);
  }

  @Override
  public boolean exec(String cmd) {
    if (cmd.startsWith("<")) return exec(new File(cmd.substring(1)+".py"));
    if (isBusy()) return false;
    synchronized (this) {
      try {
        log.info("EXEC: "+cmd);
        busy = Thread.currentThread();
        try {
          py.setOut(sos);
          py.exec("sys.settrace(__allow_interrupt__)");
          Object rv  = py.eval(cmd);
          if (rv instanceof PyFunction) {
            try {
              rv = ((PyFunction)rv).__call__();
            } catch (Exception ex) {
              // ignore
            }
          }
          py.set("ans", rv);
          if (rv != null && !(rv instanceof PyNone)) println((rv instanceof Message) ? rv.toString() : py.eval("str(ans)").toString());
        } catch (Exception ex) {
          py.exec(cmd);
        }
      } catch (Exception ex) {
        shell.error(ex.toString());
        log.warning(ex.toString());
      } finally {
        Thread.interrupted();
        busy = null;
      }
    }
    return true;
  }

  @Override
  public boolean exec(File script) {
    if (isBusy()) return false;
    try {
      byte[] encoded = Files.readAllBytes(Paths.get(script.getAbsolutePath()));
      String code = new String(encoded);
      py.set("script", script.getName());
      return exec(code);
    } catch (IOException ex) {
      shell.error(ex.toString());
      log.warning(ex.toString());
    } finally {
      py.set("script", null);
    }
    return false;
  }

  @Override
  public boolean exec(File script, List<String> args) {
    try {
      py.set("args", args);
      return exec(script);
    } finally {
      py.set("args", null);
    }
  }

  @Override
  public boolean exec(Class<?> script) {
    return false;
  }

  @Override
  public boolean exec(Class<?> script, List<String> args) {
    return false;
  }

  @Override
  public boolean exec(Reader reader, String name) {
    if (isBusy()) return false;
    try {
      String s = read(reader);
      py.set("script", name);
      return exec(s);
    } catch (IOException ex) {
      shell.error(ex.toString());
      log.warning(ex.toString());
    } finally {
      py.set("script", null);
    }
    return false;
  }

  @Override
  public boolean exec(Reader reader, String name, List<String> args) {
    try {
      py.set("args", args);
      return exec(reader, name);
    } finally {
      py.set("args", null);
    }
  }

  @Override
  public void deliver(Message msg) {
    if (msg.getPerformative() == Performative.INFORM || msg.getInReplyTo() == null) py.set("ntf", msg);
    else py.set("rsp", msg);
    if (shell != null) shell.notify(msg.getSender().getName() + " >> " + msg.toString());
  }

  @Override
  public boolean isBusy() {
    return busy != null;
  }

  @Override
  public void abort() {
    if (!isBusy()) return;
    try {
      busy.interrupt();
    } catch (Exception ex) {
      // do nothing
    }
  }

  @Override
  public void setVariable(String name, Object value) {
    if (name.equals("agent") && value instanceof Agent) name = "__agent__";
    py.set(name, value);
  }

  @Override
  public Object getVariable(String name) {
    return py.get(name);
  }

  @Override
  public void shutdown() {
    py.cleanup();
  }

  private String read(Reader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    int n = -1;
    char[] buf = new char[1024];
    do {
      n = reader.read(buf, 0, buf.length);
      if (n > 0) sb.append(buf, 0, n);
    } while (n > 0);
    return sb.toString();
  }

  class ShellOutputStream extends OutputStream {

    private StringBuffer sb;
    private Shell shell;

    ShellOutputStream(Shell shell) {
      this.shell = shell;
      sb = new StringBuffer();
    }

    public void write(int b) {
      if (b == 13 || b == 10) {
        if (shell != null) shell.println(sb.toString());
        sb = new StringBuffer();
      } else {
        sb.append((char)b);
      }
    }

  }

}
