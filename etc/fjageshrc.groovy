include 'org.arl.fjage.*'

doc['help'] = 'help [topic] - provide help on a specified topic'
doc['ps'] = 'ps - list all the agents running on the local container'
doc['who'] = 'who - display list of variables in workspace'
doc['run'] = '''\
run script - run a Groovy script

Scripts are stored in a folder defined by the \'scripts\' variable in the
workspace. If no such variable is defined, they are in the current folder.

Examples:
  run \'myscript\'                   // run a script called myscript.groovy
  <myscript                        // alternative syntax for running myscript.groovy
'''
doc['println'] = 'println message - display message on console'
doc['sleep'] = 'sleep millis - delay execution by the specified number of milliseconds'
doc['shutdown'] = 'shutdown - shutdown the local platform'
doc['logLevel'] = '''\
logLevel [name],level - set loglevel (optionally for a named logger)

Examples:
  logLevel INFO                  // set loglevel to INFO
  loglevel \'org.arl\', ALL        // set loglevel for logger org.arl to ALL
'''
doc['subscribe'] = '''\
subscribe topic(name) - subscribe to notifications from a named topic

Examples:
  subscribe topic(\'MyTopic\')       // subscribe to notifications from MyTopic
'''
doc['unsubscribe'] = 'unsubscribe topic(name) - unsubscribe from notifications for a named topic'
doc['include'] = '''\
include spec - add specified package/classes to list of imports

Examples:
  include \'org.arl.fjage.*\'      // import package org.arl.fjage
  include \'mypackage.MyClass\'    // import class mypackage.MyClass
'''
doc['agent'] = 'agent(name) - return an agent id for the named agent'
doc['agentForService'] = 'agentForService(service) - find an agent id providing the specified service'
doc['agentsForService'] = 'agentsForService(service) - get a list of agent ids providing the specified service'
doc['send'] = 'send msg - send the given message'
doc['request'] = '''\
request req,[timeout] - send the given request and wait for a response

Examples:
  rsp = request req     // send req and wait for response for default timeout
  rsp = request req,100 // send req and wait for response for 100 ms
'''
doc['receive'] = '''\
receive [filter],[timeout] - wait for a message

Examples:
  msg = receive                     // get any message with default timeout
  msg = receive 100                 // get any message within 100 ms
  msg = receive req                 // get a response message for request req
  msg = receive { it instanceof A } // get message that of class A
'''
