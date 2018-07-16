import os
import errno
import uuid as _uuid
import logging


class AgentID:
    """An identifier for an agent or a topic."""

    def __init__(self, name, is_topic=False):
        self.name = name
        if is_topic:
            self.is_topic = True
        else:
            self.is_topic = False


class Performative:
    """
    An action represented by a message. The performative actions are a subset of the
    FIPA ACL recommendations for interagent communication.
    """

    REQUEST = "REQUEST"               #: Request an action to be performed.
    AGREE = "AGREE"                   #: Agree to performing the requested action.
    REFUSE = "REFUSE"                 #: Refuse to perform the requested action.
    FAILURE = "FAILURE"               #: Notification of failure to perform a requested or agreed action.
    INFORM = "INFORM"                 #: Notification of an event.
    CONFIRM = "CONFIRM"               #: Confirm that the answer to a query is true.
    DISCONFIRM = "DISCONFIRM"         #: Confirm that the answer to a query is false.
    QUERY_IF = "QUERY_IF"             #: Query if some statement is true or false.
    NOT_UNDERSTOOD = "NOT_UNDERSTOOD"  # : Notification that a message was not understood.
    CFP = "CFP"                       #: Call for proposal.
    PROPOSE = "PROPOSE"               #: Response for CFP.
    CANCEL = "CANCEL"                 #: Cancel pending request.


class Message(object):
    """
    Base class for messages transmitted by one agent to another. This class provides
    the basic attributes of messages and is typically extended by application-specific
    message classes. To ensure that messages can be sent between agents running
    on remote containers, all attributes of a message must be serializable.
    """

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


class GenericMessage(Message):
    """A message class that can convey generic messages represented by key-value pairs."""

    def __init__(self, **kwargs):
        super(GenericMessage, self).__init__()
        self.map = dict()
        self.__dict__.update(kwargs)


def _initLogging():
    # create logger
    logger = logging.getLogger('org.arl.fjage')
    logger.setLevel(logging.DEBUG)
    filename = "logs/log-python.txt"
    if not os.path.exists(os.path.dirname(filename)):
        try:
            os.makedirs(os.path.dirname(filename))
        except OSError as exc:
            if exc.errno != errno.EEXIST:
                raise

    # create console handler and set level to debug
    ch = logging.FileHandler('logs/log-python.txt')
    ch.setLevel(logging.DEBUG)

    # create formatter
    formatter = logging.Formatter('%(created)11.3f|%(levelname)s|%(filename)s@%(lineno)d:%(funcName)s|%(message)s', datefmt='%s')

    # add formatter to ch
    ch.setFormatter(formatter)

    # add ch to logger
    logger.addHandler(ch)


# Start logging
# _initLogging()
