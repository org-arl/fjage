
import uuid
import queue
import logging
import threading
import enum
from typing import Any, Optional, Union, Callable, Type, Generic, TypeVar, Optional


from .Connector import Connector
from .TCPConnector import TCPConnector
from .AgentID import AgentID
from .JSONMessage import JSONMessage, Actions
from .Message import Message
from .Utils import UUID7

DEFAULT_RECONNECT_DELAY = 2.0  # seconds
DEFAULT_MAX_QUEUE_SIZE = 512

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())

class Gateway:
    """ A Gateway is used to connect to a fjåge container and send and receive messages,
    query for agents and services, and set or get parameters on agents from Python.
    """

    NON_BLOCKING = 0
    BLOCKING = -1

    def __init__(self, hostname: str = 'localhost', port: int = 1100, connector: Type[Connector] = TCPConnector, reconnect: bool = True, timeout: int = 10000) -> None:
        """Creates a new Gateway instance.

        Args:
            hostname : hostname of the fjage container. Defaults to 'localhost'.
            port : port of the fjage container. Defaults to 1100.
            connector : Connector class to use. Defaults to TCPConnector.
            keep_alive : whether to keep the connection alive. Defaults to True.
            timeout : default timeout in milliseconds for requests. Defaults to 10000.
        """
        if not isinstance(hostname, str) or not hostname:
            raise ValueError("hostname must be a non-empty string")
        if not isinstance(port, int) or not (0 < port < 65536):
            raise ValueError("port must be an integer 1-65535")
        if not issubclass(connector, Connector):
            raise ValueError("connector must be a subclass of Connector")
        if not isinstance(reconnect, bool):
            raise ValueError("keep_alive must be a boolean")
        if not isinstance(timeout, int) or timeout <= 0:
            raise ValueError("timeout must be a positive integer")

        self._pending_actions = dict()
        self._pending_requests = dict()
        self._queue = list()
        self._max_queue_size = DEFAULT_MAX_QUEUE_SIZE
        self._subscriptions = dict()
        self._timeout = timeout
        self.aid = AgentID("gateway-" + str(uuid.uuid4()), owner=self)
        self.connector = connector(hostname, port, -1 if not reconnect else DEFAULT_RECONNECT_DELAY)
        self.connector.set_receive_callback(self._msg_rx)
        try :
            self.connector.connect()
        except Exception as e:
            logger.error(f"Failed to connect to {hostname}:{port}: {e}")
            raise e
        self.connector.send('{"alive": true}')
        self._update_watch()

    def send(self, msg: Message) -> None:
        """Sends a message to the fjage container.

        Args:
            msg : message to send
        """
        if not isinstance(msg, Message):
            raise ValueError("msg must be a Message")

        msg.sender = self.aid
        json_msg = JSONMessage.createSend(msg=msg, relay=True)
        self._msg_tx(json_msg)

    def receive(self, filter: Union[Callable, Type[Message], Message, None]=None, timeout: Optional[int] = None) -> Any:
        """Receives a message from the fjage container.

        Args:
            filter : filter to apply to incoming messages
            timeout : timeout in milliseconds. Defaults to None, which uses the gateway's default timeout.

        Returns:
            Message or None: received message or None if timeout
        """

        if timeout is None:
            timeout = self._timeout
        if not isinstance(timeout, int):
            raise ValueError("timeout must be an integer")

        msg = self._retrieve_from_queue(filter)
        if (msg is not None or timeout == self.NON_BLOCKING):
            return msg

        lid = str(UUID7.generate())
        self._pending_requests[lid] = ChannelFilter(filter)
        if timeout is not self.BLOCKING:
            try:
                msg = self._pending_requests[lid].get(timeout=timeout / 1000)
            except queue.Empty:
                logger.warning(f"Receive timeout after {timeout} ms for filter {filter}")
                msg = None
        else:
            msg = self._pending_requests[lid].get()
        if lid in self._pending_requests:
            del self._pending_requests[lid]
        return msg

    def request(self, msg: Message, timeout: Optional[int] = None) -> Any:
        """Sends a request message and waits for a reply.

        Args:
            msg : request message to send
            timeout : timeout in milliseconds. Defaults to None, which uses the gateway's default timeout.

        Returns:
            Message or None: reply message or None if timeout
        """
        if not isinstance(msg, Message):
            raise ValueError("msg must be a Message")
        if timeout is None:
            timeout = self._timeout
        if not isinstance(timeout, int):
            raise ValueError("timeout must be an integer")

        # Create a unique msgID for this request
        self.send(msg)
        return self.receive(msg, timeout=timeout)

    def agent_id(self) -> AgentID:
        """Gets the AgentID of this gateway.

        Returns:
            AgentID: AgentID of this gateway
        """
        return self.aid

    def agent(self, name) -> AgentID:
        """Creates an AgentID for the given agent name."""
        return AgentID(name, owner=self)

    def topic(self, topic: Union[str, AgentID], topic2: Optional[str] = None) -> AgentID:
        """Creates a topic with the given name or based on the given AgentID."""
        if (isinstance(topic, str)):
            return AgentID(topic, topic=True, owner=self)
        if (isinstance(topic, AgentID)):
            if (topic.is_topic()):
                return topic
            return AgentID(topic.get_name() + (f"__{topic2}" if topic2 else "") + "__ntf", topic=True, owner=self)

    def subscribe(self, topic: AgentID) -> bool:
        """Subscribes to the given topic."""
        if (topic.is_topic() == False):
           topic = AgentID(topic.get_name() + '__ntf', topic=True, owner=self)
        self._subscriptions[topic] = True
        self._update_watch()
        return True

    def unsubscribe(self, topic: AgentID) -> bool:
        """Unsubscribes from the given topic."""
        if (topic.is_topic() == False):
           topic = AgentID(topic.get_name() + '__ntf', topic=True, owner=self)
        if topic in self._subscriptions:
            self._subscriptions[topic] = False
            self._update_watch()
            return True
        return False

    def agents(self, timeout:int = None) -> list[AgentID]:
        """Gets the list of all agents connected to the fjage container"""
        if timeout is None:
            timeout = self._timeout
        json_msg = JSONMessage.createAgents()
        rsp = self._msg_tx_rx(json_msg, timeout)
        if rsp is not None and hasattr(rsp, 'agentIDs') and isinstance(rsp.agentIDs, list):
            return rsp.agentIDs
        logger.debug("Response to agents() request does not contain agentIDs")
        return []

    def containsAgent(self, agentID: AgentID, timeout: int = None) -> bool:
        """Checks if the given agent is connected to the fjage container"""
        if timeout is None:
            timeout = self._timeout
        json_msg = JSONMessage.createContainsAgent(agentID=agentID)
        rsp = self._msg_tx_rx(json_msg, timeout)
        if rsp is not None and hasattr(rsp, 'answer') and isinstance(rsp.answer, bool):
            return rsp.answer
        logger.debug("Response to containsAgent() request does not contain answer")
        return False


    def agentForService(self, service: Union[str, enum.Enum], timeout:int = None) -> Optional[AgentID]:
        """Finds an agent that provides the given service."""
        if timeout is None:
            timeout = self._timeout
        if isinstance(service, enum.Enum):
            service = service.value
        json_msg = JSONMessage.createAgentForService(service=service)
        rsp = self._msg_tx_rx(json_msg, timeout)
        if rsp is not None and hasattr(rsp, 'agentID') and isinstance(rsp.agentID, AgentID):
            return rsp.agentID
        logger.debug("Response to agentForService() request does not contain agentID")
        return None

    def agentsForService(self, service: Union[str, enum.Enum], timeout: int = None) -> list[AgentID]:
        """Retrieves a list of all agents that provide the given service."""
        if timeout is None:
            timeout = self._timeout
        if isinstance(service, enum.Enum):
            service = service.value
        json_msg = JSONMessage.createAgentsForService(service=service)
        rsp = self._msg_tx_rx(json_msg, timeout)
        if rsp is not None and hasattr(rsp, 'agentIDs') and isinstance(rsp.agentIDs, list):
            return rsp.agentIDs
        logger.debug("Response to agentsForService() request does not contain agentIDs")
        return []

    def flush(self):
        """Clears the internal message queue."""
        self._queue.clear()

    def close(self):
        """Closes the gateway and disconnects from the fjage container."""
        self.connector.disconnect()

    @staticmethod
    def match_filter(filter: Union[Callable, Type[Message], Message, None], msg: Message) -> bool:
        if filter is None:
            return True
        if isinstance(filter, Message) and hasattr(filter, 'msgID'):
            return getattr(msg, 'inReplyTo', None) == filter.msgID
        if isinstance(filter, type):
            return isinstance(msg, filter)
        if callable(filter):
            try:
                return filter(msg)
            except Exception as e:
                logger.error(f"Error in filter function: {e}")
                return False
        return False

    # Internal helper methods
    def _retrieve_from_queue(self, filter: Union[Callable, Type[Message], Message, None]) -> Optional[Message]:
        if not self._queue or len(self._queue) == 0:
            return None

        # No filter: return first message
        if filter is None:
            return self._queue.pop(0)

        for i, msg in enumerate(self._queue):
            if Gateway.match_filter(filter, msg):
                return self._queue.pop(i)

        return None

    def _send_receivers(self, msg: Message) -> bool:
        for key, receiver in self._pending_requests.items():
                if receiver.tryput(msg):
                    del self._pending_requests[key]
                    return True
        return False

    def _msg_rx(self, messages: list) -> None:
        for msg in messages:
            logger.debug(f"<<< {msg}")
            try:
                json_msg = JSONMessage(msg, owner=self)
                if hasattr(json_msg, 'id') and json_msg.id in self._pending_actions:
                    self._pending_actions[json_msg.id].put(json_msg)
                    del self._pending_actions[json_msg.id]

                elif (json_msg.action == Actions.SEND.value and hasattr(json_msg, 'message') and isinstance(json_msg.message, Message)):
                    msg = json_msg.message
                    if msg.recipient == self.aid or msg.recipient in self._subscriptions.keys():
                        # if not consumed by any of the receivers, then queue it
                        if not self._send_receivers(msg):
                            self._queue.append(msg)
                            if len(self._queue) > self._max_queue_size:
                                self._queue.pop(0)
                else:
                    rsp = JSONMessage()
                    rsp.id = json_msg.id
                    rsp.inResponseTo = json_msg.action
                    if json_msg.action == Actions.AGENTS.value:
                        rsp.agentIDs = [self.aid]
                    elif json_msg.action == Actions.CONTAINS_AGENT.value:
                        rsp.answer = json_msg.agentID == self.aid
                    elif json_msg.action == Actions.SERVICES.value:
                        rsp.services = []
                    elif json_msg.action == Actions.AGENT_FOR_SERVICE.value:
                        rsp.agentID = None
                    elif json_msg.action == Actions.AGENTS_FOR_SERVICE.value:
                        rsp.agentIDs = []
                    else:
                        rsp = None

                    if rsp:
                        self._msg_tx(rsp)

            except Exception as e:
                logger.warning(f"Failed to parse JSONMessage: {e}")
                continue

    def _msg_tx(self, json_msg: JSONMessage) -> None:
        """Sends a JSONMessage to the fjage container.

        Args:
            json_msg : message to send
        """
        if not isinstance(json_msg, JSONMessage):
            raise ValueError("json_msg must be a JSONMessage")
        json_str = json_msg.to_json()
        logger.debug(f">>> {json_str}")
        self.connector.send(json_str)

    def _msg_tx_rx(self, json_msg: JSONMessage, timeout: int = None) -> Optional[JSONMessage]:
        """Sends a JSONMessage and waits for a response.

        Args:
            json_msg : message to send
            timeout : timeout in milliseconds

        Returns:
            JSONMessage or None: response message or None if timeout
        """
        if not isinstance(json_msg, JSONMessage):
            raise ValueError("json_msg must be a JSONMessage")
        if timeout is None:
            timeout = self._timeout
        if not isinstance(timeout, int):
            raise ValueError("timeout must be an integer")

        self._pending_actions[json_msg.id] = OneShotChannel[JSONMessage]()
        self._msg_tx(json_msg)
        if timeout is not self.BLOCKING:
            try:
                msg = self._pending_actions[json_msg.id].get(timeout=timeout / 1000)
            except queue.Empty:
                logger.warning(f"Receive timeout after {timeout} ms for {json_msg.action}[{json_msg.id}] request")
                msg = None
        else:
            msg = self._pending_actions[json_msg.id].get()
        if json_msg.id in self._pending_actions:
            del self._pending_actions[json_msg.id]
        return msg

    def _update_watch(self):
        watch = [aid for aid, subscribed in self._subscriptions.items() if subscribed]
        watch.append(self.aid)
        json_msg = JSONMessage.createWantsMessagesFor(agentIDs=watch)
        self._msg_tx(json_msg)

    def is_connected(self) -> bool:
        """Returns True if the gateway is connected to the fjage container.

        Returns:
            bool: True if connected, False otherwise
        """
        return self.connector.is_connected()

    def __enter__(self) -> "Gateway":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.close()

    def __str__(self) -> str:
        return f"Gateway(hostname={self.connector.host} port={self.connector.port} connected={self.is_connected()})"

    def _repr_pretty_(self, p, cycle):
        p.text(str(self) if not cycle else '...')

T = TypeVar("T")
class OneShotChannel(Generic[T]):
    def __init__(self):
        self._cond = threading.Condition()
        self._value: Optional[T] = None
        self._has_value = False

    def put(self, value: T) -> None:
        with self._cond:
            # overwrite if a value is still waiting
            self._value = value
            self._has_value = True
            self._cond.notify()

    def get(self, timeout: Optional[float] = None) -> Optional[T]:
        with self._cond:
            ok = self._cond.wait_for(lambda: self._has_value, timeout)
            if not ok:
                return None
            val = self._value
            self._value = None
            self._has_value = False
            return val

class ChannelFilter:
    def __init__(self, filter:Union[Callable, Type[Message], Message, None]):
        self.channel = OneShotChannel[JSONMessage]()
        self.filter = filter

    def get(self, timeout: Optional[float] = None) -> Message:
        return self.channel.get(timeout)

    def tryput(self, msg: Message) -> bool:
        if Gateway.match_filter(self.filter, msg):
            self.channel.put(msg)
            return True
        return False
