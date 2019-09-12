import numpy
import json as _json
import uuid as _uuid
import time as _time
import socket as _socket
import threading as _td
import logging as _log
import base64 as _base64
import struct as _struct
import copy as _copy
import time as _time


def _current_time_millis():
    """Returns current time in milliseconds.
    """
    return int(round(_time.time() * 1000))


def _b64toArray(base64, dtype, littleEndian=True):
    """Convert from base64 to array.
    """
    s = _base64.b64decode(base64)
    rv = []
    if dtype == '[B':  # byte array
        count = len(s) // _struct.calcsize('b')
        rv = list(_struct.unpack('<' + '{0}b'.format(count) if littleEndian else '>' + '{0}b'.format(count), s))
    elif dtype == '[S':  # short array
        count = len(s) // _struct.calcsize('h')
        rv = list(_struct.unpack('<' + '{0}h'.format(count) if littleEndian else '>' + '{0}h'.format(count), s))
    elif dtype == '[I':  # integer array
        count = len(s) // _struct.calcsize('i')
        rv = list(_struct.unpack('<' + '{0}i'.format(count) if littleEndian else '>' + '{0}i'.format(count), s))
    elif dtype == '[J':  # long array
        count = len(s) // _struct.calcsize('l')
        rv = list(_struct.unpack('<' + '{0}l'.format(count) if littleEndian else '>' + '{0}l'.format(count), s))
    elif dtype == '[F':  # float array
        count = len(s) // _struct.calcsize('f')
        rv = list(_struct.unpack('<' + '{0}f'.format(count) if littleEndian else '>' + '{0}f'.format(count), s))
    elif dtype == '[D':  # double array
        count = len(s) // _struct.calcsize('d')
        rv = list(_struct.unpack('<' + '{0}d'.format(count) if littleEndian else '>' + '{0}d'.format(count), s))
    else:
        return
    return rv


def _decodeBase64(m):
    """base64 JSON decoder.
    """
    if type(m) == dict and 'clazz' in list(m.keys()):
        clazz = m['clazz']
        if clazz.startswith('[') and len(clazz) == 2 and 'data' in list(m.keys()):
            x = _b64toArray(m['data'], m['clazz'])
            if x:
                m = x
    return m

class Action:
    """JSON message actions.
    """
    AGENTS = "agents"
    CONTAINS_AGENT = "containsAgent"
    SERVICES = "services"
    AGENT_FOR_SERVICE = "agentForService"
    AGENTS_FOR_SERVICE = "agentsForService"
    SEND = "send"
    WANTS_MESSAGES_FOR = "wantsMessagesFor"
    SHUTDOWN = "shutdown"


class Performative:
    """An action represented by a message. The performative actions are a subset of the
    FIPA ACL recommendations for interagent communication.
    """
    REQUEST = "REQUEST"               #: Request an action to be performed.
    AGREE = "AGREE"                   #: Agree to performing the requested action.
    REFUSE = "REFUSE"                 #: Refuse to perform the requested action.
    FAILURE = "FAILURE"               #: Notification of failure to perform a requested or agreed action.
    INFORM = "INFORM"                 #: Notification of an event.
    CONFIRM = "CONFIRM"               #: Confirm that the answer to a query is true.
    DISCONFIRM = "DISCONFIRM"         #: Confirm that the answer to a query is false.
    QUERY_IF = "QUERY_IF"             #: Query if some statement is true or false.
    NOT_UNDERSTOOD = "NOT_UNDERSTOOD"  # : Notification that a message was not understood.
    CFP = "CFP"                       #: Call for proposal.
    PROPOSE = "PROPOSE"               #: Response for CFP.
    CANCEL = "CANCEL"                 #: Cancel pending request.


class AgentID:
    """An identifier for an agent or a topic.
    """

    def __init__(self, name, is_topic=False, owner=None):
        if len(name) == 0 or name[0] == '#':
            raise ValueError('Bad agent name')
        self.is_topic = is_topic
        self.name = name
        self.index = -1
        self.owner = owner

    def send(self, msg):
        """Sends a message to the agent represented by this id.

        :param msg: message to send.
        """
        msg.recipient = self.name
        self.owner.send(msg)

    def request(self, msg, timeout=1000):
        """Sends a request to the agent represented by this id and waits for
        a return message for 1 second.

        :param msg: request to send.
        :param timeout: timeout in milliseconds.
        :returns: response.
        """
        msg.recipient = self.name
        return self.owner.request(msg, timeout)

    def __lshift__(self, msg):
        return self.request(msg)

    def _to_json(self):
        return '#'+self.name if self.is_topic else self.name


