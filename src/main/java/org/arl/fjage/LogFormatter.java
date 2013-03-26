/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.io.*;
import java.util.logging.*;

/**
 * Utility class to format log entries. This formatter creates log entries
 * with a line per log entry with pipe-separated fields.
 *
 * @author  Mandar Chitre
 * @version $Revision: 9899 $, $Date: 2012-11-03 01:59:58 +0800 (Sat, 03 Nov 2012) $
 */
public class LogFormatter extends Formatter {

  /**
   * Formats the logs.
   *
   * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
   */
  @Override
  public String format(LogRecord record) {
    StringBuffer s = new StringBuffer();
    s.append(record.getMillis());
    s.append('|');
    s.append(record.getLevel());
    s.append('|');
    s.append(record.getLoggerName());
    s.append('@');
    s.append(record.getThreadID());
    s.append('|');
    s.append(indent(record.getMessage()));
    s.append('\n');
    Throwable t = record.getThrown();
    if (t != null) {
      StringWriter sw = new StringWriter();
      t.printStackTrace(new PrintWriter(sw));
      s.append('\t');
      s.append(indent(sw.toString()));
      s.append('\n');
    }
    return s.toString();
  }

  /**
   * Installs this formatter for all handlers in the root logger.
   *
   * @param log logger to install on, null for root logger.
   */
  public static void install(Logger log) {
    if (log == null) log = Logger.getLogger("");
    LogFormatter f = new LogFormatter();
    Handler[] h = log.getHandlers();
    for (Handler h1: h)
      h1.setFormatter(f);
  }
  
  /**
   * Indent multiline logs. The first line in the string is not indented.
   *
   * @param s message to indent.
   * @return indented message.
   */
  private static String indent(String s) {
    if (s.indexOf('\n') < 0) return s;
    return s.replaceAll("\n", "\n\t");
  }

}

