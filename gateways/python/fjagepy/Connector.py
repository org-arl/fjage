import logging
from abc import ABC, abstractmethod
from typing import Any, Callable, List, Optional

logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())


class Connector(ABC):
    """Abstract base class for fjåge transport connectors."""

    _conn_callback: Optional[Callable[[bool], None]] = None

    def __init__(self, **kwargs: Any) -> None:
        """ Initialize the connector with any necessary parameters. Subclasses can define their own parameters as needed. """
        pass

    @abstractmethod
    def connect(self) -> None:
        """Establish the transport connection."""
        pass

    @abstractmethod
    def disconnect(self) -> None:
        """Close the transport connection."""
        pass

    @abstractmethod
    def is_connected(self) -> bool:
        """Check if the transport is currently connected."""
        pass

    @abstractmethod
    def send(self, msg: str) -> None:
        """
        Send a message over the transport.

        Args:
            msg: The message object to send (e.g., a JSONMessage or dict).
        """
        pass

    @abstractmethod
    def set_receive_callback(self, callback: Callable[[List[Any]], None]) -> None:
        """
        Set a callback function to handle incoming messages.

        Args:
            callback: Function that receives a list of messages whenever new data arrives.
        """
        pass

    @abstractmethod
    def __details__(self) -> str:
        """Return a string with details about the connector (e.g., host and port)."""
        pass

    def set_connection_callback(self, callback: Optional[Callable[[bool], None]]) -> None:
        """
        Set a callback function to be notified when the connection is established or lost.

        Args:
            callback: Function that receives True after every successful connect (including
                reconnects) and False when the connection is lost, or None to clear it.
        """
        if callback is not None and not callable(callback):
            raise ValueError("callback must be callable")
        self._conn_callback = callback

    def _notify_connection(self, state: bool) -> None:
        """Notify the connection callback. Subclasses must call this without holding any lock that send() takes."""
        callback = self._conn_callback
        if callback is None:
            return
        try:
            callback(state)
        except Exception as e:
            logger.warning(f"Connection callback error: {e}")
