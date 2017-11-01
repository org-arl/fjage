import sys
from fjage import *

print("Starting Tests...")
try:
    g = Gateway.Gateway('localhost', 5081, "PythonGW")
    print("Connected")
except Exception as e:
    print("Exception:" + str(e))
    sys.exit(0)

req = Message.Message()
g.send(req)
