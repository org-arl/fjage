/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.*;

/**
 * A platform that runs the agents in its containers in real-time.  The notion
 * of time in this platform is that of elapsed time in the real world, and hence
 * this is a suitable platform for most applications.
 * <p>
 * Typical use of this platform is shown below:
 * <pre>
 * import org.arl.fjage.*;
 *
 * Platform platform = new RealTimePlatform();
 * Container container = new Container(platform);
 * container.add("myAgent", new myAgent());         // add appropriate agents
 * platform.start();
 * </pre>
 *
 * @author  Mandar Chitre
 */
public final class RealTimePlatform extends Platform {

  /////////// Private attributes

  private Timer timer = new Timer(true);

  /////////// Implementation methods

  @Override
  public long currentTimeMillis() {
    return System.currentTimeMillis();
  }
  
  @Override
  public long nanoTime() {
    return System.nanoTime();
  }

  @Override
  public void schedule(TimerTask task, long millis) {
    timer.schedule(task, millis);
  }

  @Override
  public void idle() {
    // do nothing
  }

  @Override
  public void delay(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}

