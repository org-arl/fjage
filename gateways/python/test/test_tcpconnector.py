import pytest

from fjagepy import TCPConnector


@pytest.mark.parametrize(
    ("hostname", "port", "reconnect_delay"),
    [
        ("", 1100, 5),
        ("localhost", 0, 5),
        ("localhost", 65536, 5),
        ("localhost", True, 5),
        ("localhost", 1100, -2),
        ("localhost", 1100, float("nan")),
    ],
)
def test_tcpconnector_validates_constructor_arguments(hostname, port, reconnect_delay):
    with pytest.raises(ValueError):
        TCPConnector(hostname, port, reconnect_delay)