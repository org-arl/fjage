#!/usr/bin/env python3

import sys
import os
sys.path.insert(0, os.path.abspath("src/main/python"))
import unittest
from fjagepy import *


class FjageTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.g = Gateway('localhost', 5081, "PythonGW")
        if cls.g is None:
            print('Could not connect to fjage master container on localhost:5081')

    def test_gateway_connection(self):
        """Test: should be able to construct gateway object"""
        self.assertIsInstance(self.g, Gateway)

    def test_gateway_agentid(self):
        """Test: should be able to retrieve the AgentID of the gateway"""
        self.assertEqual(self.g.getAgentID().name, "PythonGW")

    def test_subscribe_unsubscribe_topic(self):
        """Test: Should be able to add the subscriptions in the list"""
        self.g.subscribe(self.g.topic("abc"))
        self.assertIn("abc", self.g.subscriptions)
        self.g.subscribe(self.g.topic("def"))
        self.assertIn("def", self.g.subscriptions)
        self.g.unsubscribe(self.g.topic("abc"))
        self.assertNotIn("abc", self.g.subscriptions)
        self.g.subscribe(self.g.topic("ghi"))
        self.assertIn("ghi", self.g.subscriptions)
        self.g.unsubscribe(self.g.topic("def"))
        self.assertNotIn("def", self.g.subscriptions)

    def test_subscribe_unsubscribe_agent(self):
        """Test: Should be able to remove the subscriptions from the list"""
        self.g.subscribe(AgentID(self.g, "abc"))
        self.assertIn("abc__ntf", self.g.subscriptions)
        self.g.subscribe(AgentID(self.g, "def", True))
        self.assertIn("def", self.g.subscriptions)
        self.g.unsubscribe(AgentID(self.g, "abc"))
        self.assertNotIn("abc__ntf", self.g.subscriptions)
        self.g.unsubscribe(AgentID(self.g, "def", True))
        self.assertNotIn("def", self.g.subscriptions)

    def test_send_Message(self):
        """Test: Should be able to send a message to a agent running in master container"""
        m = Message(recipient='shell')
        self.assertIsInstance(m, Message)
        self.assertTrue(self.g.send(m))

    def test_send_GenericMessage(self):
        """Test: Should be able to send a message to a agent running in master container"""
        m = GenericMessage(recipient='shell', text='hello', data=[1, 2, 3])
        self.assertIsInstance(m, Message)
        self.assertTrue(self.g.send(m))

    @classmethod
    def tearDownClass(cls):
        cls.g.close()

if __name__ == "__main__":
    suite = unittest.TestLoader().loadTestsFromTestCase(FjageTest)
    test_result = unittest.TextTestRunner(verbosity=1).run(suite)
    failures = len(test_result.errors) + len(test_result.failures)

    g = Gateway('localhost', 5081, "PythonGW")
    m = Message(recipient='test')
    if (failures > 0):
        m.perf = Performative.FAILURE
        print("Test failed")
    else:
        m.perf = Performative.AGREE
        print("Test passed")
    g.send(m)
    g.close()
