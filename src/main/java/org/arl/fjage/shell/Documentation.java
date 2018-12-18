/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.util.*;

public class Documentation {

  protected List<String> doc = new ArrayList<String>();

  /**
   * Add markdown documentation.
   *
   * @param s multiline string.
   */
  public void add(String s) {
    String lines[] = s.split("\\r?\\n");
    for (String line: lines)
      doc.add(line);
  }

  /**
   * Get documentation index.
   */
  public String get() {
    StringBuffer sb = new StringBuffer();
    for (String s: doc) {
      if (s.startsWith("# ")) {
        sb.append(s);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  /**
   * Get documentation by keyword.
   *
   * @param keyword keyword.
   */
  public String get(String keyword) {
    // TODO fix this
    StringBuffer sb = new StringBuffer();
    for (String s: doc) {
      if (s.contains(keyword)) {
        sb.append(s);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

}
