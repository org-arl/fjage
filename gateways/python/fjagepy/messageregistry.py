from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Dict, Optional

if TYPE_CHECKING:
    from .Message import Message


logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())

MESSAGE_REGISTRY: Dict[str, type["Message"]] = {}


def register_message(class_name: str, message_class: type["Message"]) -> type["Message"]:
    """Store a Message subclass under its wire name."""
    if not isinstance(class_name, str) or not class_name:
        raise TypeError('class_name must be a non-empty string')
    if not isinstance(message_class, type):
        raise TypeError('message_class must be a class')
    short_name = class_name.split('.')[-1]
    existing = MESSAGE_REGISTRY.get(class_name)
    if existing is not None and existing != message_class:
        logger.warning(
            "Overriding existing message class registered with clazz '%s': %s -> %s",
            class_name,
            existing,
            message_class,
        )
    existing = MESSAGE_REGISTRY.get(short_name)
    if existing is not None and existing != message_class:
        logger.warning(
            "Overriding existing message class registered with name '%s': %s -> %s",
            short_name,
            existing,
            message_class,
        )

    class_alias = message_class.__name__
    existing = MESSAGE_REGISTRY.get(class_alias)
    if existing is not None and existing != message_class:
        logger.warning(
            "Overriding existing message class registered with name '%s': %s -> %s",
            class_alias,
            existing,
            message_class,
        )

    message_class.__clazz__ = class_name
    MESSAGE_REGISTRY[class_name] = message_class
    MESSAGE_REGISTRY[short_name] = message_class
    MESSAGE_REGISTRY[class_alias] = message_class
    return message_class


def message_class_for_name(class_name: str) -> Optional[type["Message"]]:
    """Return the exact registered class, falling back to its short name."""
    return MESSAGE_REGISTRY.get(class_name) or MESSAGE_REGISTRY.get(class_name.split('.')[-1])