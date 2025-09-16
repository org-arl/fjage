from enum import Enum

class Performative(Enum):
    REQUEST = "REQUEST"
    AGREE = "AGREE"
    REFUSE = "REFUSE"
    FAILURE = "FAILURE"
    INFORM = "INFORM"
    CONFIRM = "CONFIRM"
    DISCONFIRM = "DISCONFIRM"
    QUERY_IF = "QUERY_IF"
    NOT_UNDERSTOOD = "NOT_UNDERSTOOD"
    CFP = "CFP"
    PROPOSE = "PROPOSE"
    CANCEL = "CANCEL"

    def __str__(self):
        return self.value