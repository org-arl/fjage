/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import groovy.lang.*;

/**
 * A binding class that makes closures read-only once defined. This behavior can
 * be disabled by explicitly setting 'protection = false'. The behavior also does
 * not apply to a special variable named 'ans'.
 */
public class ProtectedBinding extends Binding {
  
  private boolean protection = true;

  @Override
  public void setVariable(String name, Object value) {
    if (protection && hasVariable(name) && !name.equals("ans")) {
      Object oldValue = getVariable(name);
      if (oldValue instanceof Closure) throw new RuntimeException("Closure "+name+" is read only");
    }
    if (name.equals("protection")) protection = (Boolean)value;
    super.setVariable(name, value);
  }
  
}

