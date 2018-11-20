# global
import os as _os
import sys as _sys
import time as _time
import json as _json
import uuid as _uuid
import base64 as _base64
import struct as _struct
import socket as _socket
import threading as _td
import logging as _log
from warnings import warn as _warn


def _current_time_millis(): return int(round(_time.time() * 1000))


# settings
TIMEOUT = 5000   # ms, timeout to get response from to master container

# private utilities

# generate random ID with length 4*len characters


def _guid():
    s = str(_uuid.uuid4())
    return s

# convert from base 64 to array


def _b64toArray(base64, dtype, littleEndian=True):
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

# base 64 JSON decoder


def _decodeBase64(m):
    for d in m.values():
        if type(d) == dict and 'clazz' in d.keys():
            clazz = d['clazz']
            if clazz.startswith('[') and len(clazz) == 2 and 'data' in d.keys():
                x = _b64toArray(d['data'], d['clazz'])
                if x:
                    d = x
    return m


def _filtMsgsBin(filter, msg):
    if type(filter) == str or isinstance(filter, str):
        return 'inReplyTo' in msg.keys() and msg['inReplyTo'] == filter
    else:
        return isinstance(msg, filter)

# interface classes


class Performative:
    """
    An action represented by a message. The performative actions are a subset of the
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
    """An identifier for an agent or a topic."""

    def __init__(self, gw, name, topic=False):
        self.name = name
        if topic:
            self.topic = True
        else:
            self.topic = False
        self.gw = gw

    def getName(self):
        return self.name

    def isTopic(self):
        return self.topic

    def send(self, msg):
        msg.recipient = self.name
        return self.gw.send(msg)

    def request(self, msg, timeout=1000):
        msg.recipient = self.name
        return self.gw.request(msg, timeout)

    def __lshift__(self, msg):
        return self.request(msg)

    def __getattr__(self, param):
        rsp = self.request(ParameterReq(index=self.index).get(param))
        if rsp is None:
            return None
        return rsp.get(param)

    def __setattr__(self, param, value):
        if param in ['name', 'gw', 'is_topic', 'index']:
            self.__dict__[param] = value
            return value
        rsp = self.request(ParameterReq(index=self.index).set(param, value))
        if rsp is None:
            _warn('Could not set parameter ' + param)
            return None
        v = rsp.get(param)
        if v != value:
            _warn('Parameter ' + param + ' set to ' + str(v))
        return v

    def __getitem__(self, index):
        c = AgentID(self.gw, self.name)
        c.index = index
        return c

    def __str__(self):
        peer = self.gw.socket.getpeername()
        return self.name + ' on ' + peer[0] + ':' + str(peer[1])

    def toString(self):
        if(self.topic):
            return '#' + self.name
        return self.name

    def toJSON(self):
        return self.toString()


class Message(object):
    """
    Base class for messages transmitted by one agent to another. This class provides
    the basic attributes of messages and is typically extended by application-specific
    message classes. To ensure that messages can be sent between agents running
    on remote containers, all attributes of a message must be serializable.
    """

    def __init__(self, **kwargs):
        self.__clazz__ = 'org.arl.fjage.Message'
        self.msgID = str(_uuid.uuid4())
        self.sender = None
        self.recipient = None
        self.perf = None
        self.__dict__.update(kwargs)

    # convert a message into a JSON string
    # NOTE: we don't do any base64 encoding for TX as
    #       we don't know what data type is intended
    def _serialize(self):
        clazz = self.__clazz__
        m = self.__dict__
        t = [key for key, value in self.__dict__.items() if key.startswith('__')]
        for i in t:
            m.pop(i)
        data = _json.dumps(m, separators=(',', ':'))
        return '{ "clazz": "' + clazz + '", "data": ' + data + ' }'

    def _inflate(self, data):
        for key, value in data.items():
            self.__dict__[key] = data[key]

    def _deserialize(self, obj):
        if (type(obj) == str or isinstance(obj, str)):
            obj = json.loads(obj)
        qclazz = obj['clazz']
        clazz = qclazz.split('.')[-1]
        try:
            rv = clazz()
        except:
            rv = Message()
        rv.__clazz__ = qclazz
        rv._inflate(obj['data'])
        return rv


class GenericMessage(Message):
    def __init__(self, **kwargs):
        super()
        self.__clazz__ = 'org.arl.fjage.GenericMessage'
        self.__dict__.update(kwargs)


class Gateway:
    """ Gateway to communicate with agents from Python. Creates a gateway connecting to a specified master container.

        :param hostname: hostname to connect to.
        :param port: TCP port to connect to.
    """

    DEFAULT_TIMEOUT = 1000
    NON_BLOCKING = 0
    BLOCKING = -1

    def __init__(self, hostname, port):
        self.logger = _log.getLogger('org.arl.fjage')
        try:
            self.pending = dict()
            self.pendingOnOpen = list()
            self.aid = "PythonGW-" + _guid()
            self.subscriptions = dict()
            self.cv = _td.Condition()
            self.listener = dict()
            self.observers = list()
            self.queue = list()
            self.debug = False
            self.sock = _socket.socket(_socket.AF_INET, _socket.SOCK_STREAM)
            self.recv_thread = _td.Thread(target=self.__recv_proc)
            self.recv_thread.daemon = True
            self.logger.info("Connecting to " + str(hostname) + ":" + str(port))
            self.sock.connect((hostname, port))
            self.socket_file = self.sock.makefile('r', 65536)
            self.recv_thread.start()
        except Exception as e:
            self.logger.critical("Exception: " + str(e))
            raise

    def _OnSockRx(self, data):
        """Parse incoming messages and respond to them or dispatch them."""
        obj = _json.loads(data, object_hook=_decodeBase64)
        if self.debug:
            self.logger.debug('< ' + data)
        if 'id' in obj.keys() and obj['id'] in self.pending.keys():
            # response to a pending request to master
            self.pending[obj['id']](obj)
            del self.pending[obj['id']]
        elif obj['action'] == 'send':
            # incoming message from master
            msg = Message()
            msg = msg._deserialize(obj['message'])
            if msg.recipient == self.aid or self.subscriptions[msg.recipient]:
                for i in range(len(self.observers)):
                    if self.observers[i](msg):
                        return
                self.queue.append(msg)
                self.cv.acquire()
                self.cv.notify()
                self.cv.release()
                for key in self.listener.keys():  # iterate over internal callbacks, until one consumes the message
                    if self.listener[key]():  # callback returns true if it has consumed the message
                        break
        else:
            # respond to standard requests that every container must
            rsp = {'id': obj['id'], 'inResponseTo': obj['action']};
            if obj['action'] == 'agents':
                rsp['agentIDs'] = [self.aid]
            elif obj['action'] == 'containsAgent':
                rsp['answer'] = (obj['agentID'] == self.aid)
            elif obj['action'] == 'services':
                rsp['services'] = []
            elif obj['action'] == 'agentForService':
                rsp['agentID'] = ''
            elif obj['action'] == 'agentsForService':
                rsp['agentIDs'] = []
            else:
                rsp = None
            if rsp:
                self._sockTx(rsp)

    def _sockTx(self, s):
        if type(s) != str and not isinstance(s, str):
            s = _json.dumps(s)
        if self.debug:
            self.logger.debug('> ' + s)
        self.sock.sendall((s + '\n').encode())
        return True

    def _sockTxRx(self, rq):
        self._sockTx(rq)

    def __recv_proc(self):
        """Receive process."""
        parenthesis_count = 0
        rmsg = ""
        name = self.sock.getpeername()
        while True:
            try:
                rmsg = self.socket_file.readline()
                if not rmsg:
                    print('The remote connection is closed!')
                    break
                self.logger.debug(str(name[0]) + ":" + str(name[1]) + " <<< " + rmsg)
                # Parse and dispatch incoming messages
                self._OnSockRx(rmsg)
            except:
                self.logger.critical("Exception: " + str(e))
                pass
            self.close()

    def _getMessageFromQueue(self, filter):
        if not len(self.queue):
            return
        if not filter:
            return self.queue.pop(0)
        filtMsgs = []
        for msg in self.queue:
            if _filtMsgsBin(filter, msg):
                filtMsgs.append(msg)
        if filtMsgs:
            self.queue.pop(self.queue.index(filtMsgs[0]))
            return filtMsgs[0]

    # creates a unqualified message class based on a fully qualified name
    def importmsg(self, name):
        sname = name.split('.')[-1]
        class_ = type(sname, (Message,), {"__clazz__": name})
        globals()[sname] = class_

    def request(self, msg, timeout=10000):
        """Sends a request and waits for a response. This method blocks until timeout if no response is received.

        :param msg: message to send.
        :param timeout: timeout in milliseconds.
        :returns: received response message, null on timeout.
        """
        self.send(msg)
        rsp = self.receive(msg, timeout)
        return rsp

    def receive(self, filter=None, timeout=0):
        """
        Returns a message received by the gateway and matching the given filter. This method blocks until timeout if no message available.

        :param filter: message filter.
        :param timeout: timeout in milliseconds.
        :returns: received message matching the filter, null on timeout.
        """
        rmsg = self._getMessageFromQueue(filter)
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
                rmsg = self._getMessageFromQueue(filter)
        if not rmsg:
            return None
        return rmsg

    def addMessageListener(self, listener):
        self.observers.append(listener)

    def removeMessageListener(self, listener):
        ndx = self.observers.index(listener)
        if ndx >= 0:
            self.observers.pop(ndx)

    def getAgentID(self):
        return self.aid

    def agent(self, name):
        return AgentId(self, name, False)

    def topic(self, topic, topic2):
        if type(topic) == str or isinstance(topic, str):
            return AgentID(self, topic, True)
        if isinstance(topic, AgentID):
            if topic.isTopic():
                return topic
            return AgentID(self, topic.getName() + (topic2 + '__' if topic2 else '') + '__ntf', True)

    def subscribe(self, topic):
        if not topic.isTopic():
            topic = AgentID(self, topic, True)
        self.subscriptions[topic.toString()] = True

    def unsubscribe(self, topic):
        if not topic.isTopic():
            topic = AgentID(self, topic, True)
        del self.subscriptions[topic.toString()]

    def agentForService(self, service):
        rq = {'action': 'agentForService', 'service': 'service'}
        rsp = self._sockTxRx(rq)
        return AgentID(self, rsp.agentID, False)

    def agentsForService(self, service):
        rq = {'action': 'agentForService', 'service': 'service'}
        rsp = self._sockTxRx(rq)
        aids = []
        for i in range(len(rsp.agentIds)):
            aids.append(AgentID(self, rsp.agentIDs[i], False))
        return aids

    def send(self, msg, relay=True):
        msg.sender = self.aid
        if msg.perf == None:
            if msg.__clazz__.endswith('Req'):
                msg.perf = Performative.REQUEST
            else:
                msg.perf = Performative.INFORM
        rq = _json.dumps({'action': 'send', 'relay': relay, 'message': '###MSG###'})
        rq = rq.replace('"###MSG###"', msg._serialize())
        self._sockTx(rq)

    def flush(self):
        self.queue.clear()

    def close(self):
        """ Closes the gateway. The gateway functionality may not longer be accessed after this method is called."""

        try:
            self.sock.shutdown(_socket.SHUT_RDWR)
            # self.socket.close()
        except Exception as e:
            self.logger.critical("Exception: " + str(e))
        print('The gateway connection is closed!')
