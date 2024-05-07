/******************************************************************************

Copyright (c) 2016, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import groovy.lang.*;
import groovy.transform.ThreadInterrupt;
import org.arl.fjage.Message;
import org.arl.fjage.Performative;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Groovy scripting engine.
 */
public class GroovyScriptEngine implements ScriptEngine {

  ////// private constants

  private final int MAX_RESULT_LEN = 8192;
  private final int RESULT_SNIPPET_LEN = 32;

  ////// inner classes

  static class BlockingInput {

    private int waiting = 0;
    private String input = null;

    synchronized String get() throws InterruptedException {
      try {
        waiting++;
        while (input == null)
          wait();
      } finally {
        waiting--;
      }
      String s = input;
      input = null;
      return s;
    }

    synchronized boolean put(String s) {
      if (waiting == 0) return false;
      input = s;
      notify();
      return true;
    }

    synchronized boolean isWaiting() {
      return waiting > 0;
    }

  }

  ////// private attributes

  private GroovyShell groovy;
  private final Binding binding;
  private ImportCustomizer imports;
  private Shell out = null;
  private Thread busy = null;
  private final Documentation doc = new Documentation();
  private final BlockingInput input = new BlockingInput();
  private final Logger log = Logger.getLogger(getClass().getName());

  ////// constructor

  public GroovyScriptEngine() {
    binding = new ConcurrentBinding();
    GroovyClassLoader gcl = new GroovyClassLoader(getClass().getClassLoader());
    init(gcl);
  }

  public GroovyScriptEngine(GroovyClassLoader gcl) {
    binding = new ConcurrentBinding();
    init(gcl);
  }

  public GroovyScriptEngine(ClassLoader cl) {
    binding = new ConcurrentBinding();
    GroovyClassLoader gcl = new GroovyClassLoader(cl);
    init(gcl);
  }

  private void init(GroovyClassLoader gcl) {
    CompilerConfiguration compiler = new CompilerConfiguration();
    compiler.setScriptBaseClass(BaseGroovyScript.class.getName());
    imports = new ImportCustomizer();
    binding.setVariable("__input__", input);
    binding.setVariable("rsp", null);
    binding.setVariable("ntf", null);
    compiler.addCompilationCustomizers(imports);
    compiler.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
    groovy = new GroovyShell(gcl, binding, compiler);
    binding.setVariable("__groovy__", groovy);
    binding.setVariable("__doc__", doc);
    groovy.evaluate("__init__()");
  }

  ////// script engine methods

  @Override
  public void bind(Shell shell) {
    out = shell;
    binding.setVariable("__out__", out);
  }

  @Override
  public String getPrompt(boolean cont) {
    return cont ? "- " : "> ";
  }

  @Override
  public boolean isComplete(String cmd) {
    if (cmd == null || cmd.trim().isEmpty()) return true;
    try {
      groovy.parse(cmd);
      return true;
    } catch (MultipleCompilationErrorsException ex) {
      String s = ex.getMessage();
      if (s == null) return true;
      if (s.contains("unexpected token")) return false;
      return !s.contains("expecting");
    } catch (Throwable ex) {
      return true;
    }
  }

  @Override
  public boolean exec(final String cmd1) {
    if (isBusy()) return false;
    synchronized(this) {
      try {
        busy = Thread.currentThread();
        String cmd = cmd1.trim();
        if (cmd.startsWith("help ")) cmd = "help '"+cmd.substring(5)+"'";
        else if (cmd.startsWith("import ")) cmd = "export '"+cmd.substring(7)+"'";
        log.fine("EVAL: "+cmd);
        Object rv = null;
        try {
          if (binding.hasVariable(cmd)) {
            rv = binding.getVariable(cmd);
            if (rv instanceof Closure) {
              Closure<?> cl = (Closure<?>)rv;
              try {
                binding.setVariable("out", out);
                rv = cl.call();
              } catch (MissingMethodException ex) {
                // do nothing, as it's probably a closure that needs at least one argument
              }
            }
          } else {
            binding.setVariable("out", out);
            binding.setVariable("script", null);
            binding.setVariable("args", null);
            rv = groovy.evaluate(cmd);
          }
        } catch (Throwable ex) {
          error(ex);
        } finally {
          binding.setVariable("out", null);
          binding.setVariable("ans", rv);
        }
        if (rv != null && !cmd.endsWith(";"))
          println((rv instanceof Message) ? rv : groovy.evaluate("ans.toString()"));
        return true;
      } catch (RejectedExecutionException ex) {
        return false;
      } finally {
        Thread.interrupted();
        busy = null;
      }
    }
  }

  @Override
  public boolean exec(final File script) {
    return exec(script, null);
  }

  @Override
  public boolean exec(final File script, final List<String> args) {
    if (isBusy()) return false;
    try {
      log.fine("RUN: "+script.getAbsolutePath());
      try {
        binding.setVariable("out", out);
        binding.setVariable("script", script.getAbsoluteFile());
        binding.setVariable("args", null);
        groovy.getClassLoader().clearCache();
        groovy.run(script, args!=null?args:new ArrayList<String>());
      } catch (Throwable ex) {
        error(ex);
      } finally {
        binding.setVariable("out", null);
        binding.setVariable("script", null);
      }
      return true;
    } catch (RejectedExecutionException ex) {
      return false;
    }
  }

  @Override
  public boolean exec(final Class<?> script) {
    return exec(script, null);
  }