class _CustomEncoder(_json.JSONEncoder):
    def default(self, obj):
        if '_to_json' in obj.__dir__():
            return obj._to_json()
        return _json.JSONEncoder.default(self, obj)


class Message(object):
    """Base class for messages transmitted by one agent to another. This class provides
    the basic attributes of messages and is typically extended by application-specific
    message classes. To ensure that messages can be sent between agents running
    on remote containers, all attributes of a message must be serializable.
    """

    def __init__(self, inReplyTo=None, perf=Performative.INFORM, **kwargs):
        self.__clazz__ = 'org.arl.fjage.Message'
        self.msgID = str(_uuid.uuid4())
        self.perf = perf
        self.recipient = None
        self.sender = None
        self.inReplyTo = inReplyTo
        for k, v in kwargs.items():
            if not k.startswith('_') and k.endswith('_'):
                k = k[:-1]
            self.__dict__[k] = v
            if isinstance(v, AgentID):
                self.__dict__[k] = v.name

    def __getattribute__(self, name):
        if not name.startswith('_') and name.endswith('_'):
            return object.__getattribute__(self, name[:-1])
        return object.__getattribute__(self, name)

    def _serialize(self):
        """Convert a message into a JSON string.
        NOTE: we don't do any base64 encoding for TX as
        we don't know what data type is intended.
        """
        clazz = self.__clazz__
        m = self.__dict__
        t = [key for key, value in self.__dict__.items() if key.startswith('__')]
        for i in t:
            m.pop(i)
        for key, value in m.items():
            if type(value) == numpy.ndarray:
                if value.dtype == 'complex':
                    value = numpy.vstack((value.real, value.imag)).reshape((-1,), order='F')
                m[key] = value.tolist()
        data = _json.dumps(m, separators=(',', ':'), cls=_CustomEncoder)
        return '{ "clazz": "' + clazz + '", "data": ' + data + ' }'

    def _inflate(self, data):
        cplx = [x for x in data.keys() if x.endswith('__isComplex') and data[x]]
        for key, value in data.items():
            if key.endswith('__isComplex'):
                continue
            if (key+'__isComplex') in cplx:
                self.__dict__[key] = numpy.asarray(data[key][0::2]) + 1j * numpy.asarray(data[key][1::2])
            else:
                self.__dict__[key] = data[key]

    def _deserialize(self, obj):
        if (type(obj) == str or isinstance(obj, str)):
            obj = _json.loads(obj)
        qclazz = obj['clazz']
        clazz = qclazz.split('.')[-1]
        try:
            mod = __import__('fjagepy')
            clazz = getattr(mod, clazz)
            rv = clazz()
        except:
            rv = Message()
        rv.__clazz__ = qclazz
        rv._inflate(obj['data'])
        return rv

    def __str__(self):
        s = ''
        suffix = ''
        sigrepr = ''
        datarepr = ''
        clazz = self.__clazz__
        clazz = clazz.split(".")[-1]
        perf = self.perf
        flag = False
        for k, v in self.__dict__.items():
            if k.startswith('__'):
                continue
            if k == 'sender':
                continue
            if k == 'recipient':
                continue
            if k == 'msgID':
                continue
            if k == 'perf':
                continue
            if k == 'inReplyTo':
                continue
            if type(self.__dict__[k]) not in (int, float, bool, str, list, numpy.ndarray):
                suffix = ' ...'
                continue
            if type(self.__dict__[k]) in (numpy.ndarray, list) and k == 'signal':
                sigrepr = '(' + str(len(self.__dict__[k])) + ' samples' + ')'
                continue
            if type(self.__dict__[k]) in (numpy.ndarray, list) and k == 'data':
                datarepr = '(' + str(len(self.__dict__[k])) + ' bytes' + ')'
                continue
            s += ' ' + k + ':' + str(self.__dict__[k]) if flag else '' + k + ':' + str(self.__dict__[k])
            flag = True
        if suffix != '':
            s += ' ' + suffix
        if sigrepr != '':
            s += ' ' + sigrepr
        if datarepr != '':
            s += ' ' + datarepr
        if self.__clazz__ == 'org.arl.fjage.Message' and s == '':
            return perf
        return clazz + ':' + perf + '[' + s + ']'

    def _repr_pretty_(self, p, cycle):
        if cycle:
            p.text('...')
            return
        return p.text(self.__str__())

