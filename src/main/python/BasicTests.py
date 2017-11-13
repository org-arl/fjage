import unittest
import fjage 

class MyTestCase(unittest.TestCase):    

    global g1 
    g1 = fjage.remote.Gateway('localhost', 5081, "PythonGW")
    
    def test_gateway_connection(self):
        rsp = g1.receive(100)
        self.assertEqual( rsp, None )
        
    def test_agentForService(self):
        self.assertEqual(g1.agentForService("org.arl.fjage.shell.Services.SHELL"), 'shell')        

    def test_AgentID(self):
        a1 = g1.topic("testtopic")
        self.assertEqual(a1.name, "testtopic")
        self.assertEqual(a1.is_topic, True)        
        
if __name__ == "__main__":
    unittest.main()
    
