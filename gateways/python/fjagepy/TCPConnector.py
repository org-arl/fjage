import socket
import logging
import threading
import time
from typing import Callable, List, Optional

from .Connector import Connector

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())

class TCPConnector(Connector):
    """Simple TCP connector using synchronous sockets."""

    def __init__(
        self,
        host: str,
        port: int,
        reconnect_delay: float = 2.0,
    ):
        if not host or not isinstance(host, str):
            raise ValueError("host must be a non-empty string")
        if not isinstance(port, int) or not (0 < port < 65536):
            raise ValueError("port must be an integer 1-65535")

        self.host = host
        self.port = port
        self.reconnect_delay = reconnect_delay

        # Socket and connection state
        self._socket: Optional[socket.socket] = None
        self._connected = False
        self._callback: Optional[Callable[[List[str]], None]] = None

        # Threading for receive loop
        self._read_thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self._lock = threading.Lock()

    def set_receive_callback(self, callback: Callable[[List[str]], None]) -> None:
        """Set the callback function to handle incoming messages."""
        if not callable(callback):
            raise ValueError("callback must be callable")
        self._callback = callback

    def connect(self) -> None:
        """Establish the connection."""
        with self._lock:
            if self._connected:
                return

            try:
                self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self._socket.settimeout(10.0)  # 10 second connection timeout
                self._socket.connect((self.host, self.port))
                self._connected = True
                logger.debug(f"Connected to {self.host}:{self.port}")

                # Start read thread if callback is set
                if self._callback:
                    self._stop_event.clear()
                    self._read_thread = threading.Thread(target=self._read_loop, daemon=True)
                    self._read_thread.start()

            except Exception as e:
                self._cleanup_socket()
                raise ConnectionError(f"Failed to connect to {self.host}:{self.port}: {e}")

    def disconnect(self) -> None:
        """Close the connection."""
        with self._lock:
            if not self._connected:
                return

            self._stop_event.set()
            self._connected = False
            self._cleanup_socket()

            # Wait for read thread to finish
            if self._read_thread and self._read_thread.is_alive():
                self._read_thread.join(timeout=2.0)

            logger.debug("Disconnected")

    def is_connected(self) -> bool:
        """Check if connected."""
        return self._connected

    def send(self, msg: str) -> None:
        """Send a message."""
        with self._lock:
            if not self._connected or not self._socket:
                raise ConnectionError("Not connected")
            try:
                message = msg + "\n"
                self._socket.sendall(message.encode())
            except Exception as e:
                self._connected = False
                self._cleanup_socket()
                raise ConnectionError(f"Failed to send message: {e}")

    def __str__(self):
        return f"TCPConnector(host={self.host}, port={self.port}, connected={self.is_connected()})"

    # Internal methods

    def _cleanup_socket(self) -> None:
        """Clean up socket resources."""
        if self._socket:
            try:
                self._socket.close()
            except Exception:
                pass
            self._socket = None

    def _read_loop(self) -> None:
        """Background thread that reads data and calls the callback."""
        buffer = ""

        try:
            # Set socket to non-blocking for periodic stop checks
            self._socket.settimeout(0.1)

            while not self._stop_event.is_set() and self._connected:
                try:
                    data = self._socket.recv(4096)
                    if not data:
                        # Connection closed by peer
                        with self._lock:
                            self._connected = False
                            self._cleanup_socket()
                        logger.warning("Connection closed by peer")
                        break

                    buffer += data.decode()
                    lines = buffer.split("\n")
                    buffer = lines.pop()  # Keep incomplete line

                    if lines and self._callback:
                        try:
                            self._callback(lines)
                        except Exception as e:
                            logger.warning(f"Callback error: {e}")

                except socket.timeout:
                    # Normal timeout, continue loop
                    continue
                except Exception as e:
                    if self._connected:  # Only log if we expect to be connected
                        logger.warning(f"Read error: {e}")
                        with self._lock:
                            self._connected = False
                            self._cleanup_socket()
                    break

        except Exception as e:
            logger.warning(f"Read loop error: {e}")
        finally:
            # Auto-reconnect if configured and not stopping
            if not self._stop_event.is_set() and self.reconnect_delay >= 0:
                self._attempt_reconnect()

    def _attempt_reconnect(self) -> None:
        """Attempt to reconnect with delay."""
        while not self._stop_event.is_set() and self.reconnect_delay >= 0:
            try:
                logger.debug(f"Attempting to reconnect in {self.reconnect_delay}s...")
                time.sleep(self.reconnect_delay)

                if self._stop_event.is_set():
                    break

                self.connect()
                return  # Successfully reconnected

            except Exception as e:
                logger.warning(f"Reconnection failed: {e}")
                continue
