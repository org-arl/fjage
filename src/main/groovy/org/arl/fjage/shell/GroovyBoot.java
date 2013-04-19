/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.logging.*;
import groovy.lang.ExpandoMetaClass;
import org.arl.fjage.*  ;

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

  private static final String loggingProperties = "logging.properties";

  /**
   * Application entry point.
   */
  public static void main(String[] args) {
    Logger log = null;
    try {

      // setup Groovy extensions
      ExpandoMetaClass.enableGlobally();
      GroovyExtensions.enable();

      // load logging configuration from fjage defaults, if not specified
      if (System.getProperty("java.util.logging.config.file") == null
       && System.getProperty("java.util.logging.config.class") == null) {
        InputStream logprop = GroovyBoot.class.getResourceAsStream(loggingProperties);
        if (logprop == null) throw new FileNotFoundException("res://org/arl/fjage/shell/"+loggingProperties+" not found");
        LogManager.getLogManager().readConfiguration(logprop);
      }
      log = Logger.getLogger(GroovyBoot.class.getName());

      // load build details
      log.info("fjage Build: "+Platform.getBuildVersion());

      // parse command line and execute scripts
      ScriptEngine engine = new GroovyScriptEngine();
      ScriptOutputStream out = new ScriptOutputStream(System.out);
      for (String a: args) {
        if (a.equals("-nocolor")) Term.setDefaultState(false);
        else if (a.startsWith("-debug:")) {
          String lname = a.substring(7);
          Logger.getLogger(lname).setLevel(Level.ALL);
        } else {
          if (!a.endsWith(".groovy")) a += ".groovy";
          log.info("Running "+a);
          if (a.startsWith("res:/")) {
            // execute script from resource file
            InputStream inp = GroovyBoot.class.getResourceAsStream(a.substring(5));
            if (inp == null) throw new FileNotFoundException(a+" not found");
            engine.exec(new InputStreamReader(inp), a, out);
          } else {
            // execute script from file
            engine.exec(new File(a), out);
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
