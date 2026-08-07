import time

import pytest

pytest.importorskip("serial")

from fjagepy import SerialConnector


@pytest.fixture
def connector():
    """SerialConnector on a pyserial loopback port, so writes come back as reads."""
    conn = SerialConnector('loop://', reconnect_delay=-1)
    yield conn
    conn.disconnect()


def collect(conn):
    """Attach a receive callback, returning the list it appends lines to."""
    lines = []
    conn.set_receive_callback(lines.extend)
    return lines


def wait_for(lines, n, timeout=2.0):
    deadline = time.time() + timeout
    while len(lines) < n and time.time() < deadline:
        time.sleep(0.01)
    return lines


def test_serialconnector_requires_devname():
    """SerialConnector should require a device name."""
    with pytest.raises(TypeError):
        SerialConnector()   # type: ignore[call-arg]


@pytest.mark.parametrize(
    ("devname", "baud", "reconnect_delay"),
    [
        ("", 9600, 5),
        ("loop://", 0, 5),
        ("loop://", True, 5),
        ("loop://", 9600, -2),
        ("loop://", 9600, float("inf")),
    ],
)
def test_serialconnector_validates_constructor_arguments(devname, baud, reconnect_delay):
    with pytest.raises(ValueError):
        SerialConnector(devname, baud, reconnect_delay)


def test_serialconnector_send_receive(connector):
    """A line sent on a loopback port should come back to the receive callback."""
    lines = collect(connector)
    connector.connect()
    assert connector.is_connected()
    connector.send('{"alive": true}')
    assert wait_for(lines, 1) == ['{"alive": true}']


def test_serialconnector_reassembles_split_lines(connector):
    """Partial writes should be buffered until a complete line arrives."""
    lines = collect(connector)
    connector.connect()
    connector._port.write(b'{"ali')
    time.sleep(0.2)
    assert lines == []
    connector._port.write(b've": true}\n{"partial"')
    assert wait_for(lines, 1) == ['{"alive": true}']
    time.sleep(0.2)
    assert len(lines) == 1  # incomplete line not delivered


def test_serialconnector_disconnect(connector):
    """Disconnect should close the port and stop the read thread."""
    collect(connector)
    connector.connect()
    connector.disconnect()
    assert not connector.is_connected()
    assert not connector._read_thread.is_alive()
    with pytest.raises(ConnectionError):
        connector.send('nope')
