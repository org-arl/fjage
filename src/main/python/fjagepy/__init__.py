import numpy
import json as _json
import uuid as _uuid
import time as _time
import socket as _socket
import threading as _td
import logging as _log
import base64 as _base64
import struct as _struct


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
        count = len(s) // _struct.calcsize('c')
        rv = list(_struct.unpack('<' + '{0}c'.format(count) if littleEndian else '>' + '{0}c'.format(count), s))
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
    for d in m.values():
        if type(d) == dict and 'clazz' in d.keys():
            clazz = d['clazz']
            if clazz.startswith('[') and len(clazz) == 2 and 'data' in d.keys():
                x = _b64toArray(d['data'], d['clazz'])
                if x:
                    m['signal'] = x
    return m


def MessageClass(name):
    """Creates a unqualified message class based on a fully qualified name.
    """

    def setclazz(self, **kwargs):
        super(class_, self).__init__()
        self.__clazz__ = name
        for k, v in kwargs.items():
            if not k.startswith('_') and k.endswith('_'):
                k = k[:-1]
            self.__dict__[k] = v
    sname = name.split('.')[-1]
    class_ = type(sname, (Message,), {"__init__": setclazz})
    globals()[sname] = class_
    mod = __import__('fjagepy')
    return getattr(mod, str(class_.__name__))


class Action:
    """JSON message actions.
    """
    AGENTS = "agents"
    CONTAINS_AGENT = "containsAgent"
    SERVICES = "services"
    AGENT_FOR_SERVICE = "agentForService"
    AGENTS_FOR_SERVICE = "agentsForService"
    SEND = "send"
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

    def __init__(self, name, is_topic=False):
        self.name = name
        if is_topic:
            self.is_topic = True
        else:
            self.is_topic = False


class Message(object):
    """Base class for messages transmitted by one agent to another. This class provides
    the basic attributes of messages and is typically extended by application-specific
    message classes. To ensure that messages can be sent between agents running
    on remote containers, all attributes of a message must be serializable.
    """

    def __init__(self, **kwargs):
        self.msgID = str(_uuid.uuid4())
        self.perf = None
        self.recipient = None
        self.sender = None
        self.inReplyTo = None
        for k, v in kwargs.items():
            if not k.startswith('_') and k.endswith('_'):
                k = k[:-1]
            self.__dict__[k] = v

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
                m[key] = value.tolist()
        data = _json.dumps(m, separators=(',', ':'))
        return '{ "clazz": "' + clazz + '", "data": ' + data + ' }'

    def _inflate(self, data):
        for key, value in data.items():
            self.__dict__[key] = data[key]

    def _deserialize(self, obj):
        if (type(obj) == str or isinstance(obj, str)):
            obj = _json.loads(obj)
        qclazz = obj['clazz']
        clazz = qclazz.split('.')[-1]
        try:
            rv = clazz()
        except:
            rv = Message()
        rv.__clazz__ = qclazz
        rv._inflate(obj['data'])
        return rv

    def __str__(self):
        s = ''
        suffix = ''
        clazz = self.__clazz__
        clazz = clazz.split(".")[-1]
        perf = self.perf
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
            if type(self.__dict__[k]) not in (int, float, bool, str):
                suffix = ' ...'
                continue
            s += ' ' + k + ':' + str(self.__dict__[k])
        s += suffix
        return clazz + ':' + perf + '[' + s.replace(',', ' ') + ']'


class GenericMessage(Message):
    """A message class that can convey generic messages represented by key-value pairs.
    """

    def __init__(self, **kwargs):
        super(GenericMessage, self).__init__()
        self.map = dict()
        self.__dict__.update(kwargs)


