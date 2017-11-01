from .Message import Message


class GenericMessage(Message):
    """A message class that can convey generic messages represented by key-value pairs."""

    def __init__(self, **kwargs):
        super(GenericMessage, self).__init__()
        self.map = dict()
        self.__dict__.update(kwargs)
