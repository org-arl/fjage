import pytest, logging
from fjagepy import AgentID, MessageClass, Gateway
from .conftest import DEFAULT_HOST, DEFAULT_PORT

logging.basicConfig(level=logging.DEBUG)

@pytest.fixture
def gateway():
    # Setup code before each test
    gw = Gateway(hostname=DEFAULT_HOST, port=DEFAULT_PORT)
    yield gw
    # Teardown code after each test
    gw.close()

def test_agentid_construction(gateway):
    """AgentID should be constructable."""
    aid = AgentID('agent-name', owner=gateway, topic=True)
    assert isinstance(aid, AgentID)

def test_agentid_getters(gateway):
    """AgentID should have working getters and toString."""
    aid = AgentID('agent-name', owner=gateway, topic=True)
    assert aid.get_name() == 'agent-name'
    assert aid.is_topic() is True
    assert aid.to_json() == '#agent-name'

def test_agentid_get_param(gateway):
    """AgentID should get the value of a single parameter."""
    aid = AgentID('S', owner=gateway, topic=False)
    assert aid.y == 2

def test_agentid_get_unknown_param(gateway):
    """AgentID should return null if asked to get the value of unknown parameter."""
    aid = AgentID('S', owner=gateway, topic=False)
    assert aid.k is None

def test_agentid_set_param(gateway):
    """AgentID should set the value of a single parameter and return the new value."""
    aid = AgentID('S', owner=gateway, topic=False)
    aid.a = 42
    assert aid.a == 42
    aid.a = 0
    assert aid.a == 0

def test_agentid_set_unknown_param(gateway):
    """AgentID should return null if asked to set the value of unknown parameter."""
    aid = AgentID('S', owner=gateway, topic=False)
    aid.k = 42
    assert aid.k is None

def test_agentid_get_param_array(gateway):
    """AgentID should get the values of an array of parameters."""
    aid = AgentID('S', owner=gateway, topic=False)
    vals = [aid.y, aid.s]
    assert vals == [2, 'xxx']

def test_agentid_set_param_array(gateway):
    """AgentID should set the values of an array of parameters and return the new values."""
    aid = AgentID('S', owner=gateway, topic=False)
    aid.a, aid.b = 42, -32.876
    assert aid.a == 42
    assert aid.b == -32.876
    aid.a, aid.b = 0, 42
    assert aid.a == 0
    assert aid.b == 42

def test_agentid_get_all_params(gateway):
    """AgentID should get the values of all parameter on a Agent."""
    aid = AgentID('S', owner=gateway, topic=False)
    val = aid.get()
    # val is a dictionary of all parameters
    params = {
        'org.arl.fjage.test.Params.x': 1,
        'org.arl.fjage.test.Params.y': 2,
        'org.arl.fjage.test.Params.z': 2,
        'org.arl.fjage.test.Params.s': 'xxx',
        'org.arl.fjage.test.Params.a': 0,
        'org.arl.fjage.test.Params.b': 42
    }
    assert val == params

def test_agentid_get_indexed_param(gateway):
    """AgentID should get the value of a single indexed parameter."""
    aid = AgentID('S', owner=gateway, topic=False)
    assert aid[1].z == 4

def test_agentid_get_unknown_indexed_param(gateway):
    """AgentID should return null if asked to get the value of unknown indexed parameter."""
    aid = AgentID('S', owner=gateway, topic=False)
    assert aid[1].k is None

def test_agentid_set_indexed_param(gateway):
    """AgentID should set the value of a single indexed parameter and return the new value."""
    aid = AgentID('S', owner=gateway, topic=False)
    aid[1].z = 42
    assert aid[1].z == 42
    aid[1].z = 4
    assert aid[1].z == 4

def test_agentid_set_unknown_indexed_param(gateway):
    """AgentID should return null if asked to set the value of unknown indexed parameter."""
    aid = AgentID('S', owner=gateway, topic=False)
    aid[1].k = 42
    assert aid[1].k is None

def test_agentid_get_indexed_param_array(gateway):
    """AgentID should get the values of an array of indexed parameters."""
    aid = AgentID('S', owner=gateway, topic=False)
    assert aid[1].y == 3
    assert aid[1].s == 'yyy'

def test_agentid_set_indexed_param_array(gateway):
    """AgentID should set the values of an array of indexed parameters and return the new values."""
    aid = AgentID('S', owner=gateway, topic=False)
    aid[1].z, aid[1].s = 42, 'boo'
    assert aid[1].z == 42
    assert aid[1].s == 'yyy'
    aid[1].z = 4
    assert aid[1].z == 4

def test_agentid_get_all_indexed_params(gateway):
    """AgentID should get the values of all indexed parameter on a Agent."""
    aid = AgentID('S', owner=gateway, topic=False)
    val = aid.get(1)
    params = {
        'org.arl.fjage.test.Params.z': 4,
        'org.arl.fjage.test.Params.s': 'yyy',
        'org.arl.fjage.test.Params.y': 3
    }
    assert val == params