  @Override
  public boolean exec(final Class<?> script, final List<String> args) {
    if (ShellExtension.class.isAssignableFrom(script)) {
      log.fine("LOAD: "+script.getName());
      importClasses("static "+script.getName()+".*");
      try {
        Method m = script.getMethod("__init__", ScriptEngine.class);
        m.invoke(null, this);
      } catch (NoSuchMethodException ex) {
        // do nothing - it's OK to have no __init__() method
      } catch (Throwable ex) {
        error(ex);
      }
      try {
        Field m = script.getField("__doc__");
        doc.add((String)m.get(null));
      } catch (NoSuchFieldException ex) {
        // do nothing - it's OK to have no __doc__ field
      } catch (Throwable ex) {
        error(ex);
      }
      return true;
    }
    synchronized(this) {
      try {
        if (isBusy()) return false;
        busy = Thread.currentThread();
        log.fine("RUN: "+script.getName());
        try {
          binding.setVariable("out", out);
          binding.setVariable("script", script.getName());
          binding.setVariable("args", args);
          Script gs = (Script)script.getDeclaredConstructor().newInstance();
          gs.setBinding(binding);
          gs.run();
        } catch (Throwable ex) {
          error(ex);
        } finally {
          binding.setVariable("out", null);
          binding.setVariable("script", null);
        }
        return true;
      } catch (RejectedExecutionException ex) {
        return false;
      } finally {
        Thread.interrupted();
        busy = null;
      }
    }
  }

  @Override
  public boolean exec(final Reader reader, final String name) {
    return exec(reader, name, null);
  }

  @Override
  public boolean exec(final Reader reader, final String name, final List<String> args) {
    if (isBusy()) return false;
    synchronized(this) {
      try {
        busy = Thread.currentThread();
        log.fine("RUN: "+name);
        try {
          binding.setVariable("out", out);
          binding.setVariable("script", name);
          binding.setVariable("args", null);
          groovy.getClassLoader().clearCache();
          groovy.run(reader, name, args!=null?args:new ArrayList<String>());
        } catch (Throwable ex) {
          error(ex);
        } finally {
          binding.setVariable("out", null);
          binding.setVariable("script", null);
        }
        return true;
      } catch (RejectedExecutionException ex) {
        return false;
      } finally {
        Thread.interrupted();
        busy = null;
      }
    }
  }

  @Override
  public boolean isBusy() {
    return busy != null;
  }

  @Override
  public void abort() {
    try {
      busy.interrupt();
    } catch (Exception ex) {
      // do nothing
    }
  }

  @Override
  public void setVariable(String name, Object value) {
    binding.setVariable(name, value);
  }

  @Override
  public Object getVariable(String name) {
    try {
      return binding.getVariable(name);
    } catch (Exception ex) {
      return null;
    }
  }

  @Override
  public void importClasses(String clazz) {
    if (clazz.startsWith("static ")) {
      clazz = clazz.substring(7).trim();
      if (clazz.endsWith(".*")) imports.addStaticStars(clazz.substring(0,clazz.length()-2));
      else {
        int n = clazz.lastIndexOf('.');
        if (n < 0) return;
        imports.addStaticImport(clazz.substring(0, n), clazz.substring(n+1));
      }
    } else {
      if (clazz.endsWith(".*")) imports.addStarImports(clazz.substring(0,clazz.length()-2));
      else imports.addImports(clazz);
    }
  }

  @Override
  public void shutdown() {
    abort();
  }

  @Override
  public void deliver(Message msg) {
    if (msg.getPerformative() == Performative.INFORM || msg.getInReplyTo() == null) binding.setVariable("ntf", msg);
    else binding.setVariable("rsp", msg);
    if (out != null) out.notify(msg.getSender().getName() + " >> " + msg.toString());
  }

  @Override
  public boolean offer(String s) {
    return input.put(s);
  }

  @Override
  public String input() throws InterruptedException {
    return input.get();
  }


  ////// private methods

  private void println(Object s) {
    String str = s.toString();
    int n = str.length();
    if (n > MAX_RESULT_LEN) str = str.substring(0,RESULT_SNIPPET_LEN) + " <<snip>> " + str.substring(n-RESULT_SNIPPET_LEN);
    log.fine("RESULT: "+str);
    // Mostly log the String version, but for messages, log it so that GUI can display details
    // Be careful not to ever log AgentIDs otherwise the toString() extensions can get called too often by GUI!
    if (out != null) out.println((s instanceof Message)?s:str);
  }

  private void error(Throwable ex) {
    if (ex instanceof GroovyBugError) ex = resolveGroovyBug(ex);
    if (out != null) out.error(ex);
    else log.log(Level.WARNING, "Groovy execution failed", ex);
  }

  private Throwable resolveGroovyBug(Throwable ex) {
    String s = ex.toString();
    if (s.contains("BUG! exception in phase 'semantic analysis'")) {
      int ndx1 = s.indexOf("The lookup for ");
      int ndx2 = s.indexOf(" caused a failed compilaton");
      if (ndx1 >= 0 && ndx2 >= ndx1) {
        String offendingClass = s.substring(ndx1+15, ndx2);
        String offendingGroovyScript = offendingClass.replace(".","/")+".groovy";
        try {
          InputStream in = groovy.getClassLoader().getResourceAsStream(offendingGroovyScript);
          if (in != null) {
            groovy.parse(new InputStreamReader(in), offendingGroovyScript);
          }
        } catch (Throwable ex1) {
          ex = ex1;
        }
      }
    }
    return ex;
  }

}
