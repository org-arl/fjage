#!/usr/bin/env python3

import sys
import os
sys.path.insert(0, os.path.abspath("gateways/python"))
import pytest
from fjagepy import *


class TestFjage:

    @pytest.fixture(scope="class")
    def gateway(self):
        """Setup gateway connection for all tests"""
        g = Gateway('localhost', port=5082)
        if g is None:
            pytest.fail('Could not connect to fjage master container on localhost:5082')
        yield g
        g.close()

    def test_gateway_connection(self, gateway):
        """Test: should be able to construct gateway object"""
        assert isinstance(gateway, Gateway)

    def test_gateway_agentid(self, gateway):
        """Test: should be able to retrieve the AgentID of the gateway"""
        assert "gateway" in gateway.getAgentID().name

    def test_subscribe_unsubscribe_topic(self, gateway):
        """Test: Should be able to add the subscriptions in the list"""
        gateway.subscribe(gateway.topic("abc"))
        assert "abc" in gateway.subscriptions
        gateway.subscribe(gateway.topic("def"))
        assert "def" in gateway.subscriptions
        gateway.unsubscribe(gateway.topic("abc"))
        assert "abc" not in gateway.subscriptions
        gateway.subscribe(gateway.topic("ghi"))
        assert "ghi" in gateway.subscriptions
        gateway.unsubscribe(gateway.topic("def"))
        assert "def" not in gateway.subscriptions

    def test_subscribe_unsubscribe_agent(self, gateway):
        """Test: Should be able to remove the subscriptions from the list"""
        gateway.subscribe(AgentID("abc", owner=gateway))
        assert "abc__ntf" in gateway.subscriptions
        gateway.subscribe(AgentID("def", True, owner=gateway))
        assert "def" in gateway.subscriptions
        gateway.unsubscribe(AgentID("abc", owner=gateway))
        assert "abc__ntf" not in gateway.subscriptions
        gateway.unsubscribe(AgentID("def", True, owner=gateway))
        assert "def" not in gateway.subscriptions

    def test_send_Message(self, gateway):
        """Test: Should be able to send a message to a agent running in master container"""
        m = Message(recipient='test')
        assert isinstance(m, Message)
        assert gateway.send(m) is True

    def test_send_GenericMessage(self, gateway):
        """Test: Should be able to send a message to a agent running in master container"""
        m = GenericMessage(recipient='test', text='hello', data=[1, 2, 3])
        assert isinstance(m, Message)
        assert gateway.send(m) is True

    def test_parameters(self, gateway):
        """Test: Should be able to set and get parameter values"""
        assert gateway.agent('S').y == 2            # should get the value of a single parameter
        assert gateway.agent('S').k is None         # should return None if asked to get the value of unknown parameter
        gateway.agent('S').a = 42                   # should set the value of a single parameter and return the new value
        assert gateway.agent('S').a == 42
        gateway.agent('S').a = 0
        assert gateway.agent('S').a == 0
        gateway.agent('S').k = 42                   # should return None if asked to set the value of unknown parameter
        assert gateway.agent('S').k is None
        gateway.agent('S')[1].z = 4                 # should get the value of a single indexed parameter
        assert gateway.agent('S')[1].z == 4
        assert gateway.agent('S')[1].k is None      # should return None if asked to get the value of unknown indexed parameter
        gateway.agent('S')[1].z = 42                # should set the value of a single indexed parameter and return the new value
        assert gateway.agent('S')[1].z == 42
        gateway.agent('S')[1].z = 4
        assert gateway.agent('S')[1].z == 4
        gateway.agent('S')[1].k = 1
        assert gateway.agent('S')[1].k is None      # should return None if asked to set the value of unknown indexed test_parameters


def test_completion_notification():
    """Send completion notification after tests"""
    g = Gateway('localhost', port=5082)
    m = Message(recipient='test')
    m.perf = Performative.AGREE
    print("Test passed")
    g.send(m)
    g.close()


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
