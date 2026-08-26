import json

import pytest

def test_jsonmessage_serialize_deserialize():
    """JSONMessage should serialize and deserialize back identically."""
    from fjagepy import JSONMessage, Message, AgentID, message

    ## Using the Javascript example above as a guide
    @message('org.arl.unet.phy.TxFrameReq')
    class TxFrameReq(Message):
        pass
    tx_msg = TxFrameReq()
    json_msg = JSONMessage()
    json_msg.action = 'send'
    json_msg.relay = False
    json_msg.message = tx_msg
    raw = json_msg.to_json()
    parsed_json_msg = JSONMessage(raw)
    assert parsed_json_msg.action == 'send'
    assert isinstance(parsed_json_msg.message, TxFrameReq)
    assert parsed_json_msg.message.msgID == tx_msg.msgID
    assert parsed_json_msg.message.perf == tx_msg.perf

def test_jsonmessage_base64_numeric_arrays():
    """JSONMessage should be able to deserialize base64 encoded numeric arrays."""
    from fjagepy import JSONMessage

    DATA_ARRAY = [72, 101, 108, 108, 111, 44, 32, 87, 111, 114, 108, 100, 33]
    str_data = '{"action":"send","relay":false,"message":{"clazz":"org.arl.fjage.test.TestMessage","data":{"msgID":"12345678901234567890123456789012","sender":"test","recipient":"echo","perf":"REQUEST","data":{"clazz":"[B","data":"SGVsbG8sIFdvcmxkIQ=="}}}}'
    json_msg = JSONMessage(str_data)
    assert json_msg.message.data == DATA_ARRAY


def test_jsonmessage_retains_unregistered_embedded_message():
    """An embedded message should survive even when its class is unknown."""
    from fjagepy import JSONMessage, Message

    raw_message = {
        "clazz": "org.example.UnknownMessage",
        "data": {"value": {"nested": True}},
    }
    json_msg = JSONMessage(json.dumps({"action": "send", "relay": False, "message": raw_message}))

    assert type(json_msg.message) is Message
    assert json_msg.message.__clazz__ == raw_message["clazz"]
    assert json_msg.message.value == {"nested": True}

    encoded = json.loads(json_msg.to_json())
    assert encoded["message"]["clazz"] == raw_message["clazz"]
    assert encoded["message"]["data"]["value"] == {"nested": True}
