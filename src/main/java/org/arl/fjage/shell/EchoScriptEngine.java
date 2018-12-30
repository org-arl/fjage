/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.File;
import java.io.Reader;
import java.util.List;
import org.arl.fjage.Message;


/**
 * Implements a simple script engine that simply echoes whatever is sent to it.
 * This is useful for testing purposes.
 */
public class EchoScriptEngine implements ScriptEngine {

  protected Shell shell = null;
  protected boolean busy = false;

  protected void println(String s) {
    if (shell != null) shell.println(s);
  }

  @Override
  public String getPrompt(boolean cont) {
    return "# ";
  }

  @Override
  public boolean isComplete(String cmd) {
    return true;
  }

  @Override
  public void bind(Shell shell) {
    this.shell = shell;
  }

  @Override
  public boolean exec(String cmd) {
    if (busy) return false;
    busy = true;
    println(cmd);
    try {
      synchronized(this) {
        wait(500);
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    busy = false;
    return true;
  }

  @Override
  public boolean exec(File script) {
    return false;
  }

  @Override
  public boolean exec(File script, List<String> args) {
    return false;
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
    return false;
  }

  @Override
  public boolean exec(Reader reader, String name, List<String> args) {
    return false;
  }

  @Override
  public void deliver(Message msg) {
    if (shell != null) shell.notify(msg.getSender().getName() + " >> " + msg.toString());
  }

  @Override
  public boolean isBusy() {
    return busy;
  }

  @Override
  public void abort() {
    synchronized(this) {
      notify();
    }
  }

  @Override
  public void setVariable(String name, Object value) {
    // do nothing
  }

  @Override
  public Object getVariable(String name) {
    return null;
  }

  @Override
  public void importClasses(String clazz) {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

}
