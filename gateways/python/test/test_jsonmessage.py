import pytest

def test_jsonmessage_serialize_deserialize():
    """JSONMessage should serialize and deserialize back identically."""
    from fjagepy import JSONMessage, Message, AgentID, MessageClass

    ## Using the Javascript example above as a guide
    TxFrameReq = MessageClass('org.arl.unet.phy.TxFrameReq')
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