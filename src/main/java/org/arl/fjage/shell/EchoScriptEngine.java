/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

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

}
