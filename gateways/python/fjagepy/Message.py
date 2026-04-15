import sys
import logging
import keyword
from typing import Callable, Optional, Any, Dict, Type, TYPE_CHECKING

from .AgentID import AgentID
from .Performative import Performative
from .Utils import UUID7

if TYPE_CHECKING:
    from .Gateway import Gateway

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())

_MESSAGE_REGISTRY: Dict[str, Type["Message"]] = {}


def _normalize_field_name(name: str) -> str:
    """
    Normalize a field name by stripping trailing underscores from Python keywords.
    """
    if not name.startswith('_') and name.endswith('_') and keyword.iskeyword(name[:-1]):
        return name[:-1]
    return name

def _register_message_class(class_: Type["Message"], fqcn: Optional[str] = None) -> Type["Message"]:
    sys.modules[__name__].__dict__[class_.__name__] = class_
    _MESSAGE_REGISTRY[class_.__name__] = class_

    clazz_name = fqcn or getattr(class_, '__clazz__', None)
    if isinstance(clazz_name, str) and clazz_name:
        _MESSAGE_REGISTRY[clazz_name] = class_
        _MESSAGE_REGISTRY[clazz_name.split('.')[-1]] = class_

    return class_

def _instantiate_message(class_: Type["Message"]) -> "Message":
    try:
        return class_()
    except TypeError:
        instance = class_.__new__(class_)
        if isinstance(instance, Message):
            Message.__init__(instance)
        return instance


def message(arg: Optional[Any] = None) -> type[Message] | Callable[..., type[Message]]:
    """Decorator to register a Message subclass for JSON inflation.

    Can be used as ``@message`` or ``@message('org.example.MyMessage')``.
    """

    def decorate(class_: Type["Message"], fqcn: Optional[str] = None) -> Type["Message"]:
        if not issubclass(class_, Message):
            raise TypeError('@message can only be used with Message subclasses')

        clazz_name = fqcn or getattr(class_, '__clazz__', None) or class_.__name__
        original_init = class_.__init__
        if not getattr(original_init, '__fjage_message_wrapped__', False):
            def __init__(self, *args, **kwargs):
                original_init(self, *args, **kwargs)
                self.__clazz__ = clazz_name
                if clazz_name.endswith('Req') and getattr(self, 'perf', None) == Performative.INFORM:
                    self.perf = Performative.REQUEST

            __init__.__fjage_message_wrapped__ = True
            class_.__init__ = __init__

        class_.__clazz__ = clazz_name
        return _register_message_class(class_, clazz_name)

    if isinstance(arg, type):
        return decorate(arg)

    def decorator(class_: Type["Message"]) -> Type["Message"]:
        return decorate(class_, arg)

    return decorator

# Attempt to import numpy for array handling
# If numpy is not available, we don't need to
# handle numpy arrays in Message
try:
    import numpy

    def _serialize_numpy_array(value: Any, key: str, props: Dict) -> list:
        """Convert a numpy array to a JSON-serializable dict, mark complex arrays."""
        if numpy.iscomplexobj(value):
            props[f"{key}__isComplex"] = True
            return numpy.vstack((value.real, value.imag)).reshape((-1,), order='F').tolist()
        return value.tolist()

except ImportError:
    numpy = None
    def _serialize_numpy_array(value: Any, key: str, props: Dict) -> Any:
        return value

