/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.logging.*;
import org.arl.fjage.shell.Term;

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
    StringBuffer s = new StringBuffer();
    s.append(Term.YELLOW);
    s.append(record.getLevel());
    s.append(": ");
    s.append(record.getLoggerName());
    s.append(" > ");
    s.append(record.getMessage());
    Throwable t = record.getThrown();
    if (t != null) {
      s.append("\n  ");
      s.append(t.getClass().getName());
      s.append(": ");
      s.append(t.getMessage());
    }
    s.append(Term.RESET);
    s.append('\n');
    return s.toString();
  }

}

