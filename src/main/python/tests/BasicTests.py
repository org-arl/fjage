import unittest
from fjagepy import *


class MyTestCase(unittest.TestCase):

    global g
    g = org_arl_fjage_remote.Gateway('localhost', 1101, "PythonGW")

    def test_gateway_connection(self):
        self.assertIsInstance(g, org_arl_fjage_remote.Gateway)

    def test_topic(self):
        g.topic("xyz")

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
        m = org_arl_fjage.Message()
        m.recipient = '#abc'
        self.assertIsInstance(m, org_arl_fjage.Message)
        self.assertTrue(g.send(m))
    #

    def test_send_receive_Message(self):
        m = org_arl_fjage.Message()
        m.recipient = '#abc'
        g.subscribe(org_arl_fjage.AgentID("abc", True))
        g.send(m)
        self.assertTrue(g.receive(org_arl_fjage.Message, 1000), True)
        self.assertEqual(g.receive(org_arl_fjage.Message, 1000).recipient, '#abc')

    def test_agentForService(self):
        self.assertEqual(g.agentForService("org.arl.fjage.shell.Services.SHELL"), 'shell2')

    def test_AgentID(self):
        a = g.topic("testtopic")
        self.assertEqual(a.name, "testtopic")
        self.assertEqual(a.is_topic, True)


if __name__ == "__main__":
    unittest.main()