def MessageClass(name, parent=Message, perf=None):
    """Creates a unqualified message class based on a fully qualified name.
    """

    def setclazz(self, **kwargs):
        super(class_, self).__init__()
        self.__clazz__ = name
        if perf is not None:
            self.perf = perf
        elif name.endswith('Req'):
            self.perf = Performative.REQUEST
        for k, v in kwargs.items():
            if not k.startswith('_') and k.endswith('_'):
                k = k[:-1]
            self.__dict__[k] = v
            if isinstance(v, AgentID):
                self.__dict__[k] = v.name
    sname = name.split('.')[-1]
    class_ = type(sname, (parent,), {"__init__": setclazz})
    globals()[sname] = class_
    mod = __import__('fjagepy')
    return getattr(mod, str(class_.__name__))


class GenericMessage(Message):
    """A message class that can convey generic messages represented by key-value pairs.
    """

    def __init__(self, **kwargs):
        self.__clazz__ = 'org.arl.fjage.GenericMessage'
        self.msgID = str(_uuid.uuid4())
        self.perf = None
        self.recipient = None
        self.sender = None
        self.__dict__.update(kwargs)
        for k, v in kwargs.items():
            if not k.startswith('_') and k.endswith('_'):
                k = k[:-1]
            self.__dict__[k] = v
            if isinstance(v, AgentID):
                self.__dict__[k] = v.name


