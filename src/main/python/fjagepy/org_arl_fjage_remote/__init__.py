import os as _os
import sys as _sys
import json as _json
import uuid as _uuid
import time as _time
import socket as _socket
import threading as _td
import logging as _log
import fjagepy
import base64
import struct
import numpy
from collections import OrderedDict
from fjagepy.org_arl_fjage import AgentID
from fjagepy.org_arl_fjage import Message
from fjagepy.org_arl_fjage import GenericMessage


def current_time_millis(): return int(round(_time.time() * 1000))


class Action:
    """
    JSON message actions.

    """

    AGENTS = "agents"
    CONTAINS_AGENT = "containsAgent"
    SERVICES = "services"
    AGENT_FOR_SERVICE = "agentForService"
    AGENTS_FOR_SERVICE = "agentsForService"
    SEND = "send"
    SHUTDOWN = "shutdown"


class Gateway:
    """ Gateway to communicate with agents from Python. Creates a gateway connecting to a specified master container.

        :param hostname: hostname to connect to.
        :param port: TCP port to connect to.
    """

    DEFAULT_TIMEOUT = 1000
    NON_BLOCKING = 0
    BLOCKING = -1

    def __init__(self, hostname, port, name=None):
        """NOTE: Developer must make sure a duplicate name is not assigned to the Gateway."""

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
        """Parse incoming messages and respond to them or dispatch them."""

        req = _json.loads(rmsg)
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
        """Receive process."""

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
        self.close()

    def __del__(self):
        try:
            self.socket.close()
        except Exception as e:
            self.logger.critical("Exception: " + str(e))

    def shutdown(self):
        """ Shutdown the platform."""

        j_dict = dict()
        j_dict["action"] = Action.SHUTDOWN
        self.socket.sendall((_json.dumps(j_dict) + '\n').encode())

    def close(self):
        """ Closes the gateway. The gateway functionality may not longer be accessed after this method is called."""

        try:
            self.socket.close()
        except Exception as e:
            self.logger.critical("Exception: " + str(e))
        print('The gateway connection is closed!')

    def send(self, msg, relay=True):
        """Sends a message to the recipient indicated in the message. The recipient may be an agent or a topic."""

        if not msg.recipient:
            return False
        j_dict = dict()
        m_dict = OrderedDict()
        d_dict = dict()
        j_dict["action"] = Action.SEND
        j_dict["relay"] = relay
        msg.sender = self.name
        d_dict = self._to_json(msg)
        module_ = msg.__module__
        m_dict["clazz"] = module_.split('.')[-1].replace("_", ".") + "." + msg.__class__.__name__
        m_dict["data"] = d_dict
        j_dict["message"] = m_dict
        if msg.__class__.__name__ == GenericMessage().__class__.__name__:
            j_dict["map"] = msg.map
        json_str = _json.dumps(j_dict)
        name = self.socket.getpeername()
        self.logger.debug(str(name[0]) + ":" + str(name[1]) + " >>> " + json_str)
        self.socket.sendall((json_str + '\n').encode())
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
        """
        Returns a message received by the gateway and matching the given filter. This method blocks until timeout if no message available.

        :param filter: message filter.
        :param timeout: timeout in milliseconds.
        :returns: received message matching the filter, null on timeout.
        """

        rmsg = self._retrieveFromQueue(filter)
        if (rmsg == None and timeout != self.NON_BLOCKING):
            deadline = current_time_millis() + timeout
            while (rmsg == None and (timeout == self.BLOCKING or current_time_millis() < deadline)):
                if timeout == self.BLOCKING:
                    self.cv.acquire()
                    self.cv.wait()
                    self.cv.release()
                elif timeout > 0:
                    self.cv.acquire()
                    t = deadline - current_time_millis()
                    self.cv.wait(t / 1000)
                    self.cv.release()
                rmsg = self._retrieveFromQueue(filter)
        if not rmsg:
            return None
        try:
            rsp = self._from_json(rmsg)
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
        j_dict = dict()
        j_dict["action"] = Action.AGENT_FOR_SERVICE
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
        """ Returns the gateway Agent ID."""
        return self.name

    def _to_json(self, inst):
        """Convert the object attributes to a dict."""

        dt = inst.__dict__.copy()
        for i in list(dt):
            if i == 'data' or i == 'signal':
                if type(dt[i]) == numpy.ndarray:
                    dt[i] = dt[i].tolist()
        for key in list(dt):
            if dt[key] == None:
                dt.pop(key)
            elif list(key)[-1] == '_':
                dt[key[:-1]] = dt.pop(key)
            if key == 'map':
                dt.pop(key)
        return dt

    def _from_json(self, dt):
        """If possible, do class loading, else return the dict."""

        if 'clazz' in dt:
            class_name = dt['clazz'].split(".")[-1]
            module_name = dt['clazz'].split(".")
            module_name.remove(module_name[-1])
            if "fjage" in module_name:
                module_name = "fjagepy." + "_".join(module_name)
            else:
                module_name = "unetpy." + "_".join(module_name)
            try:
                module = __import__(module_name)
            except Exception as e:
                self.logger.critical("Exception in from_json, module: " + str(e))
                return dt
            try:
                class_ = getattr(module, class_name)
            except Exception as e:
                self.logger.critical("Exception in from_json, class: " + str(e))
                return dt
            args = dict()
            for key, value in dt["data"].items():
                args[key] = value
            if 'signal' in args.keys():
                type_ = args['signal']['clazz']
                x_ = base64.standard_b64decode(args['signal']['data'])
                count = len(x_) // 4
                if 'F' in type_:
                    x_ = struct.unpack('<{0}f'.format(count), x_)
                elif 'I' in type_:
                    x_ = struct.unpack('<{0}i'.format(count), x_)
                elif 'D' in type_:
                    x_ = struct.unpack('<{0}d'.format(count), x_)
                elif 'J' in type_:
                    x_ = struct.unpack('<{0}l'.format(count), x_)
                args["signal"]["data"] = list(x_)
                del args["signal"]["clazz"]
                args["signal"] = args["signal"]["data"]
            elif 'data' in args.keys():
                type_ = args['data']['clazz']
                x_ = base64.standard_b64decode(args['data']['data'])
                count = len(x_) // 4
                if 'F' in type_:
                    x_ = struct.unpack('<{0}f'.format(count), x_)
                elif 'I' in type_:
                    x_ = struct.unpack('<{0}i'.format(count), x_)
                elif 'D' in type_:
                    x_ = struct.unpack('<{0}d'.format(count), x_)
                elif 'J' in type_:
                    x_ = struct.unpack('<{0}l'.format(count), x_)
                args["data"]["data"] = list(x_)
                del args["data"]["clazz"]
                args["data"] = args["data"]["data"]
            elif 'values' or 'value' in args.keys():
                if 'values' in args.keys():
                    for i, j in args["values"].items():
                        if isinstance(j, dict):
                            if 'clazz' in j and '[' in j['clazz']:
                                type_ = j['data']['clazz']
                                if 'F' in type_:
                                    x_ = numpy.frombuffer(base64.decodestring(bytearray(j['data']['data'], 'utf-8')), dtype=numpy.float32)
                                    args["values"][i] = list(x_)
                                elif 'I' in type_:
                                    x_ = numpy.frombuffer(base64.decodestring(bytearray(j['data']['data'], 'utf-8')), dtype=numpy.int16)
                                    args["values"][i] = list(x_)
                                elif 'D' in type_:
                                    x_ = numpy.frombuffer(base64.decodestring(bytearray(j['data']['data'], 'utf-8')), dtype=numpy.float64)
                                    args["values"][i] = list(x_)
                                elif 'J' in type_:
                                    x_ = numpy.frombuffer(base64.decodestring(bytearray(j['data']['data'], 'utf-8')), dtype=numpy.int32)
                                    args["values"][i] = list(x_)
                elif 'value' in args.keys():
                    for i, j in args["value"].items():
                        if isinstance(j, dict):
                            if 'clazz' in j and '[' in j['clazz']:
                                type_ = j['clazz']
                                if 'F' in type_:
                                    x_ = numpy.frombuffer(base64.decodestring(bytearray(j['data'], 'utf-8')), dtype=numpy.float32)
                                    args["value"] = list(x_)
                                elif 'I' in type_:
                                    x_ = numpy.frombuffer(base64.decodestring(bytearray(j['data'], 'utf-8')), dtype=numpy.int16)
                                    args["value"] = list(x_)
                                elif 'D' in type_:
                                    x_ = numpy.frombuffer(base64.decodestring(bytearray(j['data'], 'utf-8')), dtype=numpy.float64)
                                    args["value"] = list(x_)
                                elif 'J' in type_:
                                    x_ = numpy.frombuffer(base64.decodestring(bytearray(j['data'], 'utf-8')), dtype=numpy.int32)
                                    args["value"] = list(x_)
            inst = class_(**args)
        else:
            inst = dt
        return inst

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
