/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

/**
 * Any shell input/output driver should implement this interface.
 */
public interface Shell {

  /**
   * Abort string returned when user presses ^C.
   */
  public static final String ABORT = "\003";

  /**
   * Initialize the shell.
   *
   * @param engine script engine to use for sentence completion check,
   *               or null to disable.
   */
  public void init(ScriptEngine engine);

  /**
   * Prompt user for input.
   */
  public void prompt(Object obj);

  /**
   * Display input line.
   */
  public void input(Object obj);

  /**
   * Display script output.
   */
  public void println(Object obj);

  /**
   * Display unsolicited notification.
   */
  public void notify(Object obj);

  /**
   * Display error.
   */
  public void error(Object obj);

  /**
   * Read a line from the shell.
   *
   * @param prompt1 prompt to display on first line, null if none.
   * @param prompt2 prompt to display on continuation line, null if none.
   * @param line input text to edit, null if blank.
   * @return input string, null on EOF, or ABORT on ^C.
   */
  public String readLine(String prompt1, String prompt2, String line);

  /**
   * Shutdown the shell.
   */
  public void shutdown();

}
