/******************************************************************************

Copyright (c) 2016, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.concurrent.*;
import groovy.lang.*;
import groovy.transform.ThreadInterrupt;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.*;
import org.codehaus.groovy.GroovyBugError;
import org.arl.fjage.*;

/**
 * Groovy scripting engine.
 */
public class GroovyScriptEngine implements ScriptEngine {

  ////// private attributes

  private GroovyShell groovy;
  private Binding binding;
  private Shell out = null;
  private Thread busy = null;
  private Logger log = Logger.getLogger(getClass().getName());

  ////// constructor

  public GroovyScriptEngine() {
    binding = new ConcurrentBinding();
    init();
  }

  public GroovyScriptEngine(boolean protect) {
    binding = protect ? new ProtectedBinding() : new Binding();
    init();
  }

  private void init() {
    CompilerConfiguration compiler = new CompilerConfiguration();
    compiler.setScriptBaseClass(BaseGroovyScript.class.getName());
    ImportCustomizer imports = new ImportCustomizer();
    binding.setVariable("imports", imports);
    binding.setVariable("rsp", null);
    binding.setVariable("ntf", null);
    compiler.addCompilationCustomizers(imports);
    compiler.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
    GroovyClassLoader gcl = new GroovyClassLoader(getClass().getClassLoader());
    groovy = new GroovyShell(gcl, binding, compiler);
    binding.setVariable("groovy", groovy);
    groovy.evaluate("_init_()");
  }

  ////// script engine methods

  @Override
  public void bind(Shell shell) {
    out = shell;
  }

  @Override
  public String getPrompt(boolean cont) {
    return cont ? "- " : "> ";
  }

  @Override
  public boolean isComplete(String cmd) {
    if (cmd == null || cmd.trim().length() == 0) return true;
    try {
      groovy.parse(cmd);
      return true;
    } catch (Exception ex) {
      return false;
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
        else if (cmd.startsWith("import ")) cmd = "shellImport '"+cmd.substring(7)+"'";
        else if (cmd.startsWith("<")) {
          if (cmd.contains(" ")) cmd = "run('"+cmd.substring(1).replaceFirst(" ","',")+");";
          else cmd = "run('"+cmd.substring(1)+"');";
        } else {
          String sname = cmd;
          int ndx = sname.indexOf(' ');
          if (ndx > 0) sname = sname.substring(0,ndx);
          String folder = binding.hasVariable("scripts") ? (String)binding.getVariable("scripts") : null;
          File f = new File(folder, sname+".groovy");
          if (f.exists() && f.isFile())
            if (ndx > 0) cmd = "run('"+sname+"',"+cmd.substring(ndx+1)+");";
            else cmd = "run('"+cmd+"');";
        }
        log.info("EVAL: "+cmd);
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
      log.info("RUN: "+script.getAbsolutePath());
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
    if (isBusy()) return false;
    synchronized(this) {
      try {
        busy = Thread.currentThread();
        log.info("RUN: "+script.getName());
        try {
          binding.setVariable("out", out);
          binding.setVariable("script", script.getName());
          binding.setVariable("args", args);
          Script gs = (Script)script.newInstance();
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
        log.info("RUN: "+name);
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
    return binding.getVariable(name);
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

  ////// private methods

  private void println(Object s) {
    String str = s.toString();
    log.info("RESULT: "+str);
    // Mostly log the String version, but for messages, log it so that GUI can display details
    // Be careful not to ever log AgentIDs otherwise the toString() extensions can get called too often by GUI!
    if (out != null) out.println((s instanceof Message)?s:str);
  }

  private void error(Throwable ex) {
    if (ex instanceof GroovyBugError) ex = resolveGroovyBug(ex);
    log.log(Level.WARNING, "Groovy script execution failed", ex);
    if (out != null) out.error(ex);
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
          groovy.parse(new InputStreamReader(in), offendingGroovyScript);
        } catch (Throwable ex1) {
          ex = ex1;
        }
      }
    }
    return ex;
  }

}
