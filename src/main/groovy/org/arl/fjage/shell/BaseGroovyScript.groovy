/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.util.logging.*;
import org.arl.fjage.*;
import org.codehaus.groovy.control.customizers.ImportCustomizer

/**
 * Methods and attributes available to Groovy scripts.
 *
 * @author Mandar Chitre
 */
abstract class BaseGroovyScript extends Script {

  // Log levels
  static final Level ALL = Level.ALL;
  static final Level FINEST = Level.FINEST;
  static final Level FINER = Level.FINER;
  static final Level FINE = Level.FINE;
  static final Level INFO = Level.INFO;
  static final Level WARNING = Level.WARNING;
  static final Level SEVERE = Level.SEVERE;
  static final Level OFF = Level.OFF;
  
  /**
   * Initializes the script.  Creates a 'log' variable to allow logging from the
   * script. Also creates a 'doc' hash map in suppport of 'help' commands.
   */
  void _init_() {
    Logger log = Logger.getLogger(getClass().getName());
    log.setLevel(Level.ALL);
    Binding binding = getBinding();
    binding.setVariable('log', log);
    def doc = [:];
    binding.setVariable('doc', doc);
  }

  /**
   * Clean up after the script.  Currently does nothing.
   */
  void _cleanup_() {
    // do nothing
  }
  
  /**
   * Update list of default import classes/packages.
   *
   * @param name name of class or package to import.
   */
  def shellImport(String name) {
    Binding binding = getBinding();
    if (binding.hasVariable('imports')) {
      ImportCustomizer imports = binding.getVariable('imports');
      if (name.endsWith('.*')) imports.addStarImport(name[0..-3]);
      else imports.addImport(name);
    }
  }

  /**
   * Do not use include(), use shellImport() instead.
   */
  def include(String name) {
    throw new FjageError('include() has been superceded by shellImport()');
  }

