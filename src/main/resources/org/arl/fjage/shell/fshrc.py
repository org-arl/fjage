#################################################################################
#
# Copyright (c) 2018, Mandar Chitre
#
# This file is part of fjage which is released under Simplified BSD License.
# See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
# for full license details.
#
#################################################################################

from java.util.logging import Level as _Level, Logger as _Logger
from org.arl.fjage import AgentID as _AgentID

# Log levels
ALL = _Level.ALL
FINEST = _Level.FINEST
FINER = _Level.FINER
FINE = _Level.FINE
INFO = _Level.INFO
WARNING = _Level.WARNING
SEVERE = _Level.SEVERE
OFF = _Level.OFF

log = _Logger.getLogger('org.arl.fjage.shell.JythonScriptEngine')

class __AgentID__(_AgentID):
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
  _Logger.getLogger(name).setLevel(level)

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

def services():
  s = ''
  if '__agent__' in globals():
    c = __agent__.getContainer()
    svc = c.getServices()
    first = True
    for s1 in svc:
      if not first:
        s += '\n'
      s += str(s1)
      aids = agentsForService(s1)
      if aids:
        s += ':'
        for a1 in aids:
          s += ' '+str(a1)
      first = False
  return s

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
