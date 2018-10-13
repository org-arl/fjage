/******************************************************************************

Copyright (c) 2016, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import groovy.lang.*;

/**
 * A binding class that is thread-safe.
 */
public class ConcurrentBinding extends Binding {
  
  @Override
  public synchronized boolean hasVariable(String name) {
    return super.hasVariable(name);
  }
  
  @Override
  public synchronized Object getVariable(String name) {
    return super.getVariable(name);
  }
  
  @Override
  public synchronized void setVariable(String name, Object value) {
    super.setVariable(name, value);
  }

}