  /**
   * Subscribe to notifications from a given topic.
   *
   * @param topic
   */
  void subscribe(topic) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      a.subscribe(topic);
    }
  }

  /**
   * Unsubscribe from notifications from a given topic.
   *
   * @param topic
   */
  void unsubscribe(topic) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      a.unsubscribe(topic);
    }
  }

  /**
   * Terminates the current platform and all containers and agents on it.
   */
  void shutdown() {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      Platform p = a.getPlatform();
      p.shutdown();
      binding.setVariable('agent', null);
    }
  }

  def getShutdown() {
    shutdown();
    return null;
  }

  /**
   * Lists all the agents running on a specified container.
   *
   * @return a string representation of all agents and their states.
   */
  String ps() {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      Container c = a.getContainer();
      return psContainer(c);
    }
    return null;
  }

  private String psContainer(Container c) {
    Agent[] agents = c.getAgents();
    StringBuffer s = new StringBuffer();
    boolean first = true;
    for (Agent a: agents) {
      if (!first) s.append('\n');
      s.append(a.getAgentID());
      s.append(': ');
      s.append(a.getClass().getName());
      s.append(' - ');
      s.append(a.getState());
      first = false;
    }
    return s.toString();
  }

  String getPs() {
    return ps();
  }

  /**
   * Represents an agent identifier for a named agent.
   *
   * @param name name of the agent.
   * @return agent identifier.
   */
  AgentID agent(String name) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      return a.agent(name);
    }
    return new AgentID(name);
  }
  
  /**
   * Returns an agent identifier for a specified service.
   *
   * @param service service of interest.
   * @return agent identifier.
   */
  AgentID agentForService(def service) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      return a.agentForService(service);
    }
    return null;
  }
  
  /**
   * Returns agent identifiers for a specified service.
   *
   * @param service service of interest.
   * @return array of agent identifiers.
   */
  AgentID[] agentsForService(def service) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      return a.agentsForService(service);
    }
    return null;
  }

 /**
  * Represents a topic for a specified agent or a named topic.
  * 
  * @param s name of topic or agent identifier.
  * @return topic.
  */
  AgentID topic(s) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      return a.topic(s);
    }
    return null;
  }

  /**
   * Sets current log level for a specified logger.
   *
   * @param name name of the logger.
   * @param level log level.
   */
  void logLevel(String name, Level level) {
    Logger logger = Logger.getLogger(name);
    logger.setLevel(level);
  }

  /**
   * Sets current log level for the root logger.
   *
   * @param level log level.
   */
  void logLevel(Level level) {
    Logger logger = Logger.getLogger('');
    logger.setLevel(level);
  }

  /**
   * Delay execution by a given time.
   *
   * @param millis time in milliseconds.
   */
  void delay(long millis) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      Platform p = a.getPlatform();
      p.delay(millis);
    }
    else Thread.sleep(millis);
  }

  /**
   * Lists variables in current binding.
   * 
   * @return string representation of all variables.
   */
  String who() {
    Binding binding = getBinding();
    StringBuffer s = new StringBuffer();
    binding.getVariables().each {
      if (s.length() > 0) s << ', ';
      s << it.key;
    }
    return s.toString();
  }
  
  String getWho() {
    return who();
  }
  
  /**
   * Display on console. This method clears the current line
   * and displays output on it, followed by a newline.
   * 
   * @param s object to display.
   */
  void println(def x) {
    Binding binding = getBinding();
    if (binding.hasVariable('out')) {
      def out = binding.getVariable('out');
      if (out != null) out.println(x.toString(), OutputType.OUTPUT);
    }
  }

  /**
   * Display on console. This method clears the current line
   * and displays output on it, followed by a newline.
   * 
   * @param s object to display.
   * @param type type of output to display.
   */
  void println(def x, OutputType type) {
    Binding binding = getBinding();
    if (binding.hasVariable('out')) {
      def out = binding.getVariable('out');
      if (out != null) out.println(x.toString(), type);
    }
  }

  /**
   * Do not use print(), use println() only.
   */
  void print(def x) {
    throw new FjageError("print() not supported, use println() instead");
  }

  /**
   * Run a nested Groovy script.
   * 
   * @param name filename of the script to run.
   * @param args arguments to pass to the script.
   */
  void run(String name, Object... args) {
    Binding binding = getBinding();
    def oldScript = binding.getVariable('script');
    try {
      if (binding.hasVariable('groovy')) {
        GroovyShell groovy = binding.getVariable('groovy');
        groovy.getClassLoader().clearCache();
        if (!name.endsWith('.groovy')) name += '.groovy';
        if (name.startsWith('res:/')) {
          InputStream inp = groovy.class.getResourceAsStream(name.substring(5));
          if (inp == null) throw new FileNotFoundException(name+" not found");
          binding.setVariable('script', name);
          groovy.run(new InputStreamReader(inp), name, args as String[]);
        } else {
          List<?> arglist = new ArrayList<?>();
          if (args != null && args.length > 0)
            for (a in args)
              arglist.add(a.toString());
          String folder = null;
          if (!name.startsWith(File.pathSeparator) && binding.hasVariable('scripts'))
            folder = binding.getVariable('scripts');
          File f = new File((String)folder, name);
          binding.setVariable('script', f.getAbsoluteFile());
          groovy.run(f, arglist);
        }
      }
    } finally {
      binding.setVariable('script', oldScript);
    }
  }
  
  /**
   * Run a nested Groovy script.
   * 
   * @param reader reader to obtain the script from.
   * @param name name of the script to run.
   * @param args arguments to pass to the script.
   */
  void run(Reader reader, String name, Object... args) {
    Binding binding = getBinding();
    if (binding.hasVariable('groovy')) {
      GroovyShell groovy = binding.getVariable('groovy');
      groovy.getClassLoader().clearCache();
      groovy.run(reader, name, args as String[]);
    }
  }
  
  /**
   * Show help on a given Groovy object, if available in the documentation database.
   * 
   * @param obj Groovy object to get help on.
   * @return help text, or null if none available.
   */
  String help(Object obj) {
    Binding binding = getBinding();
    if (binding.hasVariable('doc')) {
      def doc = binding.getVariable('doc');
      String s = doc[obj];
      if (s != null && s.startsWith('@')) s = s.substring(1);
      return s;
    }
    return null;
  }

  /**
   * Show brief help on all Groovy objects available in the documentation database.
   * 
   * @return help text, or null if none available.
   */
  String getHelp() {
    Binding binding = getBinding();
    if (binding.hasVariable('doc')) {
      def doc = binding.getVariable('doc');
      StringBuffer s = new StringBuffer();
      doc.each {
        if (!it.value.startsWith('@')) {
          if (it.value.endsWith('\n')) s << it.value.find(/^[^\n]*\n/);
          else s << "${it.value}\n";
        }
      }
      return s.toString();
    }
    return null;
  }

  /**
   * Show brief help on Groovy objects with given prefix available in the
   * documentation database.
   * 
   * @param prefix prefix to look for.
   * @return help text, or null if none available.
   */
  String getHelp(String prefix) {
    Binding binding = getBinding();
    if (binding.hasVariable('doc')) {
      def doc = binding.getVariable('doc');
      StringBuffer s = new StringBuffer();
      doc.each {
        if (it.key.startsWith(prefix)) {
          String s1 = it.value;
          if (s1.startsWith('@')) s1 = '  '+s1.substring(1);
          if (s1.endsWith('\n')) s << s1.find(/^[^\n]*\n/);
          else s << "${s1}\n";
        }
      }
      return s.toString();
    }
    return null;
  }
  
  /**
   * Send an agent message.
   * 
   * @param msg message to send.
   * @return true on success, false on failure.
   */
  boolean send(Message msg) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      Agent a = binding.getVariable('agent');
      return a.send(msg);
    }
    return false;
  }

   /**
    * Send an agent message and waits for a response.
    *
    * @param msg message to send.
    * @param timeout timeout in milliseconds.
    * @return response, if available, null otherwise.
    */
   Message request(Message msg, long timeout = 1000) {
     Binding binding = getBinding();
     if (binding.hasVariable('agent')) {
       ShellAgent a = binding.getVariable('agent');
       return a.request(msg, timeout);
     }
     return null;
   }
  
  /**
   * Receives an agent message.
   *
   * @param cls class of message.
   * @param timeout timeout in milliseconds.
   * @return received message, if available, null otherwise.
   */
  Message receive(Class cls, long timeout = 1000) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      ShellAgent a = binding.getVariable('agent');
      return a.receive(cls, timeout);
    }
    return null;
  }

  /**
   * Receives an agent message.
   *
   * @param timeout timeout in milliseconds.
   * @return received message, if available, null otherwise.
   */
  Message receive(long timeout = 1000) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      ShellAgent a = binding.getVariable('agent');
      return a.receive(timeout);
    }
    return null;
  }

  /**
   * Receives an agent message.
   *
   * @param filter message filter.
   * @param timeout timeout in milliseconds.
   * @return received message, if available, null otherwise.
   */
  Message receive(Closure filter, long timeout = 1000) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      ShellAgent a = binding.getVariable('agent');
      MessageFilter f = new MessageFilter() {
        boolean matches(Message m) {
          return filter(m);
        }
      };
      return a.receive(f, timeout);
    }
    return null;
  }

  /**
   * Receives an agent message.
   *
   * @param msg original message to which a response is expected.
   * @param timeout timeout in milliseconds.
   * @return received message, if available, null otherwise.
   */
  Message receive(Message msg, long timeout = 1000) {
    Binding binding = getBinding();
    if (binding.hasVariable('agent')) {
      ShellAgent a = binding.getVariable('agent');
      MessageFilter f = new MessageFilter() {
        private String mid = msg.getMessageID();
        boolean matches(Message m) {
          String s = m.getInReplyTo();
          if (s == null) return false;
          return s.equals(mid);
        }
      };
      return a.receive(f, timeout);
    }
    return null;
  }

  /**
   * Checks is a named variable is defined in the shell.
   *
   * @param varname name of the variable
   * @return true if defined, false otherwise
   */
  boolean defined(String varname) {
    Binding binding = getBinding();
    return binding.hasVariable(varname)
  }

}
