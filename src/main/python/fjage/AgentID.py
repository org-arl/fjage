class AgentID:

    """An identifier for an agent or a topic."""

    def __init__(self, name, is_topic=False):
        self.name = name
        if is_topic:
            self.is_topic = True
        else:
            self.is_topic = False
