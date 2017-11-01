class Performative:
    """An action represented by a message."""
    REQUEST             = "REQUEST"             # Request an action to be performed.
    AGREE               = "AGREE"               # Agree to performing the requested action.
    REFUSE              = "REFUSE"              # Refuse to perform the requested action.
    FAILURE             = "FAILURE"             # Notification of failure to perform a requested or agreed action.
    INFORM              = "INFORM"              # Notification of an event.
    CONFIRM             = "CONFIRM"             # Confirm that the answer to a query is true.
    DISCONFIRM          = "DISCONFIRM"          # Confirm that the answer to a query is false.
    QUERY_IF            = "QUERY_IF"            # Query if some statement is true or false.
    NOT_UNDERSTOOD      = "NOT_UNDERSTOOD"      # Notification that a message was not understood.
    CFP                 = "CFP"                 # Call for proposal.
    PROPOSE             = "PROPOSE"             # Response for CFP.
    CANCEL              = "CANCEL"              # Cancel pending request.
