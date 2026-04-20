from abc import ABC, abstractmethod
from typing import Any, Callable, List, Optional


class Connector(ABC):
    """Abstract base class for fjåge transport connectors."""

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
