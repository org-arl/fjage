include 'org.arl.fjage.*'

doc['help'] = 'help [topic] - provide help on a specified topic'
doc['ps'] = 'ps - list all the agents running on the local container'
doc['who'] = 'who - display list of variables in workspace'
doc['run'] = '''\
run - run a Groovy script

Scripts are stored in a folder defined by the \'scripts\' variable in the
workspace. If no such variable is defined, they are in the current folder.

Examples:
  run \'myscript\'                   // run a script called myscript.groovy
  <myscript                        // alternative syntax for running myscript.groovy
'''
doc['println'] = '''\
println - display message on console

Example:
  println \'hello there!\'
'''
doc['delay'] = '''\
delay - delay execution by the specified number of milliseconds

Example:
  delay 1000                      // delay for 1000 ms
'''
doc['shutdown'] = 'shutdown - shutdown the local platform'
doc['logLevel'] = '''\
logLevel - set loglevel (optionally for a named logger)

Usage:
  logLevel [name],level

Examples:
  logLevel INFO                  // set loglevel to INFO
  loglevel \'org.arl\', ALL        // set loglevel for logger org.arl to ALL
'''
doc['subscribe'] = '''\
subscribe - subscribe to notifications from a named topic

Examples:
  subscribe topic(\'MyTopic\')     // subscribe to notifications from MyTopic
  subscribe agent(\'abc\')         // subscribe to notifications from agent abc
'''
doc['unsubscribe'] = '''\
unsubscribe - unsubscribe from notifications for a named topic

Examples:
  unsubscribe topic(\'MyTopic\')   // unsubscribe notifications from MyTopic
  unsubscribe agent(\'abc\')       // unsubscribe notifications from agent abc
'''
doc['include'] = '''\
include - add specified package/classes to list of imports

Examples:
  include \'org.arl.fjage.*\'      // import package org.arl.fjage
  include \'mypackage.MyClass\'    // import class mypackage.MyClass
'''
doc['agent'] = '''\
agent - return an agent id for the named agent

Usage:
  agent(name)

Example:
  a = agent(\'shell\')
'''
doc['agentForService'] = '''\
agentForService - find an agent id providing the specified service

Examples:
  a = agentForService Services.SHELL  // find agents providing shell service
'''
doc['agentsForService'] = '''\
agentsForService - get a list of all agent ids providing the specified service

Examples:
  a = agentsForService Services.SHELL // list all agents providing a service
'''
doc['send'] = '''\
send - send the given message

Examples:
  send new Message(agent(\'shell\'))    // send a message to agent shell
'''
doc['request'] = '''\
request - send the given request and wait for a response

Usage:
  request req,[timeout]

Examples:
  rsp = request req     // send req and wait for response for default timeout
  rsp = request req,100 // send req and wait for response for 100 ms
'''
doc['receive'] = '''\
receive - wait for a message

Usage:
  receive [filter],[timeout]
  receive [msg], [timeout]

Examples:
  msg = receive                     // get any message with default timeout
  msg = receive 100                 // get any message within 100 ms
  msg = receive req                 // get a response message for request req
  msg = receive A                   // get message that of class A
  msg = receive { it instanceof A } // get message that of class A
  msg = receive req                 // get message response to req
'''
