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
      // terminal code for RED color
      // RESET terminal
      return "\033[31m" +                   // terminal code for RED color
              record.getLevel() +
              ": " +
              record.getLoggerName() +
              " > " +
              record.getMessage() +
              "\033[0m" +                    // RESET terminal
              '\n';
  }

}
