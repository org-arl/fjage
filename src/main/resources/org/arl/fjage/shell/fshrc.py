#################################################################################
#
# Copyright (c) 2018, Mandar Chitre
#
# This file is part of fjage which is released under Simplified BSD License.
# See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
# for full license details.
#
#################################################################################

from java.util.logging import Level, Logger
from org.arl.fjage import AgentID

# Log levels
ALL = Level.ALL
FINEST = Level.FINEST
FINER = Level.FINER
FINE = Level.FINE
INFO = Level.INFO
WARNING = Level.WARNING
SEVERE = Level.SEVERE
OFF = Level.OFF

log = Logger.getLogger('org.arl.fjage.shell.JythonScriptEngine')

class __AgentID__(AgentID):
  def __lshift__(self, msg):
    return self.request(msg)

def subscribe(topic):
  if '__agent__' in globals():
    __agent__.subscribe(topic)

def unsubscribe(topic):
  if '__agent__' in globals():
    __agent__.unsubscribe(topic)

def shutdown():
  if '__agent__' in globals():
    __agent__.getPlatform().shutdown()
  return None

def ps():
  s = ''
  if '__agent__' in globals():
    c = __agent__.getContainer()
    agents = c.getAgents()
    first = True
    for aid in agents:
      if not first:
        s += ''
      s += aid.toString()
      a1 = c.getAgent(aid)
      if a1:
        s += ': '+a1.class.canonicalName+' - '+str(a1.getState())
      else:
        s += ': REMOTE'
      first = False
  return s

def agent(name):
  if '__agent__' in globals():
    return __AgentID__(name, __agent__)
  return __AgentID__(name)

def topic(name, s=None):
  if '__agent__' in globals():
    if s is None:
      return __agent__.topic(name)
    return __agent__.topic(name, s)

def logLevel(name, level=None):
  if level is None:
    level = name
    name = ''
  Logger.getLogger(name).setLevel(level)

def delay(millis):
  if '__agent__' in globals():
    __agent__.getPlatform().delay(millis)
  else:
    import time
    time.sleep(millis/1000.0)

def who():
  vars = globals().keys()
  return [item for item in vars if not item.startswith('_') and item not in __masked__]

def agentForService(service):
  if '__agent__' in globals():
    return __agent__.agentForService(service)
  return None

def agentsForService(service):
  if '__agent__' in globals():
    return __agent__.agentsForService(service)
  return None

def help(topic=None):
  if topic is None:
    return 'No help available yet!'  # TODO
  else:
    try:
      return topic.__doc__
    except:
      return None

def send(msg):
  if '__agent__' in globals():
    return __agent__.send(msg)
  return False

def request(msg, timeout=1000):
  if '__agent__' in globals():
    return __agent__.request(msg, timeout)
  return None

def receive(cls=None, timeout=1000):
  from java.lang import Class
  if '__agent__' in globals():
    if cls is None:
      return __agent__.receive(timeout)
    elif isinstance(cls, Class):
      return __agent__.receive(cls, timeout)
    else:
      # TODO add support for message/filters
      pass
  return None

def defined(varname):
  return varname in globals()

__masked__ = globals().keys()

# String services() {
#   Binding binding = getBinding();
#   if (binding.hasVariable('__agent__')) {
#     Agent a = binding.getVariable('__agent__');
#     Container c = a.getContainer();
#     String[] svc = c.getServices();
#     StringBuffer s = new StringBuffer();
#     boolean first = true;
#     for (String s1: svc) {
#       if (!first) s.append('\n');
#       s.append(s1);
#       AgentID[] aids = agentsForService(s1)
#       if (aids) {
#         s.append(':')
#         aids.each {
#           s.append(' ')
#           s.append(it)
#         }
#       }
#       first = false;
#     }
#     return s.toString();
#   }
#   return null;
# }
