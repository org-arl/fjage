/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import groovy.lang.*;
import groovy.transform.ThreadInterrupt;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.*;

/**
 * Groovy scripting engine.
 */
public class GroovyScriptEngine extends Thread implements ScriptEngine {
  
  ////// private attributes

  private GroovyShell groovy;
  private Binding binding;
  private ScriptOutputStream out = null;
  private String cmd = null;
  private File script = null;
  private List<String> args = null;
  private Object result = null;
  private boolean busy = false;
  private boolean quit = false;
  private Object done = new Object();
  private Logger log = Logger.getLogger(getClass().getName());
  
  ////// constructor

  public GroovyScriptEngine() {
    binding = new Binding();
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
    setDaemon(true);
    setName(getClass().getSimpleName());
    start();
  }
  
  ////// thread implementation

  public void run() {
    while (!quit) {
      try {
        synchronized (this) {
          if (!busy) wait();
        }
        if (cmd != null) {
          cmd = cmd.trim();
          if (cmd.startsWith("help ")) cmd = "help '"+cmd.substring(5)+"'";
          if (cmd.startsWith("<")) {
            if (cmd.contains(" ")) cmd = "run('"+cmd.substring(1).replaceFirst(" ","',")+");";
            else cmd = "run('"+cmd.substring(1)+"');";
          }
          log.info("EVAL: "+cmd);
          result = groovy.evaluate(cmd);
          if (result != null && result instanceof Closure && !cmd.endsWith("}") && !cmd.endsWith("};")) {
            // try calling returned closures with no arguments if they take a single argument
            Closure<?> c = (Closure<?>)result;
            if (c.getMaximumNumberOfParameters() == 1) result = c.call();
          }
          binding.setVariable("ans", result);
          if (result != null && !cmd.endsWith(";")) println(groovy.evaluate("ans.toString()").toString());
        } else if (script != null) {
          if (args == null) args = new ArrayList<String>();
          log.info("RUN: "+script.getAbsolutePath());
          groovy.getClassLoader().clearCache();
          result = groovy.run(script, args);
          //if (result != null) println(result.toString());
        }
      } catch (Exception ex) {
        error(ex);
      }
      cmd = null;
      script = null;
      args = null;
      busy = false;
      if (out != null) out.eos();
      synchronized (done) {
        done.notifyAll();
      }
    }
    groovy.evaluate("_cleanup_()");
  }
  
  ////// script engine methods
  
  @Override
  public synchronized boolean exec(String cmd, ScriptOutputStream out) {
    if (busy) return false;
    this.cmd = cmd;
    result = null;
    binding.setVariable("out", out);
    this.out = out;
    busy = true;
    notify();
    return true;
  }

  @Override
  public synchronized boolean exec(File script, ScriptOutputStream out) {
    if (busy) return false;
    this.script = script;
    args = null;
    result = null;
    binding.setVariable("out", out);
    this.out = out;
    busy = true;
    notify();
    return true;
  }

  @Override
  public synchronized boolean exec(File script, List<String> args, ScriptOutputStream out) {
    if (busy) return false;
    this.script = script;
    this.args = args;
    result = null;
    binding.setVariable("out", out);
    this.out = out;
    busy = true;
    notify();
    return true;
  }

  @Override
  public boolean isBusy() {
    return busy;
  }

  @Override
  public void abort() {
    log.info("ABORT");
    if (busy) interrupt();
  }

  @Override
  public void waitUntilCompletion() {
    synchronized (done) {
      try {
        done.wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public synchronized Object getResult() {
    Object rv = result;
    result = null;
    return rv;
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
  public synchronized void shutdown() {
    quit = true;
    notify();
  }

  ////// private methods
  
  private void println(String s) {
    log.info("RESULT: "+s);
    if (out != null) {
      Term t = out.getTerm();
      out.println(t.response(s));
    }
  }

  private void error(Throwable ex) {
    log.log(Level.WARNING, "Exception in Groovy script", ex);
    if (out != null) {
      Term t = out.getTerm();
      out.println(t.error(ex.toString()));
    }
  }

}

