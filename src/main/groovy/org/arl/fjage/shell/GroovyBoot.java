/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.File;
import java.util.logging.Logger;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

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
    log.info("fjage Build: "+getBuildInfo());
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

  /**
   * Get build information from JAR.
   */
  public static String getBuildInfo() {
    try {
      Class<?> cls = GroovyBoot.class;
      URL res = cls.getResource(cls.getSimpleName() + ".class");
      JarURLConnection conn = (JarURLConnection) res.openConnection();
      Manifest mf = conn.getManifest();
      Attributes a = mf.getMainAttributes();
      return "jaf-"+a.getValue("Build-Version")+"/"+a.getValue("Build-Timestamp");
    } catch (Exception ex) {
      return "(unknown)";
    }
  }

}

