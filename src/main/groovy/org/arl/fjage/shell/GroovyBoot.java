/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.GroovyExtensions;
import org.arl.fjage.Platform;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * fjage bootloader.
 * <p>
 * Usage:
 * <code>
 * java [-Djava.util.logging.config.file=logging.properties] org.arl.fjage.shell.GroovyBoot [-debug:package-name] [[-arg:arg]... script-file]...
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
    List<Logger> loggers = new ArrayList<Logger>();
    try {

      // setup Groovy extensions
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
      GroovyScriptEngine engine = new GroovyScriptEngine();
      List<String> arglist = new ArrayList<String>();
      for (String a: args) {
        if (a.equals("-debug")) {
          log.info("Setting root logger level to ALL");
          Logger logger = Logger.getLogger("");
          logger.setLevel(Level.ALL);
          loggers.add(logger);  // keep reference to avoid the level setting being garbage collected
        } else if (a.startsWith("-debug:")) {
          String lname = a.substring(7);
          log.info("Setting logger "+lname+" level to ALL");
          Logger logger = Logger.getLogger(lname);
          logger.setLevel(Level.ALL);
          loggers.add(logger);  // keep reference to avoid the level setting being garbage collected
        } else if (a.startsWith("-arg:")) {
          arglist.add(a.substring(5));
        } else {
          if (!a.endsWith(".groovy") && !a.startsWith("cls://")) a += ".groovy";
          log.info("Running "+a);
          if (a.startsWith("res:/")) {
            // execute script from resource file
            InputStream inp = GroovyBoot.class.getResourceAsStream(a.substring(5));
            if (inp == null) throw new FileNotFoundException(a+" not found");
            engine.exec(new InputStreamReader(inp), a, arglist);
            if (!arglist.isEmpty()) arglist = new ArrayList<String>();
          } else if (a.startsWith("cls://")) {
            // execute pre-compiled script from class file
            Class<?> cls = Class.forName(a.substring(6));
            engine.exec(cls, arglist);
          } else {
            // execute script from file
            engine.exec(new File(a), arglist);
            if (!arglist.isEmpty()) arglist = new ArrayList<String>();
          }
        }
      }
      engine.shutdown();

    } catch (Throwable ex) {
      if (log == null) ex.printStackTrace(System.err);
      else log.log(Level.SEVERE, "", ex);
    }
  }

}
