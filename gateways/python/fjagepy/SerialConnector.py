import logging
import threading
import time
from typing import Any, Callable, List, Optional

from .Connector import Connector

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())


class SerialConnector(Connector):
    """Simple serial port connector using pyserial.

    Requires the optional `pyserial` dependency (`pip install fjagepy[serial]`).
    """

    def __init__(self, **kwargs: Any):
        self.devname = kwargs.get("devname")
        if not self.devname or not isinstance(self.devname, str) or self.devname.strip() == "":
            raise ValueError("devname must be a non-empty string")
        self.baud: int = kwargs.get("baud", 9600)
        if not isinstance(self.baud, int) or self.baud <= 0:
            raise ValueError("baud must be a positive integer")
        self.reconnect_delay = kwargs.get("reconnect_delay", -2)
        if not isinstance(self.reconnect_delay, (int, float)) or self.reconnect_delay < -1:
            raise ValueError("reconnect_delay must be a non-negative number or -1 for no reconnect")

        try:
            import serial  # type: ignore[import-untyped]  # local import, so that pyserial stays an optional dependency
        except ImportError as e:
            raise ImportError("SerialConnector requires pyserial: pip install pyserial") from e
        self._serial = serial

        # Port and connection state
        self._port: Optional[Any] = None
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
        """Open the serial port."""
        with self._lock:
            if self._connected:
                return

            try:
                # serial_for_url() accepts device names as well as URLs (eg. loop://)
                self._port = self._serial.serial_for_url(self.devname, baudrate=self.baud, timeout=0.1)
                self._connected = True
                logger.debug(f"Connected to {self.devname}@{self.baud}")

                # Start read thread if callback is set
                if self._callback:
                    self._stop_event.clear()
                    self._read_thread = threading.Thread(target=self._read_loop, daemon=True)
                    self._read_thread.start()

            except Exception as e:
                self._cleanup_port()
                raise ConnectionError(f"Failed to connect to {self.devname}@{self.baud}: {e}")

    def disconnect(self) -> None:
        """Close the serial port."""
        with self._lock:
            if not self._connected:
                return

            self._stop_event.set()
            self._connected = False
            self._cleanup_port()

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
            if not self._connected or not self._port:
                raise ConnectionError("Not connected")
            try:
                self._port.write((msg + "\n").encode())
            except Exception as e:
                self._connected = False
                self._cleanup_port()
                raise ConnectionError(f"Failed to send message: {e}")

    def __details__(self):
        return f"dev:{self.devname}@{self.baud}"

    def __repr__(self):
        return f"SerialConnector(devname={self.devname}, baud={self.baud}, connected={self.is_connected()})"

    # Internal methods

    def _cleanup_port(self) -> None:
        """Clean up serial port resources."""
        if self._port:
            try:
                self._port.close()
            except Exception:
                pass
            self._port = None

    def _read_loop(self) -> None:
        """Background thread that reads data and calls the callback."""
        buffer = ""

        try:
            while not self._stop_event.is_set() and self._connected:
                port = self._port
                if not port:
                    break
                try:
                    # read() blocks for at most the port timeout (0.1s), allowing periodic stop checks
                    data = port.read(port.in_waiting or 1)
                    if not data:
                        continue

                    buffer += data.decode(errors="replace")
                    lines = buffer.split("\n")
                    buffer = lines.pop()  # Keep incomplete line

                    if lines and self._callback:
                        try:
                            self._callback(lines)
                        except Exception as e:
                            logger.warning(f"Callback error: {e}")

                except Exception as e:
                    if self._connected:  # Only log if we expect to be connected
                        logger.warning(f"Read error: {e}")
                        with self._lock:
                            self._connected = False
                            self._cleanup_port()
                    break

        except Exception as e:
            logger.warning(f"Read loop error: {e}")
        finally:
            # Auto-reconnect if configured and not stopping
            if not self._stop_event.is_set() and self.reconnect_delay >= 0:
                self._attempt_reconnect()

    def _attempt_reconnect(self) -> None:
        """Attempt to reopen the port with delay."""
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
