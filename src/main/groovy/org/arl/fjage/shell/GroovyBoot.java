/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.Platform;
import java.io.File;
import java.util.logging.Logger;

/**
 * fjage bootloader.
 * <p>
 * Usage:
 * <code>
 * java org.arl.fjage.shell.Boot [-nocolor] [script-file]...
 * </code>
 *
 * @author Mandar Chitre
 */
public class GroovyBoot {

  /////////// Private attributes

  private static Logger log = Logger.getLogger(GroovyBoot.class.getName());
  public static ScriptEngine engine;

  /////////// Command-line startup

  /**
   * Application entry point.
   */
  public static void main(String[] args) {
    log.info("fjage Build: "+Platform.getBuildVersion());
    try {
      engine = new GroovyScriptEngine();
      for (String a: args) {
        if (a.equals("-nocolor")) Term.setDefaultState(false);
        else {
          log.info("Running "+a);
          engine.exec(new File(a), null);
          engine.waitUntilCompletion();
        }
      }
      engine.shutdown();
    } catch (Exception ex) {
      log.severe(ex.toString());
    }
  }

}

