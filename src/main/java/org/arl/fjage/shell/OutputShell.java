/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;

/**
 * Output only shell, used for displaying messages.
 */
public class OutputShell implements Shell {
  
  ////////// Private attributes

  private PrintStream out;
  private Term term = new Term();

  ////////// Methods

  /**
   * Create an output shell to display messages.
   *
   * @param out print stream to display messages to.
   */
  public OutputShell(PrintStream out) {
    this.out = out;
  }

  /**
   * Enable terminal ANSI sequences.
   */
  public void enableTerm() {
    term.enable();
  }

  /**
   * Disable terminal ANSI sequences.
   */
  public void disableTerm() {
    term.disable();
  }

  @Override
  public void start(ScriptEngine engine) {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public void println(Object obj, OutputType type) {
    if (obj == null) return;
    String s = obj.toString();
    switch(type) {
      case RESPONSE:
        out.println(term.response(s));
        break;
      case NOTIFICATION:
        out.println(term.notification(s));
        break;
      case ERROR:
        out.println(term.error(s));
        break;
      default:
        out.println(s);
        break;
    }
  }

}
