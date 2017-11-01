import uuid as _uuid


class Message(object):
    """Base class for messages transmitted by one agent to another."""

    def __init__(self, **kwargs):

        self.msgID = str(_uuid.uuid4())
        self.perf = None
        self.recipient = None
        self.sender = None
        self.inReplyTo = None
        self.__dict__.update(kwargs)

    def __str__(self):
        p = self.perf if self.perf else "MESSAGE"
        if self.__class__ == Message:
            return p
        return p + ": " + str(self.__class__.__name__);
