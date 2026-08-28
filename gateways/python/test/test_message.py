import inspect
import sys

import pytest

from fjagepy import Gateway, Message, AgentID, Performative, MessageClass, message


@message
class DecoratedMessage(Message):
    pass


@message('org.arl.fjage.test.DecoratedReq')
class DecoratedReq(Message):
    def __init__(self, value=None):
        super().__init__()
        self.value = value


@message('org.arl.fjage.test.RequiredArgsMessage')
class RequiredArgsMessage(Message):
    def __init__(self, token):
        super().__init__()
        self.token = token

def test_message_construction():
    """Message should be constructable."""
    msg = Message()
    assert isinstance(msg, Message)

def test_message_unique_id():
    """Message should have a unique ID."""
    msg1 = Message()
    msg2 = Message()
    assert msg1.msgID != msg2.msgID

def test_message_serialization():
    """Message should serialize and deserialize back identically."""
    msg = Message()
    msg.id = "123"
    msg.clazz = "org.arl.fjage.Message"
    msg.perf = Performative.INFORM
    msg.sender = AgentID("agent1")
    msg.recipient = AgentID("agent2")

    js = msg.to_json()
    msg2 = Message.from_json(js)

    assert msg2.id == msg.id
    assert msg2.clazz == msg.clazz
    assert msg2.perf == msg.perf
    assert msg2.sender == msg.sender
    assert msg2.recipient == msg.recipient


def test_base_message_serializes_with_qualified_name():
    assert Message().to_json()['clazz'] == 'org.arl.fjage.Message'


def test_unregistered_message_subclass_serializes_with_class_name():
    class UnregisteredMessage(Message):
        pass

    assert UnregisteredMessage().to_json()['clazz'] == 'UnregisteredMessage'


def test_gateway_send_rejects_non_message():
    with pytest.raises(ValueError, match='msg must be a Message'):
        Gateway.send(object.__new__(Gateway), object())


def test_gateway_register_message_serializes_and_inflates_registered_class():
    """Gateway.registerMessage should use the registered name in both directions."""
    class RegisteredReq(Message):
        pass

    registered = Gateway.registerMessage('org.arl.fjage.test.RegisteredReq', RegisteredReq)
    outgoing = registered(value=7)
    incoming = Message.from_json(outgoing.to_json())

    assert registered is RegisteredReq
    assert outgoing.__clazz__ == 'org.arl.fjage.test.RegisteredReq'
    assert outgoing.perf == Performative.REQUEST
    assert isinstance(incoming, RegisteredReq)
    assert incoming.value == 7


def test_register_message_keeps_qualified_names_and_updates_unqualified_name():
    """Qualified names should stay distinct while the latest short name wins."""
    FirstFoo = type('TestFoo', (Message,), {})
    SecondFoo = type('TestFoo', (Message,), {})

    Gateway.registerMessage('a.TestFoo', FirstFoo)
    Gateway.registerMessage('b.TestFoo', SecondFoo)

    assert isinstance(Message.from_json({'clazz': 'a.TestFoo', 'data': {}}), FirstFoo)
    assert isinstance(Message.from_json({'clazz': 'b.TestFoo', 'data': {}}), SecondFoo)
    assert isinstance(Message.from_json({'clazz': 'TestFoo', 'data': {}}), SecondFoo)
    assert Message.from_json({'clazz': 'TestFoo', 'data': {}}).to_json()['clazz'] == 'b.TestFoo'
    unknown = Message.from_json({'clazz': 'c.TestFoo', 'data': {}})
    assert type(unknown) is Message
    assert unknown.__clazz__ == 'c.TestFoo'


def test_gateway_register_message_rejects_invalid_arguments():
    """Gateway.registerMessage should require a name and a Message subclass."""
    with pytest.raises(TypeError, match='class_name'):
        Gateway.registerMessage('', Message)
    with pytest.raises(TypeError, match='fully qualified'):
        Gateway.registerMessage('Invalid', Message)
    with pytest.raises(TypeError, match='fully qualified'):
        Gateway.registerMessage('org..Invalid', Message)
    with pytest.raises(TypeError, match='message_class'):
        Gateway.registerMessage('org.arl.fjage.test.Invalid', object)


