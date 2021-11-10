/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell

import org.arl.fjage.*
import org.arl.fjage.param.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Methods and attributes available to Groovy scripts.
 *
 * @author Mandar Chitre
 */
@SuppressWarnings('rawtypes')
abstract class BaseGroovyScript extends Script {

  // Log levels
  static final Level ALL = Level.ALL
  static final Level FINEST = Level.FINEST
  static final Level FINER = Level.FINER
  static final Level FINE = Level.FINE
  static final Level INFO = Level.INFO
  static final Level WARNING = Level.WARNING
  static final Level SEVERE = Level.SEVERE
  static final Level OFF = Level.OFF

  /**
   * Initializes the script.  Creates a 'log' variable to allow logging from the
   * script.
   */
  void __init__() {
    Logger log = Logger.getLogger(getClass().getName())
    log.setLevel(Level.ALL)
    Binding binding = getBinding()
    binding.setVariable('log', log)
  }

  /**
   * Update list of default import classes/packages.
   *
   * @param name name of class or package to import.
   */
  def export(String name) {
    Binding binding = getBinding()
    if (binding.hasVariable('__script_engine__')) {
      GroovyScriptEngine engine = binding.getVariable('__script_engine__')
      engine.importClasses(name.trim())
    }
  }

  /**
   * Subscribe to notifications from a given topic.
   *
   * @param topic
   */
  void subscribe(topic) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      a.subscribe(topic)
    }
  }

  /**
   * Unsubscribe from notifications from a given topic.
   *
   * @param topic
   */
  void unsubscribe(topic) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      a.unsubscribe(topic)
    }
  }

  /**
   * Terminates the current platform and all containers and agents on it.
   */
  void shutdown() {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      Platform p = a.getPlatform()
      p.shutdown()
    }
  }

  def getShutdown() {
    shutdown()
    return null
  }

  /**
   * Lists all the agents.
   *
   * @return a string representation of all agents.
   */
  String ps() {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      Container c = a.getContainer()
      AgentID[] agentIDs = c.getAgents()
      StringBuffer s = new StringBuffer()
      boolean first = true
      for (AgentID aid: agentIDs) {
        if (!first) s.append('\n')
        s.append(aid)
        if (aid.type == null) s.append(': REMOTE')
        else {
          s.append(": ${aid.type}")
          if (c.getAgent(aid) == null) s.append(' [REMOTE]')
        }
        first = false
      }
      return s.toString()
    }
    return null
  }

  String getPs() {
    return ps()
  }

  /**
   * Gets the shell agent running the script.
   *
   * @return shell agent instance.
   */
  Agent agent() {
    if (binding.hasVariable('__agent__')) return binding.getVariable('__agent__')
    return null
  }

  Agent getAgent() {
    return agent()
  }

  /**
   * Represents an agent identifier for a named agent.
   *
   * @param name name of the agent.
   * @return agent identifier.
   */
  AgentID agent(String name) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      return a.agent(name)
    }
    return new AgentID(name)
  }

  /**
   * Gets the container in which the shell is running.
   */
  Container container() {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      return a.getContainer()
    }
    return null
  }

  Container getContainer() {
    return container()
  }

  /**
   * Gets the platform on which the shell is running.
   */
  Platform platform() {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      return a.getPlatform()
    }
    return null
  }

  Platform getPlatform() {
    return platform()
  }

  /**
   * Lists all the services, along with a list of agents that provide them.
   *
   * @return a string representation of all services.
   */
  String services() {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      Container c = a.getContainer()
      String[] svc = c.getServices()
      StringBuffer s = new StringBuffer()
      boolean first = true
      for (String s1: svc) {
        if (!first) s.append('\n')
        s.append(s1)
        AgentID[] aids = agentsForService(s1)
        if (aids) {
          s.append(':')
          aids.each {
            s.append(' ')
            s.append(it)
          }
        }
        first = false
      }
      return s.toString()
    }
    return null
  }

  String getServices() {
    return services()
  }

  /**
   * Returns an agent identifier for a specified service.
   *
   * @param service service of interest.
   * @return agent identifier.
   */
  AgentID agentForService(def service) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      return a.agentForService(service)
    }
    return null
  }

  /**
   * Returns agent identifiers for a specified service.
   *
   * @param service service of interest.
   * @return array of agent identifiers.
   */
  AgentID[] agentsForService(def service) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      return a.agentsForService(service)
    }
    return null
  }

 /**
  * Represents a topic for a specified agent or a named topic.
  *
  * @param s name of topic or agent identifier.
  * @return topic.
  */
  AgentID topic(s) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      return a.topic(s)
    }
    return null
  }

 /**
  * Represents a named notification topic for a specified agent.
  *
  * @param aid agent identifier.
  * @param s name of the notification topic.
  * @return topic.
  */
  AgentID topic(aid, s) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      return a.topic(aid, s)
    }
    return null
  }

  /**
   * Sets current log level for a specified logger.
   *
   * @param name name of the logger.
   * @param level log level.
   */
  void logLevel(String name, Level level) {
    Logger logger = Logger.getLogger(name)
    logger.setLevel(level)
  }

  /**
   * Sets current log level for the root logger.
   *
   * @param level log level.
   */
  void logLevel(Level level) {
    Logger logger = Logger.getLogger('')
    logger.setLevel(level)
  }

  /**
   * Delay execution by a given time.
   *
   * @param millis time in milliseconds.
   */
  void delay(long millis) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      a.delay(millis)
    }
    else Thread.sleep(millis)
  }

  /**
   * Lists variables in current binding. Variables with "__" are considered
   * reserved and are not shown.
   *
   * @return string representation of all variables.
   */
  String who() {
    Binding binding = getBinding()
    StringBuffer s = new StringBuffer()
    binding.getVariables().each {
      if (!it.key.contains('__')) {
        if (s.length() > 0) s << ', '
        s << it.key
      }
    }
    return s.toString()
  }

  String getWho() {
    return who()
  }

  @Override
  void println() {
    println('')
  }

  @Override
  void print(def x) {
    println(x)
  }

  @Override
  void printf(String format, Object value) {
    println(String.format(format, value))
  }

  @Override
  @SuppressWarnings('overrides')
  void printf(String format, Object[] value) {
    println(String.format(format, value))
  }

  /**
   * Converts a URL to a clickable link, if terminal supports it.
   *
   * @param url URL
   * @return clickable link String that can be printed to terminal
   */
  String href(String url) {
    return href(url, url)
  }

  /**
   * Converts a URL to a clickable link, if terminal supports it.
   *
   * @param url URL
   * @param text text to display instead of URL
   * @return clickable link String that can be printed to terminal
   */
  String href(String url, String text) {
    if (out == null || out.isDumb()) return text;
    return "\033]8;;" + url + "\007" + text + "\033]8;;\007";
  }

  /**
   * Run a nested Groovy script.
   *
   * @param name filename of the script to run.
   * @param args arguments to pass to the script.
   */
  void run(String name, String... args) {
    Binding binding = getBinding()
    def oldScript = binding.getVariable('script')
    def oldArgs = binding.getVariable('args')
    try {
      if (binding.hasVariable('__groovy__')) {
        GroovyShell groovy = binding.getVariable('__groovy__')
        groovy.getClassLoader().clearCache()
        if (!name.endsWith('.groovy') && !name.startsWith('cls://')) name += '.groovy'
        if (name.startsWith('res:/')) {
          InputStream inp = groovy.class.getResourceAsStream(name.substring(5))
          if (inp == null) throw new FileNotFoundException(name+' not found')
          binding.setVariable('script', name)
          groovy.run(new InputStreamReader(inp), name, args)
        } else if (name.startsWith('cls://')) {
          Class<?> cls = (Class<?>)Class.forName(name.substring(6))
          if (ShellExtension.class.isAssignableFrom(cls) && binding.hasVariable('__script_engine__')) {
            GroovyScriptEngine engine = binding.getVariable('__script_engine__')
            engine.exec(cls)
          } else {
            Script script = ((Class<Script>)cls).newInstance()
            binding.setVariable('script', cls.getName())
            binding.setVariable('args', args)
            script.setBinding(binding)
            script.run()
          }
        } else {
          List<?> arglist = new ArrayList<?>()
          if (args != null && args.length > 0)
            for (a in args)
              arglist.add(a)
          def folder = null
          if (!name.startsWith(File.pathSeparator) && binding.hasVariable('scripts'))
            folder = binding.getVariable('scripts')
          File f = folder ? new File(folder, name) : new File(name)
          binding.setVariable('script', f.getAbsoluteFile())
          groovy.run(f, arglist)
        }
      }
    } finally {
      binding.setVariable('script', oldScript)
      binding.setVariable('args', oldArgs)
    }
  }

  /**
   * Run a nested Groovy script.
   *
   * @param file script to run.
   * @param args arguments to pass to the script.
   */
  @Override
  @SuppressWarnings('overrides')
  void run(File file, String... args) {
    Binding binding = getBinding()
    def oldScript = binding.getVariable('script')
    def oldArgs = binding.getVariable('args')
    try {
      if (binding.hasVariable('__groovy__')) {
        GroovyShell groovy = binding.getVariable('__groovy__')
        groovy.getClassLoader().clearCache()
        List<?> arglist = new ArrayList<?>()
        if (args != null && args.length > 0)
          for (a in args)
            arglist.add(a.toString())
        binding.setVariable('script', file.getAbsoluteFile())
        groovy.run(file, arglist)
      }
    } finally {
      binding.setVariable('script', oldScript)
      binding.setVariable('args', oldArgs)
    }
  }

  /**
   * Run a nested Groovy script.
   *
   * @param reader reader to obtain the script from.
   * @param name name of the script to run.
   * @param args arguments to pass to the script.
   */
  void run(Reader reader, String name, String... args) {
    Binding binding = getBinding()
    if (binding.hasVariable('__groovy__')) {
      GroovyShell groovy = binding.getVariable('__groovy__')
      groovy.getClassLoader().clearCache()
      groovy.run(reader, name, args)
    }
  }

  /**
   * Show help on a given Groovy object, if available in the documentation database.
   *
   * @param obj Groovy object to get help on.
   * @return help text, or null if none available.
   */
  String help(Object obj) {
    Binding binding = getBinding()
    if (binding.hasVariable('__doc__')) {
      def doc = binding.getVariable('__doc__')
      return doc.get(obj as String)
    }
    return null
  }

  /**
   * Show brief help on all Groovy objects available in the documentation database.
   *
   * @return help text, or null if none available.
   */
  String getHelp() {
    Binding binding = getBinding()
    if (binding.hasVariable('__doc__')) {
      def doc = binding.getVariable('__doc__')
      return doc.get()
    }
    return null
  }

  /**
   * Send an agent message.
   *
   * @param msg message to send.
   * @return true on success, false on failure.
   */
  boolean send(Message msg) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      Agent a = binding.getVariable('__agent__')
      return a.send(msg)
    }
    return false
  }

   /**
    * Send an agent message and waits for a response.
    *
    * @param msg message to send.
    * @param timeout timeout in milliseconds.
    * @return response, if available, null otherwise.
    */
   Message request(Message msg, long timeout = 1000) {
     Binding binding = getBinding()
     if (binding.hasVariable('__agent__')) {
       ShellAgent a = binding.getVariable('__agent__')
       return a.request(msg, timeout)
     }
     return null
   }

  /**
   * Receives an agent message.
   *
   * @param cls class of message.
   * @param timeout timeout in milliseconds.
   * @return received message, if available, null otherwise.
   */
  Message receive(Class cls, long timeout = 1000) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      ShellAgent a = binding.getVariable('__agent__')
      return a.receive(cls, timeout)
    }
    return null
  }

  /**
   * Receives an agent message.
   *
   * @param timeout timeout in milliseconds.
   * @return received message, if available, null otherwise.
   */
  Message receive(long timeout = 1000) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      ShellAgent a = binding.getVariable('__agent__')
      return a.receive(timeout)
    }
    return null
  }

  /**
   * Receives an agent message.
   *
   * @param filter message filter.
   * @param timeout timeout in milliseconds.
   * @return received message, if available, null otherwise.
   */
  Message receive(Closure filter, long timeout = 1000) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      ShellAgent a = binding.getVariable('__agent__')
      MessageFilter f = new MessageFilter() {
        boolean matches(Message m) {
          return filter(m)
        }
      }
      return a.receive(f, timeout)
    }
    return null
  }

  /**
   * Receives an agent message.
   *
   * @param msg original message to which a response is expected.
   * @param timeout timeout in milliseconds.
   * @return received message, if available, null otherwise.
   */
  Message receive(Message msg, long timeout = 1000) {
    Binding binding = getBinding()
    if (binding.hasVariable('__agent__')) {
      ShellAgent a = binding.getVariable('__agent__')
      MessageFilter f = new MessageFilter() {
        private String mid = msg.getMessageID()
        boolean matches(Message m) {
          String s = m.getInReplyTo()
          if (s == null) return false
          return s.equals(mid)
        }
      }
      return a.receive(f, timeout)
    }
    return null
  }

  /**
   * Gets user input.
   *
   * @param prompt prompt to display, or null.
   * @param hide true to hide input after entering.
   * @return string input from user.
   */
  String input(String prompt = null, boolean hide = false) {
    Binding binding = getBinding()
    if (binding.hasVariable('__input__') && binding.hasVariable('out')) {
      def input = binding.getVariable('__input__')
      def out = binding.getVariable('out')
      if (prompt) out.prompt(prompt)
      String s = input.get()
      if (!hide && s) out.input(s)
      return s
    }
    return null
  }

  /**
   * Checks is a named variable is defined in the shell.
   *
   * @param varname name of the variable.
   * @return true if defined, false otherwise.
   */
  boolean defined(String varname) {
    Binding binding = getBinding()
    return binding.hasVariable(varname)
  }

  /**
   * Try executing a named script if a command/property does not exist.
   */
  def propertyMissing(String name) {
    Binding binding = getBinding()
    def a = agent(name)
    if (getContainer()?.canLocateAgent(a)) return a
    try {
      if (binding.hasVariable('__groovy__')) {
        GroovyShell groovy = binding.getVariable('__groovy__')
        return groovy.evaluate(name+'()')
      }
    } catch (FjageException ex) {
      // rethrow this below
    }
    throw new FjageException("Unknown command or property: ${name}")
  }

  /**
   * Try executing a named script if a command/property does not exist.
   */
  void methodMissing(String name, args) {
    try {
      run(name, args as String[])
    } catch (FileNotFoundException ex) {
      throw new FjageException("Unknown method: ${name}(...)")
    }
  }

}
