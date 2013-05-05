/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;

/**
 * Script output handler.
 */
public class ScriptOutputStream {
  
  ////// private attributes
  
  private PrintStream ps = null;
  private Term term = new Term();
  private String prompt = null;
  private boolean telnet = false;
  
  ////// public methods

  /**
   * Create an unbound script output stream.
   */
  ScriptOutputStream() {
    // do nothing
  }

  /**
   * Create a script output stream bound to an output stream.
   *
   * @param out output stream to bind to.
   */
  ScriptOutputStream(OutputStream out) {
    setOutputStream(out);
  }

  /**
   * Set the output stream to use for displaying script output.
   * 
   * @param out output stream, or null if no display is desired.
   */
  public synchronized void setOutputStream(OutputStream out) {
    if (out == null) ps = null;
    else ps = new PrintStream(out);
  }

  /**
   * Enable/disable translation of CR/LF for telnet connections.
   * 
   * @param enable enable telnet mode if true, disable if false.
   */
  public void setTelnet(boolean enable) {
    telnet = enable;
  }

  /**
   * Output a string followed by a newline.
   * 
   * @param s string to output.
   */
  public synchronized void println(String s) {
    if (ps == null) return;
    if (telnet) s = s.replace("\n","\r\n");
    ps.println(s);
    ps.flush();
  }

  /**
   * Output a string.
   * 
   * @param s string to output.
   */
  public synchronized void print(String s) {
    if (ps == null) return;
    if (telnet) s = s.replace("\n","\r\n");
    ps.print(s);
    ps.flush();
  }

  /**
   * Set prompt to be displayed when eos() is called.
   * 
   * @param s prompt to display, null to disable.
   */
  public void setPrompt(String s) {
    prompt = s;
  }

  /**
   * Output an end-of-script indicator.
   */
  public synchronized void eos() {
    if (ps == null) return;
    if (prompt != null) {
      if (telnet) ps.print("\r");
      ps.print(prompt);
    }
    ps.flush();
  }

  /**
   * Get the terminal in use.
   * 
   * @return the current terminal.
   */
  public Term getTerm() {
    return term;
  }

}