def test_register_message_is_not_exported_from_fjagepy():
    import fjagepy

    assert not hasattr(fjagepy, 'registerMessage')


def test_messageclass_is_deprecated():
    """MessageClass should remain available during the deprecation period."""
    with pytest.warns(DeprecationWarning, match='Use Gateway.registerMessage or the @message decorator'):
        DeprecatedMessage = MessageClass('org.arl.fjage.test.DeprecatedMessage')

    assert isinstance(DeprecatedMessage(), DeprecatedMessage)


def test_message_encode_numpy_array():
    """Message should encode numpy arrays correctly."""
    np = pytest.importorskip("numpy")
    arr = np.array([1, 2, 3])
    msg = Message()
    msg.data = arr
    js = msg.to_json()
    assert 'data__isComplex' not in js['data']
    assert (js['data']['data'] == [1, 2 ,3])

def test_message_encode_complex_array():
    """Message should encode complex arrays correctly."""
    np = pytest.importorskip("numpy")
    arr = np.array([1+2j, 3+4j, 5+6j])
    msg = Message()
    msg.data = arr
    js = msg.to_json()
    assert 'data__isComplex' in js['data']
    assert js['data']['data__isComplex'] is True
    assert (js['data']['data'] == [1, 2 ,3 ,4 ,5 ,6])

def test_message_decode_complex_array():
    """Message should decode complex arrays correctly."""
    js = {"clazz": "org.arl.fjage.Message", "data": { "signal" : [1,2,3,4,5,6], "signal__isComplex": True }}
    msg = Message.from_json(js)
    assert isinstance(msg.signal, list)
    assert msg.signal == [1+2j, 3+4j, 5+6j]


def test_message_runtime_assignment_normalizes_keyword_fields():
    """Message should store Python-safe keyword names using wire names."""
    msg = Message()
    msg.from_ = "alpha"

    assert msg.from_ == "alpha"
    assert getattr(msg, 'from') == "alpha"
    assert 'from' in msg.__dict__
    assert 'from_' not in msg.__dict__


def test_message_to_json_serializes_python_safe_keyword_fields():
    """Message should serialize Python-safe keyword names to canonical wire names."""
    msg = Message(from_="alpha")

    js = msg.to_json()

    assert js['data']['from'] == 'alpha'
    assert 'from_' not in js['data']


def test_message_from_json_normalizes_canonical_keyword_fields():
    """Message should expose canonical wire keyword names via Python-safe accessors."""
    js = {"clazz": "org.arl.fjage.Message", "data": {"from": "alpha"}}

    msg = Message.from_json(js)

    assert msg is not None
    assert msg.from_ == 'alpha'
    assert getattr(msg, 'from') == 'alpha'
    assert 'from' in msg.__dict__
    assert 'from_' not in msg.__dict__


def test_message_keyword_field_round_trip_uses_wire_name():
    """Message keyword fields should round-trip through JSON using canonical wire names."""
    outgoing = Message(from_="alpha")

    incoming = Message.from_json(outgoing.to_json())

    assert incoming is not None
    assert incoming.from_ == 'alpha'
    assert getattr(incoming, 'from') == 'alpha'


def test_message_decorator_registers_external_class():
    """@message should register decorated classes for inflation."""
    assert DecoratedMessage.__clazz__ == 'DecoratedMessage'
    assert DecoratedMessage().to_json()['clazz'] == 'DecoratedMessage'
    js = {"clazz": "DecoratedMessage", "data": {"value": 7}}
    msg = Message.from_json(js)
    assert isinstance(msg, DecoratedMessage)
    assert msg.value == 7


