from __future__ import annotations

import logging
import keyword
import warnings
from typing import Callable, Optional, Any, Dict, TYPE_CHECKING, get_type_hints

from .AgentID import AgentID
from .Performative import Performative
from .Utils import UUID7

if TYPE_CHECKING:
    from .Gateway import Gateway

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())

_MESSAGE_REGISTRY: Dict[str, type["Message"]] = {}
_NUMPY_AVAILABLE:bool = False

# Attempt to import numpy for array handling
# If numpy is not available, we don't need to
# handle numpy arrays in Message
try:
    import numpy # type: ignore[import-not-found]
    _NUMPY_AVAILABLE = True

    def _serialize_numpy_array(value: numpy.ndarray, key: str, props: Dict):
        """Convert a numpy array to a JSON-serializable dict, mark complex arrays."""
        if numpy.iscomplexobj(value):
            props[f"{key}__isComplex"] = True
            return numpy.vstack((value.real, value.imag)).reshape((-1,), order='F').tolist()
        return value.tolist()

except ImportError:
    _NUMPY_AVAILABLE = False

def _normalize_field_name(name: str) -> str:
    """
    Normalize a field name by stripping trailing underscores from Python keywords.
    """
    if not name.startswith('_') and name.endswith('_') and keyword.iskeyword(name[:-1]):
        return name[:-1]
    return name


def register_message(class_name: str, message_class: type["Message"]) -> type["Message"]:
    """Register a Message subclass under its wire class name.

    The class is registered under both the given name and its unqualified
    short name, and serializes with the given name as its ``clazz``.
    """
    if not isinstance(class_name, str) or not class_name:
        raise TypeError('class_name must be a non-empty string')
    if not isinstance(message_class, type) or not issubclass(message_class, Message):
        raise TypeError('message_class must be a Message subclass')
    for name in {class_name, class_name.split('.')[-1]}:
        existing = _MESSAGE_REGISTRY.get(name)
        if existing is not None and existing is not message_class:
            logger.warning("Overriding message class registered as '%s': %s -> %s", name, existing, message_class)
        _MESSAGE_REGISTRY[name] = message_class
    message_class.__clazz__ = class_name
    return message_class

def _message_clazz(class_: type["Message"]) -> str:
    clazz_name = class_.__dict__.get('__clazz__')
    if isinstance(clazz_name, str) and clazz_name:
        return clazz_name
    if class_ is Message:
        return "org.arl.fjage.Message"
    return class_.__name__

def _instantiate_message(class_: type["Message"]) -> "Message":
    try:
        return class_()
    except TypeError:
        instance = class_.__new__(class_)
        if isinstance(instance, Message):
            Message.__init__(instance)
        return instance