class Gateway:
    """Gateway to communicate with agents from Python.
    Creates a gateway connecting to a specified master container.

    :param hostname: hostname to connect to.
    :param port: TCP port to connect to.
    """
    DEFAULT_TIMEOUT = 1000
    NON_BLOCKING = 0
    BLOCKING = -1

    def __init__(self, hostname, port=1100):
        self.hostname = hostname
        self.port = port
        self.connection = None
        self.keepalive = True
        self.logger = _log.getLogger('org.arl.fjage')
        try:
            self.aid = AgentID("PythonGW-" + str(_uuid.uuid4()), owner=self)
            self.q = list()
            self.subscriptions = list()
            self.pending = dict()
            self.cv = _td.Condition()
            self.recv_thread = _td.Thread(target=self.__recv_proc, args=(self.q, self.subscriptions, ))
            self.recv_thread.daemon = True
            self.logger.info("Connecting to " + str(hostname) + ":" + str(port))
            try:
                self._socket_connect(hostname, port)
            except Exception as e:
                self.logger.critical("Exception: " + str(e))
                self.keepalive = False
                self._socket_reconnect(self.keepalive)
                raise
            self.recv_thread.start()
            if self._is_duplicate():
                self.logger.critical("Duplicate Gateway found. Shutting down.")
                self.socket.close()
                raise Exception('DuplicateGatewayException')
        except Exception as e:
            self.logger.critical("Exception: " + str(e))
            raise

    def _socket_connect(self, hostname, port):
        self.socket = _socket.socket(_socket.AF_INET, _socket.SOCK_STREAM)
        self.socket.connect((hostname, port))
        self.socket_file = self.socket.makefile('r', 65536)
        self.connection = True
        self.socket.sendall('{"alive": true}\n'.encode())
        self._update_watch()

    def isConnected(self):
        if self.connection == False:
            return False
        else:
            return True

    def _parse_dispatch(self, rmsg, q):
        """Parse incoming messages and respond to them or dispatch them.
        """
        req = _json.loads(rmsg, object_hook=_decodeBase64)
        rsp = dict()
        if "id" in req:
            req['id'] = _uuid.UUID(req['id'])
        if "action" in req:
            if req["action"] == Action.AGENTS:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                rsp["agentIDs"] = [self.aid.name]
                self.socket.sendall((_json.dumps(rsp, cls=_CustomEncoder) + '\n').encode())
            elif req["action"] == Action.CONTAINS_AGENT:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                answer = False
                if req["agentID"]:
                    if req["agentID"] == self.aid.name:
                        answer = True
                rsp["answer"] = answer
                self.socket.sendall((_json.dumps(rsp, cls=_CustomEncoder) + '\n').encode())
            elif req["action"] == Action.SERVICES:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                rsp["services"] = []
                self.socket.sendall((_json.dumps(rsp, cls=_CustomEncoder) + '\n').encode())
            elif req["action"] == Action.AGENT_FOR_SERVICE:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                rsp["agentID"] = ""
                self.socket.sendall((_json.dumps(rsp, cls=_CustomEncoder) + '\n').encode())
            elif req["action"] == Action.AGENTS_FOR_SERVICE:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                rsp["agentIDs"] = []
                self.socket.sendall((_json.dumps(rsp, cls=_CustomEncoder) + '\n').encode())
            elif req["action"] == Action.SEND:
                try:
                    msg = req["message"]
                    if msg["data"]["recipient"] == self.aid.name:
                        try:
                            m = Message()
                            demsg = m._deserialize(msg)
                        except Exception as e:
                            self.logger.critical("Exception: Class loading failed - " + str(e))
                        q.append(demsg)
                        self.cv.acquire()
                        self.cv.notify()
                        self.cv.release()
                    if self._is_topic(msg["data"]["recipient"]):
                        if self.subscriptions.count(msg["data"]["recipient"].replace("#", "")):
                            try:
                                m = Message()
                                demsg = m._deserialize(msg)
                            except Exception as e:
                                self.logger.critical("Exception: Class loading failed - " + str(e))
                            q.append(demsg)
                            self.cv.acquire()
                            self.cv.notify()
                            self.cv.release()
                except Exception as e:
                    self.logger.critical("Exception: Error adding to queue - " + str(e))
            elif req["action"] == Action.SHUTDOWN:
                self.logger.debug("ACTION: " + Action.SHUTDOWN)
                if not self.keepalive:
                    self.close()
                return None
            else:
                self.logger.warning("Invalid message, discarding")
        else:
            if "id" in req:
                if req['id'] in self.pending:
                    tup = self.pending[req["id"]]
                    self.pending[req["id"]] = (tup[0], req)
                    tup[0].set()
        return True

    def _socket_reconnect(self, keepalive):
        if keepalive:
            self.logger.info('The master container is closed, trying to reconnect..\n')
            while True:
                try:
                    self._socket_connect(self.hostname, self.port)
                    self.logger.info('Reconnected..')
                    break
                except Exception as e:
                    _time.sleep(5)
        else:
            self.logger.info('The remote connection is closed..\n')

    def _update_watch(self):
        rq = {'action': Action.WANTS_MESSAGES_FOR, 'agentIDs': [self.aid.name]+['#'+topic for topic in self.subscriptions]}
        self.socket.sendall((_json.dumps(rq, cls=_CustomEncoder) + '\n').encode())

    def __recv_proc(self, q, subscriptions):
        """Receive process.
        """
        parenthesis_count = 0
        rmsg = ""
        try:
            name = self.socket.getpeername()
        except Exception as e:
            self.logger.critical("Exception: " + str(e))
            self.keepalive = True
            self._socket_reconnect(self.keepalive)
        while True:
            try:
                rmsg = self.socket_file.readline()
                if not rmsg:
                    self.connection = False
                    self._socket_reconnect(self.keepalive)
                    if self.keepalive:
                        continue
                    else:
                        self.connection = True
                        break
                self.logger.debug(str(name[0]) + ":" + str(name[1]) + " <<< " + rmsg)
                # Parse and dispatch incoming messages
                self._parse_dispatch(rmsg, q)
            except Exception as e:
                self.logger.critical("Exception: " + str(e))
                pass

    def __del__(self):
        try:
            self.socket.close()
        except Exception as e:
            self.logger.critical("Exception: " + str(e))

    def close(self):
        """Closes the gateway. The gateway functionality may not longer be accessed after this method is called.
        """
        try:
            self.keepalive = False
            self.connection = False
            self.socket.sendall('{"alive": false}\n'.encode())
            self.socket.shutdown(_socket.SHUT_RDWR)
        except Exception as e:
            self.logger.critical("Exception: " + str(e))

    def send(self, msg):
        """Sends a message to the recipient indicated in the message. The recipient may be an agent or a topic.
        """
        tmsg = msg
        if not tmsg.recipient:
            return False
        tmsg.sender = self.aid.name
        if tmsg.perf == None:
            if tmsg.__clazz__.endswith('Req'):
                tmsg.perf = Performative.REQUEST
            else:
                tmsg.perf = Performative.INFORM
        rq = _json.dumps({'action': Action.SEND, 'relay': True, 'message': '###MSG###'}, cls=_CustomEncoder)
        rq = rq.replace('"###MSG###"', tmsg._serialize())
        try:
            name = self.socket.getpeername()
        except Exception as e:
            self.logger.critical("Exception: " + str(e))
            self.keepalive = True
            self._socket_reconnect(self.keepalive)
        self.logger.debug(str(name[0]) + ":" + str(name[1]) + " >>> " + rq)
        self.socket.sendall((rq + '\n').encode())
        return True

    def _retrieveFromQueue(self, filter):
        rmsg = None
        try:
            if filter == None and len(self.q):
                rmsg = self.q.pop()
            # If filter is a Message, look for a Message in the
            # receive Queue which was inReplyTo that message.
            elif isinstance(filter, Message):
                if filter.msgID:
                    for i in self.q:
                        if filter.msgID == i.inReplyTo:
                            try:
                                rmsg = self.q.pop(self.q.index(i))
                            except Exception as e:
                                self.logger.critical("Error: Getting item from list - " + str(e))
            # If filter is a class, look for a Message of that class.
            elif isinstance(filter, type):
                for i in self.q:
                    if (isinstance(i, Message) and isinstance(i, filter)):
                        try:
                            rmsg = self.q.pop(self.q.index(i))
                        except Exception as e:
                            self.logger.critical("Error: Getting item from list - " + str(e))
            # If filter is a lambda, look for a Message that on which the
            # lambda returns True.
            elif isinstance(filter, type(lambda: 0)):
                for i in self.q:
                    if filter(i):
                        try:
                            rmsg = self.q.pop(self.q.index(i))
                        except Exception as e:
                            self.logger.critical("Error: Getting item from list - " + str(e))
        except Exception as e:
            self.logger.critical("Error: Queue empty/timeout - " + str(e))
        return rmsg

    def receive(self, filter=None, timeout=0):
        """Returns a message received by the gateway and matching the given filter. This method blocks until timeout if no message available.

        :param filter: message filter.
        :param timeout: timeout in milliseconds.
        :returns: received message matching the filter, null on timeout.
        """
        rmsg = self._retrieveFromQueue(filter)
        if (rmsg == None and timeout != self.NON_BLOCKING):
            deadline = _current_time_millis() + timeout
            while (rmsg == None and (timeout == self.BLOCKING or _current_time_millis() < deadline)):
                if timeout == self.BLOCKING:
                    self.cv.acquire()
                    self.cv.wait()
                    self.cv.release()
                elif timeout > 0:
                    self.cv.acquire()
                    t = deadline - _current_time_millis()
                    self.cv.wait(t / 1000)
                    self.cv.release()
                rmsg = self._retrieveFromQueue(filter)
        if not rmsg:
            return None
        return rmsg

    def request(self, msg, timeout=1000):
        """Sends a request and waits for a response. This method blocks until timeout if no response is received.

        :param msg: message to send.
        :param timeout: timeout in milliseconds.
        :returns: received response message, null on timeout.
        """
        self.send(msg)
        return self.receive(msg, timeout)

    def topic(self, topic, topic2=None):
        """Returns an object representing the named topic.

        :param topic: name of the agent/topic.
        :param topic2: named topic for a given agent.
        :returns: object representing the topic.
        """
        if topic2 is None:
            if isinstance(topic, str):
                return AgentID(topic, True, owner=self)
            elif isinstance(topic, AgentID):
                if topic.is_topic:
                    return topic
                return AgentID(topic.name + "__ntf", True, owner=self)
            else:
                return AgentID(topic.__class__.__name__ + "." + str(topic), True, owner=self)
        else:
            if not isinstance(topic2, str):
                topic2 = topic2.__class__.__name__ + "." + str(topic2)
            return AgentID(topic.name + "__" + topic2 + "__ntf", True, owner=self)

    def agent(self, name):
        """Returns an object representing a named agent.

        :param name: name of the agent.
        :returns: object representing the agent.
        """
        return AgentID(name, owner=self)


    def subscribe(self, topic):
        """Subscribes the gateway to receive all messages sent to the given topic.

        :param topic: the topic to subscribe to.
        """
        if isinstance(topic, AgentID):
            if topic.is_topic == False:
                new_topic = AgentID(topic.name + "__ntf", True, owner=self)
            else:
                new_topic = topic
            if new_topic.name in self.subscriptions:
                self.logger.critical("Warning: Already subscribed to topic")
                return False
            self.subscriptions.append(new_topic.name)
            self._update_watch()
            return True
        else:
            self.logger.critical("Invalid AgentID")
            return False

    def unsubscribe(self, topic):
        """Unsubscribes the gateway from a given topic.

        :param topic: the topic to unsubscribe.
        """
        if isinstance(topic, AgentID):
            if topic.is_topic == False:
                new_topic = AgentID(topic.name + "__ntf", True, owner=self)
            else:
                new_topic = topic
            if len(self.subscriptions) == 0:
                return False
            try:
                self.subscriptions.remove(new_topic.name)
            except:
                self.logger.critical("Exception: No such topic subscribed: " + new_topic.name)
                return False
            self._update_watch()
            return True
        else:
            self.logger.critical("Invalid AgentID")
            return False

    def agentForService(self, service):
        """Finds an agent that provides a named service. If multiple agents are registered
        to provide a given service, any of the agents' id may be returned.

        :param service: the named service of interest.
        :returns: an agent id for an agent that provides the service.
        """
        req_id = _uuid.uuid4()
        rq = {'action': Action.AGENT_FOR_SERVICE, 'service': service, 'id': str(req_id)}
        self.socket.sendall((_json.dumps(rq, cls=_CustomEncoder) + '\n').encode())
        res_event = _td.Event()
        self.pending[req_id] = (res_event, None)
        ret = res_event.wait(self.DEFAULT_TIMEOUT)
        if not ret:
            return None
        else:
            tup = self.pending.pop(req_id)
            if "agentID" in tup[1]:
                a = tup[1]["agentID"]
            else:
                a = None
            if a is not None:
                if isinstance(a, str):
                    a = AgentID(a, owner=self)
                else:
                    a = AgentID(a.name, a.is_topic, owner=self)
            return a

    def agentsForService(self, service):
        """Finds all agents that provides a named service.

        :param service: the named service of interest.
        :returns: a list of agent ids representing all agent that provide the service.
        """
        req_id = _uuid.uuid4()
        j_dict = dict()
        j_dict["action"] = Action.AGENTS_FOR_SERVICE
        j_dict["id"] = str(req_id)
        if isinstance(service, str):
            j_dict["service"] = service
        else:
            j_dict["service"] = service.__class__.__name__ + "." + str(service)
        self.socket.sendall((_json.dumps(j_dict, cls=_CustomEncoder) + '\n').encode())
        res_event = _td.Event()
        self.pending[req_id] = (res_event, None)
        ret = res_event.wait(self.DEFAULT_TIMEOUT)
        if not ret:
            return None
        else:
            tup = self.pending.pop(req_id)
            if "agentIDs" in tup[1]:
                a = tup[1]["agentIDs"]
            else:
                a = None
            if a is not None:
                for j in range(len(a)):
                    if isinstance(a[j], str):
                        a[j] = AgentID(a[j], owner=self)
                    else:
                        a[j] = AgentID(a[j].name, owner=self)
            return a

    def getAgentID(self):
        """Returns the gateway Agent ID.
        """
        return self.aid

    def _is_duplicate(self):
        req_id = _uuid.uuid4()
        req = dict()
        req["action"] = Action.CONTAINS_AGENT
        req["id"] = str(req_id)
        req["agentID"] = self.aid.name
        self.socket.sendall((_json.dumps(req, cls=_CustomEncoder) + '\n').encode())
        res_event = _td.Event()
        self.pending[req_id] = (res_event, None)
        ret = res_event.wait(self.DEFAULT_TIMEOUT)
        if not ret:
            return True
        else:
            tup = self.pending.pop(req_id)
            return tup[1]["answer"] if "answer" in tup[1] else True

    def _is_topic(self, recipient):
        if recipient[0] == "#":
            return True
        return False

    def flush(self):
        self.q.clear()
