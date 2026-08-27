/******************************************************************************

Copyright (c) 2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.logging.Logger;
import org.arl.fjage.connectors.ConnectionListener;
import org.arl.fjage.connectors.Connector;

/**
 * Shell input/output driver for dumb console devices (with no terminal escape
 * sequences support). This shell is useful when dealing with machine-to-machine
 * connections.
 */
public class DumbShell implements Shell {

  private PrintStream out = null;
  private BufferedReader in = null;
  private Connector connector = null;

  /**
   * Create a dumb console shell attached to the system terminal.
   */
  public DumbShell() {
    this.in = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.Charset.defaultCharset()));
    this.out = System.out;
  }

  /**
   * Create a dumb console shell attached to a specified input and output stream.
   *
   * @param in input stream.
   * @param out output stream.
   */
  public DumbShell(InputStream in, OutputStream out) {
    this.in = new BufferedReader(new InputStreamReader(in, java.nio.charset.Charset.defaultCharset()));
    this.out = utf8PrintStream(out);
  }

  /**
   * Create a dumb console shell attached to a specified connector.
   *
   * @param connector input/output streams.
   */
  public DumbShell(Connector connector) {
    this.in = new BufferedReader(new InputStreamReader(connector.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
    this.out = utf8PrintStream(connector.getOutputStream());
    this.connector = connector;
  }

  @Override
  public void init(ScriptEngine engine) {
    // no initialization required
  }

  @Override
  public void prompt(Object obj) {
    // do nothing
  }

  @Override
  public void input(Object obj) {
    // do nothing
  }

  @Override
  public void println(Object obj) {
    if (out != null) out.println(obj.toString());
  }

  @Override
  public void notify(Object obj) {
    if (out != null) out.println(obj.toString());
  }

  @Override
  public void error(Object obj) {
    if (out != null) out.println(obj.toString());
  }

  @Override
  public String readLine(String prompt1, String prompt2, String line) {
    if (line != null && line.length() > 0) return line;
    if (in == null) return null;
    try {
      return in.readLine();
    } catch (IOException ex) {
      return null;
    }
  }

  @Override
  public boolean isDumb() {
    return true;
  }

  @Override
  public void shutdown() {
    if (connector != null) {
      connector.close();
      connector = null;
    }
    try {
      if (in != null) in.close();
      if (out != null) out.close();
    } catch (IOException ex) {
      // do nothing
    }
    in = null;
    out = null;
  }

  private static PrintStream utf8PrintStream(OutputStream out) {
    try {
      return new PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException ex) {
      throw new AssertionError(ex);
    }
  }

  @Override
  public String toString() {
    if (connector == null) return "console://-";
    else return connector.toString();
  }

}