def test_register_message_does_not_register_python_class_name_as_alias():
    class LocalMessage(Message):
        pass

    Gateway.registerMessage('org.arl.fjage.WireMessage', LocalMessage)

    msg = Message.from_json({'clazz': 'LocalMessage', 'data': {'value': 12}})
    assert type(msg) is Message
    assert msg.__clazz__ == 'LocalMessage'
    assert msg.value == 12


def test_message_decorator_sets_registered_clazz_and_perf():
    """@message should apply the registered clazz to new instances."""
    msg = DecoratedReq(value=9)
    assert msg.__clazz__ == 'org.arl.fjage.test.DecoratedReq'
    assert msg.perf == Performative.REQUEST


def test_message_decorator_preserves_constructor_signature():
    """@message should not replace the decorated constructor signature."""
    assert str(inspect.signature(DecoratedReq)) == '(value=None)'
    assert str(inspect.signature(RequiredArgsMessage)) == '(token)'


def test_message_decorator_inflates_without_noarg_constructor():
    """@message should inflate registered classes even if __init__ needs args."""
    js = {"clazz": "org.arl.fjage.test.RequiredArgsMessage", "data": {"token": "abc"}}
    msg = Message.from_json(js)
    assert isinstance(msg, RequiredArgsMessage)
    assert msg.token == 'abc'


def test_message_from_json_inflates_unknown_qualified_class(caplog):
    """Unknown qualified classes should inflate as Message without a warning."""
    js = {
        "clazz": "org.example.UnknownMessage",
        "data": {
            "msgID": "12345678901234567890123456789012",
            "perf": "INFORM",
            "sender": "sender",
            "recipient": "#topic",
            "inReplyTo": "reply-id",
            "sentAt": 1,
            "value": {"nested": True},
        },
    }

    caplog.set_level("WARNING", logger="fjagepy.Message")
    msg = Message.from_json(js)

    assert type(msg) is Message
    assert msg.__clazz__ == js["clazz"]
    assert msg.msgID == js["data"]["msgID"]
    assert msg.perf == Performative.INFORM
    assert msg.sender == AgentID("sender")
    assert msg.recipient == AgentID("topic", topic=True)
    assert msg.inReplyTo == "reply-id"
    assert msg.sentAt == 1
    assert msg.value == {"nested": True}
    assert msg.to_json()["clazz"] == js["clazz"]
    assert not caplog.records


def test_message_from_json_inflates_unknown_unqualified_class():
    """Unknown unqualified classes should also inflate as Message."""
    msg = Message.from_json({"clazz": "UnknownMessage", "data": {"value": 1}})

    assert type(msg) is Message
    assert msg.__clazz__ == "UnknownMessage"
    assert msg.value == 1


def test_message_from_json_uses_unregistered_module_class(monkeypatch):
    """An available unregistered Message subclass should be used when found."""
    class ReflectedMessage(Message):
        pass

    message_module = sys.modules[Message.__module__]
    monkeypatch.setattr(message_module, "ReflectedMessage", ReflectedMessage, raising=False)

    msg = Message.from_json({"clazz": "org.example.ReflectedMessage", "data": {"value": 1}})

    assert isinstance(msg, ReflectedMessage)
    assert msg.__clazz__ == "org.example.ReflectedMessage"
    assert msg.value == 1


def test_message_from_json_ignores_non_message_module_class(monkeypatch):
    """A reflected object that is not a Message subclass should be ignored."""
    message_module = sys.modules[Message.__module__]
    monkeypatch.setattr(message_module, "NotAMessage", object, raising=False)

    msg = Message.from_json({"clazz": "org.example.NotAMessage", "data": {"value": 1}})

    assert type(msg) is Message
    assert msg.__clazz__ == "org.example.NotAMessage"
    assert msg.value == 1


@pytest.mark.parametrize("json_obj", [
    {},
    {"clazz": "org.example.UnknownMessage"},
    {"data": {"value": 1}},
])
def test_message_from_json_rejects_malformed_objects(json_obj):
    """Objects without both clazz and data should remain invalid."""
    assert Message.from_json(json_obj) is None
