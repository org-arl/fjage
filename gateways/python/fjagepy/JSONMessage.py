import json
import base64
import struct
import logging
from enum import Enum
from typing import List, Optional, Any

from .AgentID import AgentID
from .Message import Message
from .Utils import UUID7

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())

class Actions(Enum):
    AGENTS = "agents"
    CONTAINS_AGENT = "containsAgent"
    SERVICES = "services"
    AGENT_FOR_SERVICE = "agentForService"
    AGENTS_FOR_SERVICE = "agentsForService"
    SEND = "send"
    SHUTDOWN = "shutdown"
    WANTS_MESSAGES_FOR = "wantsMessagesFor"

class JSONMessage:
    """A JSONMessage is used to create and parse the on-the-wire JSON based protocol
    used by fjåge containers. It can be used to send and receive messages, query
    for agents and services, and set or get parameters on agents. The JSONMessage class
    provides static factory methods for creating common types of JSON messages and also
    can parse a JSON string into a JSONMessage object."""

    def __init__(self, json_str: Optional[str] = None, owner = None) -> None:
        # Attributes
        self.id: str = str(UUID7.generate())
        self.action: Optional[str] = None
        self.inResponseTo: Optional[str] = None
        self.agentID: Optional[AgentID] = None
        self.agentIDs: Optional[List[AgentID]] = None
        self.agentTypes: Optional[List[str]] = None
        self.service: Optional[str] = None
        self.services: Optional[List[str]] = None
        self.answer: Optional[bool] = None
        self.message: Optional[Message] = None
        self.relay: Optional[bool] = None

        # Deserialize if string provided
        if json_str:
            obj = json.loads(json_str, object_hook=self._decode_base64)
            for k, v in obj.items():
                if k == "message" and isinstance(v, dict):
                    self.message = Message.from_json(v, owner=owner)
                elif k == "agentID" :
                    self.agentID = AgentID.from_json(v, owner=owner)
                elif k == "agentIDs" and isinstance(v, list):
                    self.agentIDs = [AgentID.from_json(a, owner=owner) for a in v]
                else:
                    if hasattr(self, k):
                        setattr(self, k, v)
                    else:
                        logger.warning(f"Unknown attribute '{k}' in JSONMessage")


    def _decode_base64(self, obj: dict) -> dict:
        if (
            isinstance(obj, dict)
            and obj.get("clazz", "").startswith("[")
            and len(obj.get("clazz", "")) == 2
            and "data" in obj
        ):
            dtype = obj["clazz"]
            data = base64.b64decode(obj["data"])
            fmt_map = {
                "[B": "b",
                "[S": "h",
                "[I": "i",
                "[J": "q",
                "[F": "f",
                "[D": "d",
            }
            if dtype in fmt_map:
                fmt = fmt_map[dtype]
                count = len(data) // struct.calcsize(fmt)
                endian = "<" # fjåge JSON base64 binary encoding is little-endian
                unpack_fmt = f"{endian}{count}{fmt}"
                return list(struct.unpack(unpack_fmt, data))
        return obj

    def __repr__(self) -> str:
        return f"JSONMessage(id={self.id}, action={self.action}, inResponseTo={self.inResponseTo}, agentID={self.agentID}, agentIDs={self.agentIDs}, service={self.service}, services={self.services}, answer={self.answer}, message={self.message}, relay={self.relay})"


    def to_json(self) -> str:
        obj = {k: v for k, v in self.__dict__.items() if v is not None}
        return json.dumps(obj, default=self._json_default, separators=(",", ":"))

    @staticmethod
    def _json_default(obj) -> Any | str:
        if hasattr(obj, 'to_json') and callable(getattr(obj, 'to_json')):
            return obj.to_json()
        elif (isinstance(obj, Enum)):
            return obj.value
        raise TypeError(f"Object of type {obj.__class__.__name__} is not JSON serializable")

    # Static factory methods for common JSONMessage types

    @staticmethod
    def createSend(msg: Message, relay: bool = False) -> "JSONMessage":
        if not isinstance(msg, Message):
            raise ValueError("msg must be an instance of Message")
        jm = JSONMessage()
        jm.action = Actions.SEND.value
        jm.relay = relay
        jm.message = msg
        return jm

    @staticmethod
    def createWantsMessagesFor(agentIDs: List[AgentID]) -> "JSONMessage":
        if not agentIDs:
            raise ValueError("agentIDs must be a non-empty list")
        jm = JSONMessage()
        jm.action = Actions.WANTS_MESSAGES_FOR.value
        jm.agentIDs = agentIDs
        return jm

    @staticmethod
    def createAgents() -> "JSONMessage":
        jm = JSONMessage()
        jm.action = Actions.AGENTS.value
        return jm

    @staticmethod
    def createContainsAgent(agentID: AgentID) -> "JSONMessage":
        if not isinstance(agentID, AgentID):
            raise ValueError("agentID must be an instance of AgentID")
        jm = JSONMessage()
        jm.action = Actions.CONTAINS_AGENT.value
        jm.agentID = agentID
        return jm

    @staticmethod
    def createAgentForService(service: str) -> "JSONMessage":
        if not isinstance(service, str) or not service:
            raise ValueError("service must be a non-empty string")
        jm = JSONMessage()
        jm.action = Actions.AGENT_FOR_SERVICE.value
        jm.service = service
        return jm

    @staticmethod
    def createAgentsForService(service: str) -> "JSONMessage":
        if not isinstance(service, str) or not service:
            raise ValueError("service must be a non-empty string")
        jm = JSONMessage()
        jm.action = Actions.AGENTS_FOR_SERVICE.value
        jm.service = service
        return jm