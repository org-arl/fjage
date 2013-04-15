/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.logging.*;
import org.arl.fjage.Platform;

/**
 * fjage bootloader.
 * <p>
 * Usage:
 * <code>
 * java [-Djava.util.logging.config.file=logging.properties] org.arl.fjage.shell.Boot [-nocolor] [-debug:package-name] [script-file]...
 * </code>
 *
 * @author Mandar Chitre
 */
public class GroovyBoot {

  private static final String loggingProperties = "/etc/logging.properties";

  /**
   * Application entry point.
   */
  public static void main(String[] args) {
    Logger log = null;
    try {

      // load logging configuration from fjage defaults, if not specified
      if (System.getProperty("java.util.logging.config.file") == null
       && System.getProperty("java.util.logging.config.class") == null) {
        InputStream logprop = GroovyBoot.class.getResourceAsStream(loggingProperties);
        if (logprop == null) throw new FileNotFoundException("res://"+loggingProperties+" not found");
        LogManager.getLogManager().readConfiguration(logprop);
      }
      log = Logger.getLogger(GroovyBoot.class.getName());

      // load build details
      log.info("fjage Build: "+Platform.getBuildVersion());

      // parse command line and execute scripts
      String pat = "^res://([^/]*)(/.*)$";
      ScriptEngine engine = new GroovyScriptEngine();
      for (String a: args) {
        if (a.equals("-nocolor")) Term.setDefaultState(false);
        else if (a.startsWith("-debug:")) {
          String lname = a.substring(7);
          Logger.getLogger(lname).setLevel(Level.ALL);
        } else {
          log.info("Running "+a);
          if (a.matches(pat)) {
            // execute script from resource file
            String clsname = a.replaceAll(pat, "$1");
            String res = a.replaceAll(pat, "$2");
            Class cls = GroovyBoot.class;
            if (clsname.length() > 0) cls = Class.forName(clsname);
            InputStream inp = cls.getResourceAsStream(res);
            if (inp == null) throw new FileNotFoundException(a+" not found");
            engine.exec(new InputStreamReader(inp), a, null);
          } else {
            // execute script from file
            engine.exec(new File(a), null);
          }
          engine.waitUntilCompletion();
        }
      }
      engine.shutdown();

    } catch (Exception ex) {
      if (log == null) ex.printStackTrace(System.err);
      else log.severe(ex.toString());
    }
  }

}

