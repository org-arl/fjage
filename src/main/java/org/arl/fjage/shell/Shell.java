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
   * Initialize the shell.
   */
  public void init();

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
   * Alert the user.
   */
  public void alert();

  /**
   * Read a line from the shell.
   *
   * @param prompt prompt to display, null if none.
   * @param line input text to edit, null if blank.
   * @return input string or null on abort.
   */
  public String readLine(String prompt, String line);

  /**
   * Shutdown the shell.
   */
  public void shutdown();

}
