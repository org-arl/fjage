#!/usr/bin/env python3

import sys
import os
sys.path.insert(0, os.path.abspath("gateways/python"))
import unittest
from fjagepy import *


class FjageTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.g = Gateway('localhost', port=5082)
        if cls.g is None:
            print('Could not connect to fjage master container on localhost:5081')

    def test_gateway_connection(self):
        """Test: should be able to construct gateway object"""
        self.assertIsInstance(self.g, Gateway)

    def test_gateway_agentid(self):
        """Test: should be able to retrieve the AgentID of the gateway"""
        self.assertIn("PythonGW", self.g.getAgentID().name)

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
        self.g.subscribe(AgentID("abc", owner=self.g))
        self.assertIn("abc__ntf", self.g.subscriptions)
        self.g.subscribe(AgentID("def", True, owner=self.g))
        self.assertIn("def", self.g.subscriptions)
        self.g.unsubscribe(AgentID("abc", owner=self.g))
        self.assertNotIn("abc__ntf", self.g.subscriptions)
        self.g.unsubscribe(AgentID("def", True, owner=self.g))
        self.assertNotIn("def", self.g.subscriptions)

    def test_send_Message(self):
        """Test: Should be able to send a message to a agent running in master container"""
        m = Message(recipient='test')
        self.assertIsInstance(m, Message)
        self.assertTrue(self.g.send(m))

    def test_send_GenericMessage(self):
        """Test: Should be able to send a message to a agent running in master container"""
        m = GenericMessage(recipient='test', text='hello', data=[1, 2, 3])
        self.assertIsInstance(m, Message)
        self.assertTrue(self.g.send(m))

    def test_parameters(self):
        """Test: Should be able to set and get parameter values"""
        self.assertEqual(self.g.agent('S').y, 2)            # should get the value of a single parameter
        self.assertEqual(self.g.agent('S').k, None)         # should return None if asked to get the value of unknown parameter
        self.g.agent('S').a = 42                            # should set the value of a single parameter and return the new value
        self.assertEqual(self.g.agent('S').a, 42)
        self.g.agent('S').a = 0
        self.assertEqual(self.g.agent('S').a, 0)
        self.g.agent('S').k = 42                            # should return None if asked to set the value of unknown parameter
        self.assertEqual(self.g.agent('S').k, None)
        self.g.agent('S')[1].z = 4                          # should get the value of a single indexed parameter
        self.assertEqual(self.g.agent('S')[1].z, 4)
        self.assertEqual(self.g.agent('S')[1].k, None)      # should return None if asked to get the value of unknown indexed parameter
        self.g.agent('S')[1].z = 42                         # should set the value of a single indexed parameter and return the new value
        self.assertEqual(self.g.agent('S')[1].z, 42)
        self.g.agent('S')[1].z = 4
        self.assertEqual(self.g.agent('S')[1].z, 4)
        self.g.agent('S')[1].k = 1
        self.assertEqual(self.g.agent('S')[1].k, None)     # should return None if asked to set the value of unknown indexed test_parameters

    @classmethod
    def tearDownClass(cls):
        cls.g.close()

if __name__ == "__main__":
    suite = unittest.TestLoader().loadTestsFromTestCase(FjageTest)
    test_result = unittest.TextTestRunner(verbosity=1).run(suite)
    failures = len(test_result.errors) + len(test_result.failures)

    g = Gateway('localhost', port=5082)
    m = Message(recipient='test')
    if (failures > 0):
        m.perf = Performative.FAILURE
        print("Test failed")
    else:
        m.perf = Performative.AGREE
        print("Test passed")
    g.send(m)
    g.close()
