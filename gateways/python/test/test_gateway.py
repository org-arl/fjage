import pytest
import socket
from time import sleep

from fjagepy import Gateway, Message, ShellExecReq, AgentID, MessageClass, Performative, JSONMessage
from .conftest import DEFAULT_HOST, DEFAULT_PORT

SendMsgReq = MessageClass("org.arl.fjage.test.SendMsgReq");
SendMsgRsp = MessageClass("org.arl.fjage.test.SendMsgRsp");

@pytest.fixture
def gateway():
    # Setup code before each test
    gw = Gateway(hostname=DEFAULT_HOST, port=DEFAULT_PORT)
    yield gw
    # Teardown code after each test
    gw.close()

def test_gateway_construction(gateway):
    """Gateway should be constructable."""
    assert isinstance(gateway, Gateway)

def test_gateway_connector_opened(gateway):
    """Gateway should have a successfully opened the underlying connector."""
    assert gateway.connector is not None
    assert gateway.connector.is_connected()

def test_gateway_close(gateway):
    """Gateway should close the socket when close is called on it."""
    assert gateway.connector.is_connected()
    gateway.close()
    sleep(0.1)
    assert not gateway.connector.is_connected()

def test_gateway_send_message(monkeypatch, gateway):
    """Gateway should send a message over a socket."""
    shell = gateway.agent('shell')
    sleep(0.1)
    calls = []
    def spy_sendall(self, data):
        calls.append(data)
        return original_sendall(self, data)
    original_sendall = socket.socket.sendall
    monkeypatch.setattr(socket.socket, "sendall", spy_sendall)
    req = ShellExecReq()
    req.command = 'ps'
    req.ans = True
    shell << req
    sleep(0.1)
    assert len(calls) > 0

def test_gateway_send_message_structure(monkeypatch, gateway):
    """Gateway should send a socket message of valid fjage message structure."""
    shell = gateway.agent('shell')
    sleep(0.1)
    calls = []
    def spy_sendall(self, data):
        calls.append(data)
        return original_sendall(self, data)
    original_sendall = socket.socket.sendall
    monkeypatch.setattr(socket.socket, "sendall", spy_sendall)
    req = ShellExecReq()
    req.command = 'ps'
    req.ans = True
    shell << req
    sleep(0.1)
    assert len(calls) > 0
    js = calls[0].decode().strip()
    json_msg = JSONMessage(js, owner=gateway)
    msg = json_msg.message
    assert msg.__clazz__ == 'org.arl.fjage.shell.ShellExecReq'
    assert msg.perf.name == 'REQUEST'
    assert msg.sender.name == gateway.aid.name
    assert msg.recipient.name == 'shell'
    assert msg.command == 'ps'
    assert msg.ans is True

def test_gateway_send_shellexecreq_structure(monkeypatch, gateway):
    """Gateway should send correct ShellExecReq of valid fjage message structure."""
    shell = gateway.agent('shell')
    sleep(0.1)
    calls = []
    def spy_sendall(self, data):
        calls.append(data)
        return original_sendall(self, data)
    original_sendall = socket.socket.sendall
    monkeypatch.setattr(socket.socket, "sendall", spy_sendall)
    req = ShellExecReq()
    req.command = 'ps'
    req.ans = True
    shell << req
    sleep(0.1)
    assert len(calls) > 0
    js = calls[0].decode().strip()
    json_msg = JSONMessage(js, owner=gateway)
    msg = json_msg.message
    assert msg.__clazz__ == 'org.arl.fjage.shell.ShellExecReq'
    assert msg.perf.name == 'REQUEST'
    assert msg.sender.name == gateway.aid.name
    assert msg.recipient.name == 'shell'
    assert msg.command == 'ps'
    assert msg.ans is True

def test_gateway_send_shellexecreq_param_constructor(monkeypatch, gateway):
    """Gateway should send correct ShellExecReq of valid fjage message structure created using param constructor."""
    shell = gateway.agent('shell')
    sleep(0.1)
    calls = []
    def spy_sendall(self, data):
        calls.append(data)
        return original_sendall(self, data)
    original_sendall = socket.socket.sendall
    monkeypatch.setattr(socket.socket, "sendall", spy_sendall)
    req = ShellExecReq(command='ps', ans=True)
    shell << req
    sleep(0.1)
    assert len(calls) > 0
    js = calls[0].decode().strip()
    json_msg = JSONMessage(js, owner=gateway)
    msg = json_msg.message
    assert msg.__clazz__ == 'org.arl.fjage.shell.ShellExecReq'
    assert msg.perf.name == 'REQUEST'
    assert msg.sender.name == gateway.aid.name
    assert msg.recipient.name == 'shell'
    assert msg.command == 'ps'
    assert msg.ans is True