class Gateway:
    """Gateway to communicate with agents from Python. 
    Creates a gateway connecting to a specified master container.

    :param hostname: hostname to connect to.
    :param port: TCP port to connect to.
    """
    DEFAULT_TIMEOUT = 1000
    NON_BLOCKING = 0
    BLOCKING = -1

    def __init__(self, hostname, port, name=None):
        self.logger = _log.getLogger('org.arl.fjage')
        try:
            if name == None:
                self.name = "PythonGW-" + str(_uuid.uuid4())
            else:
                try:
                    self.name = name
                except Exception as e:
                    self.self.logger.critical("Exception: Cannot assign name to gateway: " + str(e))
                    raise
            self.q = list()
            self.subscribers = list()
            self.pending = dict()
            self.cv = _td.Condition()
            self.socket = _socket.socket(_socket.AF_INET, _socket.SOCK_STREAM)
            self.recv_thread = _td.Thread(target=self.__recv_proc, args=(self.q, self.subscribers, ))
            self.recv_thread.daemon = True
            self.logger.info("Connecting to " + str(hostname) + ":" + str(port))
            self.socket.connect((hostname, port))
            self.socket_file = self.socket.makefile('r', 65536)
            self.recv_thread.start()
            if self._is_duplicate():
                self.logger.critical("Duplicate Gateway found. Shutting down.")
                self.socket.close()
                raise Exception('DuplicateGatewayException')
        except Exception as e:
            self.logger.critical("Exception: " + str(e))
            raise

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
                rsp["agentIDs"] = [self.name]
                self.socket.sendall((_json.dumps(rsp) + '\n').encode())
            elif req["action"] == Action.CONTAINS_AGENT:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                answer = False
                if req["agentID"]:
                    if req["agentID"] == self.name:
                        answer = True
                rsp["answer"] = answer
                self.socket.sendall((_json.dumps(rsp) + '\n').encode())
            elif req["action"] == Action.SERVICES:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                rsp["services"] = []
                self.socket.sendall((_json.dumps(rsp) + '\n').encode())
            elif req["action"] == Action.AGENT_FOR_SERVICE:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                rsp["agentID"] = ""
                self.socket.sendall((_json.dumps(rsp) + '\n').encode())
            elif req["action"] == Action.AGENTS_FOR_SERVICE:
                rsp["inResponseTo"] = req["action"]
                rsp["id"] = str(req["id"])
                rsp["agentIDs"] = []
                self.socket.sendall((_json.dumps(rsp) + '\n').encode())
            elif req["action"] == Action.SEND:
                try:
                    msg = req["message"]
                    if msg["data"]["recipient"] == self.name:
                        q.append(msg)
                        self.cv.acquire()
                        self.cv.notify()
                        self.cv.release()

                    if self._is_topic(msg["data"]["recipient"]):
                        if self.subscribers.count(msg["data"]["recipient"].replace("#", "")):
                            q.append(msg)
                            self.cv.acquire()
                            self.cv.notify()
                            self.cv.release()
                except Exception as e:
                    self.logger.critical("Exception: Error adding to queue - " + str(e))
            elif req["action"] == Action.SHUTDOWN:
                self.logger.debug("ACTION: " + Action.SHUTDOWN)
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

    def __recv_proc(self, q, subscribers):
        """Receive process.
        """
        parenthesis_count = 0
        rmsg = ""
        name = self.socket.getpeername()
        while True:
            try:
                rmsg = self.socket_file.readline()
                if not rmsg:
                    print('The remote connection is closed!')
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

    def shutdown(self):
        """Shutdown the platform.
        """
        j_dict = dict()
        j_dict["action"] = Action.SHUTDOWN
        self.socket.sendall((_json.dumps(j_dict) + '\n').encode())

    def close(self):
        """Closes the gateway. The gateway functionality may not longer be accessed after this method is called.
        """
        try:
            self.socket.shutdown(_socket.SHUT_RDWR)
        except Exception as e:
            self.logger.critical("Exception: " + str(e))
        print('The gateway connection is closed!')

    def send(self, msg, relay=True):
        """Sends a message to the recipient indicated in the message. The recipient may be an agent or a topic.
        """
        if not msg.recipient:
            return False
        msg.sender = self.name
        if msg.perf == None:
            if msg.__clazz__.endswith('Req'):
                msg.perf = Performative.REQUEST
            else:
                msg.perf = Performative.INFORM
        rq = _json.dumps({'action': 'send', 'relay': relay, 'message': '###MSG###'})
        rq = rq.replace('"###MSG###"', msg._serialize())
        name = self.socket.getpeername()
        self.logger.debug(str(name[0]) + ":" + str(name[1]) + " >>> " + rq)
        self.socket.sendall((rq + '\n').encode())
        return True

    def _retrieveFromQueue(self, filter):
        rmsg = None
        try:
            if filter == None and len(self.q):
                rmsg = self.q.pop()
            # If filter is a Message, look for a Message in the
            # receive Queue which was inReplyto that message.
            elif isinstance(filter, Message):
                if filter.msgID:
                    for i in self.q:
                        if "inReplyTo" in i["data"] and filter.msgID == i["data"]["inReplyTo"]:
                            try:
                                rmsg = self.q.pop(self.q.index(i))
                            except Exception as e:
                                self.logger.critical("Error: Getting item from list - " + str(e))
            # If filter is a class, look for a Message of that class.
            elif type(filter) == type(Message):
                for i in self.q:
                    if i["clazz"].split(".")[-1] == filter.__name__:
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
        try:
            m = Message()
            rsp = m._deserialize(rmsg)
            found_map = False
            # add map if it is a Generic message
            if rsp.__class__.__name__ == GenericMessage().__class__.__name__:
                if "map" in rmsg["data"]:
                    map = _json.loads(str(rmsg["data"]["map"]))
                    rsp.__dict__.update(map)
                    found_map = True
                if not found_map:
                    self.logger.warning("No map field found in Generic Message")
        except Exception as e:
            self.logger.critical("Exception: Class loading failed - " + str(e))
            return None
        return rsp

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
                return AgentID(topic, True)
            elif isinstance(topic, AgentID):
                if topic.is_topic:
                    return topic
                return AgentID(topic.name + "__ntf", True)
            else:
                return AgentID(topic.__class__.__name__ + "." + str(topic), True)
        else:
            if not isinstance(topic2, str):
                topic2 = topic2.__class__.__name__ + "." + str(topic2)
            return AgentID(topic.name + "__" + topic2 + "__ntf", True)

    def subscribe(self, topic):
        """Subscribes the gateway to receive all messages sent to the given topic.

        :param topic: the topic to subscribe to.
        """
        if isinstance(topic, AgentID):
            if topic.is_topic == False:
                new_topic = AgentID(topic.name + "__ntf", True)
            else:
                new_topic = topic

            if len(self.subscribers) == 0:
                self.subscribers.append(new_topic.name)
            else:
                if new_topic.name in self.subscribers:
                    self.logger.critical("Error: Already subscribed to topic")
                    return
                self.subscribers.append(new_topic.name)
        else:
            self.logger.critical("Invalid AgentID")

    def unsubscribe(self, topic):
        """Unsubscribes the gateway from a given topic.

        :param topic: the topic to unsubscribe.
        """
        if isinstance(topic, AgentID):
            if topic.is_topic == False:
                new_topic = AgentID(topic.name + "__ntf", True)
            if len(self.subscribers) == 0:
                return False
            try:
                self.subscribers.remove(topic.name)
            except:
                self.logger.critical("Exception: No such topic subscribed: " + new_topic.name)
            return True
        else:
            self.logger.critical("Invalid AgentID")

    def agentForService(self, service, timeout=1000):
        """Finds an agent that provides a named service. If multiple agents are registered
        to provide a given service, any of the agents' id may be returned.

        :param service: the named service of interest.
        :returns: an agent id for an agent that provides the service.
        """
        req_id = _uuid.uuid4()
        rq = {'action': 'agentForService', 'service': service, 'id': str(req_id)}
        self.socket.sendall((_json.dumps(rq) + '\n').encode())
        res_event = _td.Event()
        self.pending[req_id] = (res_event, None)
        ret = res_event.wait(timeout)
        if not ret:
            return None
        else:
            tup = self.pending.pop(req_id)
            return tup[1]["agentID"] if "agentID" in tup[1] else None

    def agentsForService(self, service, timeout=1000):
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
        self.socket.sendall((_json.dumps(j_dict) + '\n').encode())
        res_event = _td.Event()
        self.pending[req_id] = (res_event, None)
        ret = res_event.wait(timeout)
        if not ret:
            return None
        else:
            tup = self.pending.pop(req_id)
            return tup[1]["agentIDs"] if "agentIDs" in tup[1] else None

    def getAgentID(self):
        """Returns the gateway Agent ID.
        """
        return self.name

    def _is_duplicate(self):
        req_id = _uuid.uuid4()
        req = dict()
        req["action"] = Action.CONTAINS_AGENT
        req["id"] = str(req_id)
        req["agentID"] = self.name
        self.socket.sendall((_json.dumps(req) + '\n').encode())
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