class Message:
    """Base class for messages transmitted by one agent to another."""
    msgID: str
    perf: Performative
    sender: Optional[AgentID]
    recipient: Optional[AgentID]
    inReplyTo: Optional[str]
    sentAt: Optional[int]
    __clazz__: str

    def __init__(self, in_reply_to_msg: Optional["Message"] = None,
                 perf: Performative = Performative.INFORM, **kwargs):
        self.__clazz__ = _message_clazz(type(self))
        self.msgID = str(UUID7.generate())
        self.perf = perf
        self.sender: Optional[AgentID] = None
        self.recipient: Optional[AgentID] = in_reply_to_msg.sender if in_reply_to_msg else None
        self.inReplyTo: Optional[str] = in_reply_to_msg.msgID if in_reply_to_msg else None
        self.sentAt: Optional[int] = None

        if self.__clazz__.endswith('Req') and self.perf == Performative.INFORM:
            self.perf = Performative.REQUEST

        # Set extra kwargs
        for k, v in kwargs.items():
            setattr(self, k, v)

    def __getattr__(self, name):
        # Deal with attributes that are keywords in Python
        # by allowing them to be accessed with  a trailing underscore
        # e.g., msg.from --> msg.from_
        normalized_name = _normalize_field_name(name)
        if normalized_name != name:
            return getattr(self, normalized_name)
        raise AttributeError(f"'{type(self).__name__}' object has no attribute '{name}'")

    def __setattr__(self, name, value):
        object.__setattr__(self, _normalize_field_name(name), value)

    def to_json(self) -> Dict[str, Any]:
        """Convert the message to a JSON-serializable dict.

        :meta private:
        """
        props = {}
        for key, value in self.__dict__.items():
            if key.startswith("_"):
                continue
            wire_key = _normalize_field_name(key)
            if hasattr(value, "to_json") and callable(getattr(value, "to_json")):
                props[wire_key] = value.to_json()
            elif _NUMPY_AVAILABLE is True and isinstance(value, numpy.ndarray):
                props[wire_key] = _serialize_numpy_array(value, wire_key, props)
            else:
                props[wire_key] = value
        return {"clazz": self.__clazz__, "data": props}

    @classmethod
    def from_json(cls, json_obj: Dict[str, Any], owner: Optional["Gateway"] = None) -> Optional["Message"]:
        """Inflate a Message or subclass from a JSON object.

        Registered classes are inflated as their Python type. Unknown classes
        are inflated as Message instances and retain their wire class name and
        fields. Malformed objects return None.

        :meta private:
        """
        if "clazz" not in json_obj or "data" not in json_obj:
            logger.debug(f"Invalid JSON object for Message deserialization: {json_obj}, {type(json_obj)}")
            return None

        qclazz = json_obj["clazz"]

        registered_cls = _MESSAGE_REGISTRY.get(qclazz)
        rv_cls = registered_cls or Message

        rv = _instantiate_message(rv_cls)
        if registered_cls is None:
            rv.__clazz__ = qclazz

        for key, value in json_obj["data"].items():
            normalized_key = _normalize_field_name(key)
            if normalized_key in ("sender", "recipient") and isinstance(value, str):
                setattr(rv, normalized_key, AgentID.from_json(value, owner=owner))
            elif get_type_hints(rv_cls).get(normalized_key) == Optional[AgentID] and isinstance(value, str):
                setattr(rv, normalized_key, AgentID.from_json(value, owner=owner))
            elif normalized_key == "perf" and isinstance(value, str):
                setattr(rv, normalized_key, Performative(value))
            elif isinstance(value, list) and f"{key}__isComplex" in json_obj["data"]:
                is_complex = json_obj["data"].get(f"{key}__isComplex", False)
                if is_complex and len(value) % 2 == 0:
                    setattr(rv, normalized_key, [complex(value[i], value[i + 1]) for i in range(0, len(value), 2)])
                else:
                    setattr(rv, normalized_key, value)
            elif key.endswith('__isComplex'):
                continue
            else:
                setattr(rv, normalized_key, value)
        return rv

    def __str__(self) -> str:
        p = self.perf.value if self.perf else 'MESSAGE'
        if self.__clazz__ == 'org.arl.fjage.Message':
            return p
        content = []
        for k, v in self.__dict__.items():
            if k.startswith('__') or k == 'sender' or k == 'recipient' or k == 'msgID' or k == 'perf' or k == 'inReplyTo':
                # Skip internal and common fields
                pass
            elif v is None or (isinstance(v, (list, dict)) and len(v) == 0):
                # Skip None or empty fields
                pass
            elif k == 'signal':
                if type(v) is list or (hasattr(v, '__len__') and type(v).__name__ == 'ndarray'): # numpy array
                    content.append(f"{k}=({len(v)} samples)")
            elif k == 'data':
                if type(v) is list or (hasattr(v, '__len__') and type(v).__name__ == 'ndarray'): # numpy array
                    content.append(f"{k}=({len(v)} bytes)")
            else:
                content.append(f"{k}={v}")
        if not content:
            return f"{self.__clazz__.split('.')[-1]}:{p}"
        else:
            return f"{self.__clazz__.split('.')[-1]}:{p}[" + (', '.join(content)) + ']'

    def _repr_pretty_(self, p, cycle):
        p.text(str(self) if not cycle else '...')


class GenericMessage(Message):
    def __init__(self):
        super().__init__()
        self.__clazz__ = "org.arl.fjage.GenericMessage"