def test_gateway_receive_queue_limit(gateway):
    """Gateway should only store the latest 512 messages in the receive queue."""
    gateway.flush()
    smr = SendMsgReq()
    smr.num = 756
    smr.type = 0
    smr.perf = Performative.REQUEST
    smr.recipient = gateway.agent('echo')
    gateway.send(smr)
    sleep(4)  # allow time for all messages to be received
    assert len(gateway._queue) == 512
    ids = sorted([m.id for m in gateway._queue if m.id is not None])
    assert ids[-1] - ids[0] == len(ids) - 1
    assert ids[-1] >= 512

def test_gateway_send_receive_many(gateway):
    """Gateway should be able to send and receive many message."""
    rxed = [False] * 64
    NMSG = 64
    for type in range(1 , NMSG+1):
        smr = SendMsgReq()
        smr.num = 1
        smr.type = type
        smr.perf = Performative.REQUEST
        smr.recipient = gateway.agent('echo')
        gateway.send(smr)

    for type in range(1, NMSG + 1):
        m = gateway.receive(lambda m: isinstance(m, SendMsgRsp), timeout=2000)
        if m and m.type:
            rxed[m.type - 1] = True
        else:
            print(f'Error getting SendMsgRsp #{type} : {m}')

    assert rxed.count(False) == 0

def test_gateway_connection_property(gateway):
    """Gateway should update the connected property when the underlying transport is disconnected/reconnected."""
    assert gateway.is_connected()
    gateway.connector.disconnect()
    sleep(0.1)
    assert not gateway.is_connected()
    gateway.connector.connect()
    sleep(0.1)
    assert gateway.is_connected()
    gateway.close()

def test_gateway_subscribe(gateway):
    """Gateway should be able to subscribe to topics."""
    pub = gateway.agent('pub')
    # The publisher agent publishes a message every 500ms
    pubCount = [0]
    # Since the 'tick' property is not yet enabled, we should not receive any messages
    gateway.receive(lambda m: pubCount.__setitem__(0, pubCount[0] + 1) if (isinstance(m, Message) and m.__clazz__ == 'org.arl.fjage.GenericMessage' and m.sender == pub) else None, timeout=1000)
    assert pubCount[0] == 0
    # Enable publishing
    pub.tick = True
    gateway.receive(lambda m: pubCount.__setitem__(0, pubCount[0] + 1) if (isinstance(m, Message) and m.__clazz__ == 'org.arl.fjage.GenericMessage' and m.sender == pub) else None, timeout=1000)
    # Since we're not subscribing to any topics, we should not receive any messages
    assert pubCount[0] == 0
    # Subscribe to the topic
    gateway.subscribe(gateway.topic('test-topic'))
    # We should receive messages now
    gateway.receive(lambda m: pubCount.__setitem__(0, pubCount[0] + 1) if (isinstance(m, Message) and m.__clazz__ == 'org.arl.fjage.GenericMessage' and m.sender == pub) else None, timeout=1000)
    assert pubCount[0] > 0
    # Unsubscribe from the topic
    gateway.unsubscribe(gateway.topic('test-topic'))
    pub.tick = False
    gateway.close()

def test_gateway_cancel_requests_on_disconnect():
    pass

def test_gateway_request_all_agents(gateway):
    """Gateway should be able to request for all agents from the master container."""
    agents = gateway.agents()
    assert agents is not None
    assert isinstance(agents, list)
    assert len(agents) > 0
    aids = [a.name for a in agents]
    assert 'shell' in aids
    assert 'S' in aids
    assert 'echo' in aids
    assert 'test' in aids
    gateway.close()

def test_gateway_check_agent_exists(gateway):
    """Gateway should be able to check if an agent exists on the master container."""
    aid = AgentID('S', owner=gateway)
    exists = gateway.containsAgent(aid)
    assert exists is True
    aid = AgentID('T', owner=gateway)
    exists = gateway.containsAgent(aid)
    assert exists is False
    gateway.close()

def test_gateway_get_agent_for_service(gateway):
    """Gateway should be able to get the agent for a service."""
    aid = gateway.agentForService('server')
    assert aid is not None
    assert isinstance(aid, AgentID)
    assert aid.name == 'S'
    gateway.close()

def test_gateway_get_agents_for_service(gateway):
    """Gateway should be able to get all agents for a service."""
    aids = gateway.agentsForService('org.arl.fjage.test.Services.TEST')
    assert aids is not None
    assert isinstance(aids, list)
    assert len(aids) > 0
    # it should contain ParamServerAgent and EchoServerAgent only
    aidNames = [a.name for a in aids]
    assert 'S' in aidNames
    assert 'echo' in aidNames
    gateway.close()
