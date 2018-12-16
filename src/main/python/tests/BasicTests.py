import unittest
from fjagepy import *


class MyTestCase(unittest.TestCase):

    global g
    # g = Gateway('192.168.1.131', 1100, "PythonGW")
    g = Gateway('localhost', 5081, "PythonGW")
    if g is None:
        print("Could not connect to fjage master container on localhost:5081")

    def test_gateway_connection(self):
        self.assertIsInstance(g, Gateway)

    def test_gateway_agentid(self):
        self.assertEqual(g.getAgentID(), "PythonGW")

    # def test_topic(self):
    #     topic = g.topic("mytopic")
    #     self.assertTrue(topic is not None and str(topic) == '#mytopic')

    def test_subscribe_unsubscribe(self):
        g.subscribe(g.topic("abc"))
        self.assertIn("abc", g.subscribers)
        g.subscribe(g.topic("def"))
        self.assertIn("def", g.subscribers)
        g.unsubscribe(g.topic("abc"))
        self.assertNotIn("abc", g.subscribers)
        g.subscribe(g.topic("ghi"))
        self.assertIn("ghi", g.subscribers)
        g.unsubscribe(g.topic("def"))
        self.assertNotIn("def", g.subscribers)

    def test_send_Message(self):
        m = Message()
        m.recipient = '#abc'
        self.assertIsInstance(m, Message)
        self.assertTrue(g.send(m))

    def test_send_Message(self):
        m = Message()
        m.recipient = '#abc'
        self.assertIsInstance(m, Message)
        self.assertTrue(g.send(m))

    def test_send_receive_Message(self):
        m = Message()
        m.recipient = '#abc'
        g.subscribe(AgentID("abc", True))
        g.send(m)
        self.assertTrue(g.receive(Message, 1000), True)
        self.assertEqual(g.receive(Message, 1000).recipient, '#abc')

    def test_agentForService(self):
        self.assertEqual(g.agentForService("unknown"), "")
        self.assertEqual(g.agentForService("org.arl.fjage.shell.Services.SHELL"), 'shell')

    def test_agentForService(self):
        self.assertEqual(g.agentsForService("org.arl.fjage.shell.Services.SHELL")[0], 'shell')

    def test_AgentID(self):
        a = g.topic("testtopic")
        self.assertEqual(a.name, "testtopic")
        self.assertEqual(a.is_topic, True)


if __name__ == "__main__":
    unittest.main()