def MessageClass(name: str, parent: type[Message] = Message) -> type[Message]:
    """Creates an unqualified message class based on a fully qualified name.

    Deprecated. Use :py:meth:`Gateway.register_message` or :py:func:`message` instead.

    Args:
        name : fully qualified name of the message class
        parent : parent class to inherit from. Defaults to :py:class:`Message`.

    Returns:
        A new subclass of Message with the given name.
    """

    warnings.warn(
        "MessageClass is deprecated and will be removed in a future version. "
        "Use Gateway.register_message or the @message decorator instead.",
        DeprecationWarning,
        stacklevel=2
    )

    sname = name.split('.')[-1]

    def __init__(self, **kwargs):
        parent.__init__(self)
        self.__clazz__ = name
        if name.endswith('Req'):
            self.perf = Performative.REQUEST
        _update_attributes(self, kwargs)

    class_ = type(sname, (parent,), {"__init__": __init__})
    return register_message(name, class_)

def message(class_name: str) -> Callable[[type[Message]], type[Message]]:
    """Decorator to register a Message subclass for JSON inflation.

    The class must be registered under a fully qualified wire name, for example
    ``@message('org.example.MyMessage')``.
    """
    if not is_qualified_class_name(class_name):
        raise TypeError('class_name must be a fully qualified class name')

    def decorator(class_: type["Message"]) -> type["Message"]:
        if not isinstance(class_, type) or not issubclass(class_, Message):
            raise TypeError('@message can only be used with Message subclasses')
        return register_message(class_name, class_)

    return decorator

def _update_attributes(obj: Any, kwargs: Dict[str, Any]) -> None:
    for key, value in kwargs.items():
        setattr(obj, _normalize_field_name(key), value)

def _short(p):
    if p is None:
        return None
    return p.split('.')[-1]

def _value(v):
    if isinstance(v, dict):
        if 'clazz' in v:
            if v['clazz'] == 'java.util.Date':
                return v['data']
            if v['clazz'] == 'java.util.ArrayList':
                return v['data']
            if v['clazz'] == 'org.arl.fjage.AgentID':
                return AgentID(v['data'])
            p = _GenericObject()
            p.__dict__.update(v)
            return p
        if 'data' in v:
            return v['data']
    return v

def is_qualified_class_name(name: str) -> bool:
    """Check if a class name is fully qualified (contains at least one dot)."""
    return isinstance(name, str) and '.' in name and not any(not part for part in name.split('.'))

class _GenericObject:
    def __init__(self, **kwargs):
        _update_attributes(self, kwargs)

    def __str__(self):
        return self.__dict__['clazz'] + '(...)'

    def _repr_pretty_(self, p, cycle):
        p.text(str(self) if not cycle else '...')

@message('org.arl.fjage.param.ParameterReq')
class ParameterReq(Message):
    index: int
    requests: list[dict]
    perf: Performative
    param: Optional[str]
    value: Optional[Any]

    def __init__(self, index=-1, **kwargs):
        super().__init__()
        self.index = index
        self.requests = []
        self.perf = Performative.REQUEST
        self.param = None
        self.value = None
        _update_attributes(self, kwargs)

    def get(self, param: str):
        """ Request a parameter by name.

        Args:
            param : name of the parameter to request
        """

        if (self.param is None):
            self.param = param
        else:
            self.requests.append({'param': param})
        return self

    def set(self, param: str, value):
        """ Set a parameter value.

        Args:
            param : name of the parameter to set
            value : value to set the parameter to
        """
        if (self.param is None) and (self.value is None):
            self.param = param
            self.value = value
        else:
            self.requests.append({'param': param, 'value': value})
        return self

    def __str__(self):
        p = ''
        if 'param' in self.__dict__ and self.param is not None:
            p += _short(str(self.param)) + ':' + (str(_value(self.value)) if self.value is not None else '?') + ' '
        if 'requests' in self.__dict__ and self.requests is not None:
            if len(self.requests) > 0:
                p += ', '.join([_short(str(v['param'])) + ':' + (str(_value(v['value'])) if 'value' in v and v['value'] is not None else '?') for v in self.requests])
        return self.__class__.__name__ + ':' + self.perf.value + '[' + (('index:' + str(self.index) + ' ') if self.index >= 0 else '') + p.strip() + ']'

    def _repr_pretty_(self, p, cycle):
        p.text(str(self) if not cycle else '...')

