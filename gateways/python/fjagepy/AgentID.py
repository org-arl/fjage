from __future__ import annotations

import logging
from typing import Optional, Self, Any

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())

# Default timeout for non-owned AgentIDs (in milliseconds)
DEFAULT_TIMEOUT = 10000

class AgentID:
    """An identifier for an agent or a topic. This can be used to send, receive messages,
    and set or get parameters on an agent or topic on the fjåge container.

    Args:
        name : name of the agent
        topic : True if this represents a topic. Defaults to False.
        owner : Gateway owner for this AgentID. Defaults to None.
    """

    def __init__(self, name: str, topic: bool = False, owner = None) -> None:
        if not isinstance(name, str) or not name:
            raise ValueError("AgentID name must be a non-empty string")
        self.name = name
        self.topic = topic
        self.owner = owner
        self.index = -1  # for indexed parameters
        self._timeout = owner._timeout if owner else DEFAULT_TIMEOUT

    def get_name(self) -> str:
        """Gets the name of the agent or topic.

        Returns:
            str: name of agent or topic
        """
        return self.name

    def is_topic(self) -> bool:
        """Returns True if the agent id represents a topic.

        Returns:
            bool: True if the agent id represents a topic, False if it represents an agent
        """
        return self.topic

    def send(self, msg:Message) -> None:
        """Sends a message to the agent represented by this id.

        Args:
            msg: message to send

        Raises:
            RuntimeError: if this AgentID has no owner (unowned AgentID cannot send messages)
        """
        msg.recipient = self
        if self.owner:
            self.owner.send(msg)
        else:
            raise RuntimeError('Unowned AgentID cannot send messages')

    def request(self, msg:Message, timeout: Optional[int] = None) -> Optional[Message]:
        """Sends a request to the agent represented by this id and waits for a response.

        Args:
            msg: request to send
            timeout (int, optional): timeout in milliseconds. Defaults to owner's timeout.

        Returns:
            Response message

        Raises:
            RuntimeError: if this AgentID has no owner (unowned AgentID cannot send messages)
        """
        if timeout is None:
            timeout = self._timeout
        msg.recipient = self
        if self.owner:
            return self.owner.request(msg, timeout)
        else:
            raise RuntimeError('Unowned AgentID cannot send messages')

    def get(self, index: Optional[int] = -1) -> dict[str, Any]:
        """Gets the values of all parameters on the agent.

        Args:
            index : index for indexed parameters. Defaults to -1 (no index).

        Returns:
            dict: dictionary of all parameters and their values

        Raises:
            RuntimeError: if this AgentID has no owner (unowned AgentID cannot get parameters)
        """
        from .Message import ParameterReq

        rsp = self.request(ParameterReq(index=index))
        if rsp is None or 'param' not in rsp.__dict__ or 'value' not in rsp.__dict__:
            return {}

        # the first parameter is in rsp.param and rsp.value and the others are in the dict rsp.values,
        # we combine them into a single dictionary
        params = {}
        if 'param' in rsp.__dict__ and 'value' in rsp.__dict__:
            params[rsp.param] = rsp.value
        if 'values' in rsp.__dict__ and isinstance(rsp.values, dict):
            params.update(rsp.values)
        return params

    def to_json(self) -> str:
        """Gets a JSON string representation of the agent id.

        Returns:
            str: JSON string representation of the agent id

        :meta private:
        """
        return ('#' if self.topic else '') + self.name

    @staticmethod
    def from_json(json_str: str, owner = None) -> "AgentID":
        """Inflate the AgentID from a JSON string.

        Args:
            json_str : JSON string to be converted to an AgentID
            owner: Gateway owner for this AgentID. Defaults to None.

        Returns:
            AgentID: AgentID created from the JSON string

        :meta private:
        """

        json_str = json_str.strip()
        if json_str.startswith('#'):
            return AgentID(json_str[1:], topic=True, owner=owner)
        else:
            return AgentID(json_str, topic=False, owner=owner)

    def __eq__(self, other) -> bool:
        if not isinstance(other, AgentID):
            return False
        return (self.name == other.name) and (self.topic == other.topic)

    def __hash__(self) -> int:
        return hash(self.to_json())

    def __str__(self) -> str:
        """Gets a string representation of the agent id.

        Returns:
            str: string representation of the agent id
        """
        owner_str = f"Gateway({self.owner.connector.host}:{self.owner.connector.port})" if self.owner else 'none'
        return f"AgentID(name={self.name}, topic={self.topic}, owner={owner_str})"

    def _repr_pretty_(self, p, cycle) -> None:
        """Pretty print support for IPython/Jupyter."""
        if (self.owner is None) or (not self.owner.is_connected()):
            p.text(str(self) if not cycle else '...')
            return

        # Print a Java style Agent information
        from .Message import ParameterReq
        rsp = self.request(ParameterReq(index=self.index))
        if rsp is None or 'param' not in rsp.__dict__ or 'value' not in rsp.__dict__:
            p.text(str(self) if not cycle else '...')
            return

        # the first parameter is in rsp.param and rsp.value and the others are in the dict rsp.values,
        # we combine them into a single dictionary
        params = {}
        if 'param' in rsp.__dict__ and 'value' in rsp.__dict__:
            params[rsp.param] = rsp.value
        if 'values' in rsp.__dict__ and isinstance(rsp.values, dict):
            params.update(rsp.values)

        if 'title' in params:
            p.text('<<< ' + str(params['title']) + ' >>>\n')
        else:
            p.text('<<< ' + str(self.name).upper() + ' >>>\n')
        if 'description' in params:
            p.text('\n' + str(params['description']) + '\n')

        # First we take all the parameters and sort them alphabetically
        param_names = sorted(params.keys())

        # Then we split them into lists based on the prefix. Everything before the
        # final dot is considered a section.
        sections = {}
        for param in param_names:
            if '.' in param:
                section, subparam = param.rsplit('.', 1)
            elif param == 'title' or param == 'description':
                continue
            else:
                section, subparam = '', param
            if section not in sections:
                sections[section] = []
            sections[section].append((subparam, params[param], param))


        for section in sorted(sections.keys()):
            if section:
                p.text(f'\n[{section}]\n')
            for subparam, value, param in sections[section]:
                readonly = 'readonly' in rsp.__dict__ and isinstance(rsp.__dict__["readonly"], list) and param in rsp.__dict__["readonly"]
                p.text(f'  {subparam} {"=>" if readonly else "="} {value}\n')

    ## Magic methods to support syntactic sugar

    def __lshift__(self, msg) -> Optional[Message]:
        """ Supports sending messages through the << operator.
            Example: agent << msg will send the message msg to the agent represented by agent."""

        return self.request(msg)

    def __getitem__(self, index) -> Self:
        """ Supports indexed parameter access through the [] operator.
            Example: agent[1].param will refers to the first indexed parameter "param" of the agent."""

        # make a copy of this AgentID with the specified index
        new_aid = AgentID(self.name, topic=self.topic, owner=self.owner)
        new_aid.index = index
        new_aid._timeout = self._timeout
        return new_aid

# Magic methods to support dynamic parameter access using dot notation

def __getter(self, param: str) -> None | Any:
    if param in ['name', 'owner', 'topic', 'index', '_timeout'] or param.startswith('_ipython'):
        return self.__dict__[param]

    from .Message import ParameterReq

    rsp = self.request(ParameterReq(index=self.index).get(param))
    if rsp is None or 'param' not in rsp.__dict__ or 'value' not in rsp.__dict__:
        return None
    return rsp.__dict__.get('value', None)


setattr(AgentID, '__getattr__', __getter)


def __setter(self, param : str, value : Any) -> Any | None:
    if param in ['name', 'owner', 'topic', 'index', '_timeout']:
        self.__dict__[param] = value
        return value

    from .Message import ParameterReq
    rsp = self.request(ParameterReq(index=self.index).set(param, value))
    if rsp is None or 'param' not in rsp.__dict__ or 'value' not in rsp.__dict__:
        return None
    return rsp.__dict__.get('value', None)

setattr(AgentID, '__setattr__', __setter)