/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell

/**
 * Basic help for shell commands.
 */
@groovy.transform.CompileStatic
class ShellDoc implements ShellExtension {

static final public String __doc__ = '''\

# shell - basic shell commands

## help - provide help on a specified topic

Usage:
  help [topic]

Examples:
  help                            // get help index
  help shell                      // get help on 'shell'
  help('shell')                   // alternative syntax

## ps - list all the agents
## services - list all services provided by agents
## who - display list of variables in workspace
## shutdown - shutdown the local platform

## run - run a Groovy script

Scripts are stored in a folder defined by the 'scripts' variable in the
workspace. If no such variable is defined, they are in the current folder.

Examples:
  run 'myscript'                   // run a script called myscript.groovy
  myscript                         // alternative syntax for running myscript
  run 'res://myscript.groovy'      // run a script from resources (in jar)
  run 'cls://myscript'             // run a precompiled script from class

## println - display message on console

Usage:
  println output, [type]

Examples:
  println 'hello there!'
  println 'that failed!', org.arl.fjage.shell.OutputType.ERROR

## href - make a clickable URL (on terminals that support URLs)

Usage:
  href(url)
  href(url, text)

Examples:
  println href('http://www.google.com')
  println href('http://www.google.com', 'Search...')

## delay - delay execution by the specified number of milliseconds

Example:
  delay 1000                      // delay for 1000 ms

## logLevel - set loglevel (optionally for a named logger)

Usage:
  logLevel [name],level

Examples:
  logLevel INFO                  // set loglevel to INFO
  logLevel 'org.arl', ALL        // set loglevel for logger org.arl to ALL

## subscribe - subscribe to notifications from a named topic

Examples:
  subscribe topic('MyTopic')     // subscribe to notifications from MyTopic
  subscribe agent('abc')         // subscribe to notifications from agent abc

## unsubscribe - unsubscribe from notifications for a named topic

Examples:
  unsubscribe topic('MyTopic')   // unsubscribe notifications from MyTopic
  unsubscribe agent('abc')       // unsubscribe notifications from agent abc

## export - add specified package/classes to list of default imports

Examples:
  export 'org.arl.fjage.*'            // import package org.arl.fjage
  export 'mypackage.MyClass'          // import class mypackage.MyClass

At the shell prompt (but not in a script), export can be abbreviated
to import. For example:
  import org.arl.fjage.*             // import package org.arl.fjage

## agent - return an agent id for the named agent

Usage:
  agent(name)

Example:
  a = agent('shell')

## agentForService - find an agent id providing the specified service

Examples:
  a = agentForService Services.SHELL  // find agents providing shell service

## agentsForService - get a list of all agent ids providing a service

Examples:
  a = agentsForService Services.SHELL // list all agents providing a service

## send - send the given message

Examples:
  send new Message(agent('shell'))    // send a message to agent shell

## request - send the given request and wait for a response

Usage:
  request req,[timeout]

Examples:
  rsp = request req     // send req and wait for response for default timeout
  rsp = request req,100 // send req and wait for response for 100 ms

## receive - wait for a message

Usage:
  receive [filter], [timeout]
  receive [msg], [timeout]

Examples:
  msg = receive                     // get any message with default timeout
  msg = receive 100                 // get any message within 100 ms
  msg = receive req                 // get a response message for request req
  msg = receive A                   // get message that of class A
  msg = receive { it instanceof A } // get message that of class A
  msg = receive req                 // get message response to req

## input - get user input

Usage:
  input [prompt], [hide]

Examples:
  name = input('What is your name?')  // prompt user and get input
  secret = input('Secret?', true)     // hide input after entering
'''

  static void __init__(ScriptEngine engine) {
    engine.importClasses('org.arl.fjage.*')
    engine.importClasses('org.arl.fjage.shell.*')
  }

} // class