@message("org.arl.fjage.param.ParameterRsp")
class ParameterRsp(Message):
    index: int
    values: Optional[dict]
    perf: Performative
    param: Optional[str]
    value: Optional[Any]

    def __init__(self, **kwargs):
        super().__init__()
        self.index = -1
        self.values = dict()
        self.perf = Performative.INFORM
        _update_attributes(self, kwargs)

    def get(self, param:str):# -> Any | AgentID | _GenericObject | dict | Any | None:
        """Get the value of a parameter by name from the response.

        Args:
            param : name of the parameter to get

        Returns:
            value of the parameter, or None if not found

        """
        if 'param' in self.__dict__ and self.param == param:
            return _value(self.value)
        if 'values' in self.__dict__ and self.values is not None:
            if param in self.values:
                return _value(self.values[param])
        if 'param' in self.__dict__ and _short(self.param) == param:
            return _value(self.value)
        if 'values' not in self.__dict__:
            return None
        if self.values is not None:
            for v in self.values:
                if _short(v) == param:
                    return _value(self.values[v])
        return None

    def parameters(self) -> dict[str, Any]:
        """Get all parameters in the response as a dictionary."""
        if 'values' in self.__dict__ and self.values is not None:
            p = self.values.copy()
        else:
            p = {}
        if 'param' in self.__dict__:
            p[self.param] = self.value
        for k in p:
            if isinstance(p[k], dict):
                p[k] = _value(p[k])
        return p

    def __str__(self):
        p = ''
        if 'param' in self.__dict__ and self.param is not None and 'value' in self.__dict__:
            p += _short(str(self.param)) + ':' + (str(_value(self.value))) + ' '
        if 'values' in self.__dict__ and self.values is not None:
            if len(self.values) > 0:
                p += ', '.join([_short(str(k)) + ':' + str(_value(v)) for k, v in self.values.items()])
        return self.__class__.__name__ + ':' + self.perf.value + '[' + (('index:' + str(self.index) + ' ') if self.index >= 0 else '') + p.strip() + ']'

    def _repr_pretty_(self, p, cycle):
        p.text(str(self) if not cycle else '...')

@message('org.arl.fjage.shell.PutFileReq')
class PutFileReq(Message):
    filename: Optional[str]
    contents: Optional[list[int]]
    offset: int

    def __init__(self, filename: Optional[str] = None, contents: Optional[list[int]] = None,
                 offset: int = 0, **kwargs):
        super().__init__(perf=Performative.REQUEST)
        self.filename: Optional[str] = filename
        self.contents: Optional[list[int]] = contents
        self.offset: int = offset
        _update_attributes(self, kwargs)


@message('org.arl.fjage.shell.DeleteFileReq')
class DeleteFileReq(Message):
    filename: Optional[str]

    def __init__(self, filename: Optional[str] = None, **kwargs):
        super().__init__(perf=Performative.REQUEST)
        self.filename: Optional[str] = filename
        _update_attributes(self, kwargs)


@message('org.arl.fjage.shell.GetFileReq')
class GetFileReq(Message):
    filename: Optional[str]
    offset: int
    length: int

    def __init__(self, filename: Optional[str] = None, offset: int = 0,
                 length: int = 0, **kwargs):
        super().__init__(perf=Performative.REQUEST)
        self.filename: Optional[str] = filename
        self.offset: int = offset
        self.length: int = length
        _update_attributes(self, kwargs)


@message('org.arl.fjage.shell.ShellExecReq')
class ShellExecReq(Message):
    command: Optional[str]
    script: Optional[str]
    scriptArgs: Optional[list[str]]
    ans: bool

    def __init__(self, command: Optional[str] = None, script: Optional[str] = None,
                 scriptArgs: Optional[list[str]] = None, ans: bool = False, **kwargs):
        super().__init__(perf=Performative.REQUEST)
        self._command: Optional[str] = None
        self._script: Optional[str] = None
        self.command = command
        self.script = script
        self.scriptArgs: Optional[list[str]] = scriptArgs
        self.ans: bool = ans
        _update_attributes(self, kwargs)


@message('org.arl.fjage.shell.GetFileRsp')
class GetFileRsp(Message):

    def __init__(self, filename: Optional[str] = None, offset: int = 0,
                 contents: Optional[list[int]] = None, directory: bool = False, **kwargs):
        super().__init__(perf=Performative.INFORM)
        self.filename: Optional[str] = filename
        self.offset: int = offset
        self.contents: Optional[list[int]] = contents
        self.directory: bool = directory
        _update_attributes(self, kwargs)
