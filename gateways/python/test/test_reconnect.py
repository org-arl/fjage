"""Reconnection tests against a loopback fake server, so connections can be dropped on demand."""
import json
import socket
import time

import pytest

from fjagepy import Gateway, Message, Performative, TCPConnector
from fjagepy.JSONMessage import JSONMessage

TIMEOUT = 3.0


class FakeServer:
    """Stand-in for a fjåge container that accepts one gateway connection at a time."""

    def __init__(self):
        self.sock = socket.create_server(('127.0.0.1', 0))
        self.sock.settimeout(TIMEOUT)
        self.port = self.sock.getsockname()[1]
        self.conn = None

    def accept(self):
        self.conn, _ = self.sock.accept()
        self.conn.settimeout(TIMEOUT)
        self.rx = self.conn.makefile('r')

    def expect(self, predicate):
        """Read lines until one satisfies the predicate, and return it."""
        while True:
            line = self.rx.readline()
            assert line, 'connection closed before the expected line arrived'
            if predicate(line.strip()):
                return line.strip()

    def send(self, s):
        self.conn.sendall((s + '\n').encode())

    def drop(self):
        if self.conn:
            self.rx.close()
            self.conn.close()
            self.conn = None

    def close(self):
        self.drop()
        self.sock.close()


@pytest.fixture
def server():
    srv = FakeServer()
    yield srv
    srv.close()


def watching(*names):
    """Predicate matching a wantsMessagesFor line that includes all the given agent names."""
    def match(line):
        m = json.loads(line)
        return m.get('action') == 'wantsMessagesFor' and set(names) <= set(m['agentIDs'])
    return match


def wait_until(cond, timeout=TIMEOUT):
    deadline = time.monotonic() + timeout
    while not cond() and time.monotonic() < deadline:
        time.sleep(0.01)
    return cond()


def test_connector_reports_connection_changes(server):
    """The connection callback should fire on connect, loss, reconnect and disconnect."""
    states = []
    conn = TCPConnector('127.0.0.1', server.port, reconnect_delay=0.1)
    conn.set_receive_callback(lambda lines: None)
    conn.set_connection_callback(states.append)
    conn.connect()
    server.accept()
    server.drop()
    server.accept()
    assert wait_until(lambda: len(states) == 3)
    conn.disconnect()
    assert states == [True, False, True, False]


def test_gateway_restores_watch_list_after_reconnect(server):
    """After a reconnect, the gateway should resend its watch list and resume receiving notifications."""
    gw = Gateway(connector=TCPConnector('127.0.0.1', server.port, reconnect_delay=0.1))
    try:
        server.accept()
        topic = gw.topic('test-topic')
        gw.subscribe(topic)
        server.expect(watching('#test-topic', gw.aid.get_name()))

        server.drop()
        server.accept()
        server.expect(watching('#test-topic', gw.aid.get_name()))

        # keep-alives are answered on the new connection
        server.send('{"alive": true}')
        server.expect(lambda line: line == '{"alive": true}')

        # topic notifications resume
        ntf = Message(perf=Performative.INFORM)
        ntf.recipient = topic
        ntf.sender = gw.agent('pub')
        server.send(JSONMessage.createSend(msg=ntf, relay=True).to_json())
        rx = gw.receive(timeout=int(TIMEOUT * 1000))
        assert rx is not None and rx.recipient == topic
    finally:
        gw.close()


def test_gateway_close_stops_pending_reconnect(server):
    """Closing a gateway while it waits to reconnect should cancel the reconnection."""
    gw = Gateway(connector=TCPConnector('127.0.0.1', server.port, reconnect_delay=0.2))
    server.accept()
    server.drop()
    assert wait_until(lambda: not gw.is_connected())
    gw.close()
    server.sock.settimeout(0.6)
    with pytest.raises(TimeoutError):
        server.sock.accept()
