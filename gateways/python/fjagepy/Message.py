import sys
import logging
from typing import Optional, Any, Dict, Type

from .AgentID import AgentID
from .Performative import Performative
from .Utils import UUID7

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())

# Attempt to import numpy for array handling
# If numpy is not available, we don't need to
# handle numpy arrays in Message
try:
    import numpy

    def _serialize_numpy_array(value: numpy.ndarray, key:str, props: Dict) -> list:
        """Convert a numpy array to a JSON-serializable dict, mark complex arrays."""
        if numpy.iscomplexobj(value):
            props[f"{key}__isComplex"] = True
            return numpy.vstack((value.real, value.imag)).reshape((-1,), order='F').tolist()
        return value.tolist()

except ImportError:
    def _serialize_numpy_array(value: Any, key: str, props: Dict) -> Any:
        return value

class Message:
    """Base class for messages transmitted by one agent to another."""

    def __init__(self, in_reply_to_msg: Optional["Message"] = None,
                 perf: str = Performative.INFORM, **kwargs):
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
        if not name.startswith('_') and name.endswith('_'):
            return object.__getattribute__(self, name[:-1])
        return object.__getattribute__(self, name)

    def to_json(self) -> Dict[str, Any]:
        """Convert the message to a JSON-serializable dict.

        :meta private:
        """
        props = {}
        for key, value in self.__dict__.items():
            if key.startswith("_"):
                continue
            if isinstance(value, AgentID):
                props[key] = value.to_json()
            elif numpy is not None and isinstance(value, numpy.ndarray):
                props[key] = _serialize_numpy_array(value, key, props)
            else:
                props[key] = value
        return {"clazz": self.__clazz__, "data": props}

    @classmethod
    def from_json(cls, json_obj: Dict[str, Any]) -> Optional["Message"]:
        """ Inflate a Message (or subclass) from a JSON object.

        :meta private:
        """
        if "clazz" not in json_obj or "data" not in json_obj:
            logger.debug(f"Invalid JSON object for Message deserialization: {json_obj}, {type(json_obj)}")
            return None

        qclazz = json_obj["clazz"]
        clazz_name = qclazz.split(".")[-1]

        # Try to find the class in globals
        module = sys.modules[__name__]
        rv_cls = getattr(module, clazz_name, None)

        # Else default to base Message class
        if rv_cls is None:
            rv_cls = Message

        rv = rv_cls()
        rv.__clazz__ = qclazz

        for key, value in json_obj["data"].items():
            if key in ("sender", "recipient") and isinstance(value, str):
                setattr(rv, key, AgentID.from_json(value))
            elif key == "perf" and isinstance(value, str):
                setattr(rv, key, Performative(value))
            elif isinstance(value, list) and any(k == f"{key}__isComplex" for k in json_obj["data"].keys()):
                is_complex = json_obj["data"].get(f"{key}__isComplex", False)
                if is_complex:
                    setattr(rv, key, [complex(value[i], value[i + 1]) for i in range(0, len(value), 2)])
                else:
                    setattr(rv, key, value)
            elif key.endswith('__isComplex'):
                continue
            else:
                setattr(rv, key, value)
        return rv

    def __str__(self):
        p = self.perf.value if self.perf else 'MESSAGE'
        if self.__clazz__ == 'org.arl.fjage.Message':
            return p
        return f"{p}: {self.__clazz__.split('.')[-1]}"

    def _repr_pretty_(self, p, cycle):
        if cycle:
            p.text('...')
            return
        content = []
        for k, v in self.__dict__.items():
            if k.startswith('__') or k == 'sender' or k == 'recipient' or k == 'msgID' or k == 'perf' or k == 'inReplyTo':
                # Skip internal and common fields
                pass
            elif v is None or (isinstance(v, (list, dict)) and len(v) == 0):
                # Skip None or empty fields
                pass
            elif type(v) in (numpy.ndarray, list) and k == 'signal':
                content.append(f"{k}=({len(v)} samples)")
            elif type(v) in (numpy.ndarray, list) and k == 'data':
                content.append(f"{k}=({len(v)} bytes)")
            else:
                content.append(f"{k}={v}")
        if self.__clazz__ == 'org.arl.fjage.Message' and not content:
            p.text(str(self.perf))
        else:
            p.text(self.__clazz__.split(".")[-1] + ':' + str(self.perf) +  '[' + (', '.join(content)) + ']')



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

    def setclazz(self, **kwargs):
        super(class_, self).__init__()
        self.__clazz__ = name
        if name.endswith('Req'):
            self.perf = Performative.REQUEST
        for k, v in kwargs.items():
            if not k.startswith('_') and k.endswith('_'):
                k = k[:-1]
            self.__dict__[k] = v

    sname = name.split('.')[-1]
    class_ = type(sname, (parent,), {"__init__": setclazz})
    # Register the class in the current module's globals
    sys.modules[__name__].__dict__[sname] = class_
    return class_


# Predefined message classes
_ParameterReq = MessageClass("org.arl.fjage.param.ParameterReq")
_ParameterRsp = MessageClass("org.arl.fjage.param.ParameterRsp")

PutFileReq = MessageClass('org.arl.fjage.shell.PutFileReq')
GetFileReq = MessageClass('org.arl.fjage.shell.GetFileReq')
ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq')
GetFileRsp = MessageClass('org.arl.fjage.shell.GetFileRsp')

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

class ParameterReq(_ParameterReq):

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


class ParameterRsp(_ParameterRsp):

    def __init__(self, **kwargs):
        super().__init__()
        self.index = -1
        self.values = dict()
        self.perf = Performative.REQUEST
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