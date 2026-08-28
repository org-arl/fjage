/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.logging.*;

/**
 * Utility class to format log entries for brief console display.
 * @author  Mandar Chitre
 */
public class BriefLogFormatter extends Formatter {

  /**
   * Formats the logs.
   * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
   */
  @Override
  public String format(LogRecord record) {
    StringBuilder s = new StringBuilder();
    s.append("\033[31m");                   // terminal code for RED color
    s.append(record.getLevel());
    s.append(": ");
    s.append(record.getLoggerName());
    s.append(" > ");
    s.append(record.getMessage());
    s.append("\033[0m");                    // RESET terminal
    s.append('\n');
    return s.toString();
  }

}