class Message:
    """Base class for messages transmitted by one agent to another."""

    def __init__(self, in_reply_to_msg: Optional["Message"] = None,
                 perf: Performative = Performative.INFORM, **kwargs):
        self.__clazz__ = "org.arl.fjage.Message"
        self.msgID = str(UUID7.generate())
        self.perf = perf
        self.sender: Optional[AgentID] = None
        self.recipient: Optional[AgentID] = in_reply_to_msg.sender if in_reply_to_msg else None
        self.inReplyTo: Optional[str] = in_reply_to_msg.msgID if in_reply_to_msg else None

        # Set extra kwargs
        for k, v in kwargs.items():
            setattr(self, k, v)

    def __getattribute__(self, name):
        # Deal with attributes that are keywords in Python
        # by allowing them to be accessed with  a trailing underscore
        # e.g., msg.from --> msg.from_
        normalized_name = _normalize_field_name(name)
        if normalized_name != name:
            return object.__getattribute__(self, normalized_name)
        return object.__getattribute__(self, name)

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
            if isinstance(value, AgentID):
                props[wire_key] = value.to_json()
            elif numpy is not None and isinstance(value, numpy.ndarray):
                props[wire_key] = _serialize_numpy_array(value, wire_key, props)
            else:
                props[wire_key] = value
        return {"clazz": self.__clazz__, "data": props}

    @classmethod
    def from_json(cls, json_obj: Dict[str, Any], owner: Optional["Gateway"] = None) -> Optional["Message"]:
        """ Inflate a Message (or subclass) from a JSON object.

        :meta private:
        """
        if "clazz" not in json_obj or "data" not in json_obj:
            logger.debug(f"Invalid JSON object for Message deserialization: {json_obj}, {type(json_obj)}")
            return None

        qclazz = json_obj["clazz"]
        clazz_name = qclazz.split(".")[-1]

        rv_cls = _MESSAGE_REGISTRY.get(qclazz) or _MESSAGE_REGISTRY.get(clazz_name)

        if rv_cls is None:
            module = sys.modules[__name__]
            rv_cls = getattr(module, clazz_name, None)

        # Else default to base Message class
        if rv_cls is None:
            rv_cls = Message

        rv = _instantiate_message(rv_cls)
        rv.__clazz__ = qclazz

        for key, value in json_obj["data"].items():
            normalized_key = _normalize_field_name(key)
            if normalized_key in ("sender", "recipient") and isinstance(value, str):
                setattr(rv, normalized_key, AgentID.from_json(value, owner=owner))
            elif normalized_key == "perf" and isinstance(value, str):
                setattr(rv, normalized_key, Performative(value))
            elif isinstance(value, list) and any(k == f"{key}__isComplex" for k in json_obj["data"].keys()):
                is_complex = json_obj["data"].get(f"{key}__isComplex", False)
                if is_complex:
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


def MessageClass(name: str, parent: Type[Message] = Message) -> Type[Message]:
    """Creates an unqualified message class based on a fully qualified name.

    Args:
        name : fully qualified name of the message class
        parent : parent class to inherit from. Defaults to :py:class:`Message`.

    Returns:
        A new subclass of Message with the given name.
    """

    sname = name.split('.')[-1]

    def __init__(self, **kwargs):
        parent.__init__(self)
        self.__clazz__ = name
        if name.endswith('Req'):
            self.perf = Performative.REQUEST
        _update_attributes(self, kwargs)

    class_ = type(sname, (parent,), {"__init__": __init__})
    return _register_message_class(class_, name)


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

class _GenericObject:
    def __init__(self, **kwargs):
        self.__dict__.update(kwargs)

    def __str__(self):
        return self.__dict__['clazz'] + '(...)'

    def _repr_pretty_(self, p, cycle):
        p.text(str(self) if not cycle else '...')

@message('org.arl.fjage.param.ParameterReq')
class ParameterReq(Message):

    def __init__(self, index=-1, **kwargs):
        super().__init__()
        self.index = index
        self.requests = []
        self.perf = Performative.REQUEST
        self.param = None
        self.value = None
        self.__dict__.update(kwargs)

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

    def __init__(self, **kwargs):
        super().__init__()
        self.index = -1
        self.values = dict()
        self.perf = Performative.INFORM
        self.__dict__.update(kwargs)

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

    def parameters(self) -> dict[str, any]:
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

    def __init__(self, filename: Optional[str] = None, contents: Optional[list[int]] = None,
                 offset: int = 0, **kwargs):
        super().__init__(perf=Performative.REQUEST)
        self.filename: Optional[str] = filename
        self.contents: Optional[list[int]] = contents
        self.offset: int = offset
        _update_attributes(self, kwargs)


@message('org.arl.fjage.shell.GetFileReq')
class GetFileReq(Message):

    def __init__(self, filename: Optional[str] = None, offset: int = 0,
                 length: int = 0, **kwargs):
        super().__init__(perf=Performative.REQUEST)
        self.filename: Optional[str] = filename
        self.offset: int = offset
        self.length: int = length
        _update_attributes(self, kwargs)


@message('org.arl.fjage.shell.ShellExecReq')
class ShellExecReq(Message):

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