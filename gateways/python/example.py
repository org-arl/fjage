#!/usr/bin/env python3
# Example of using fjagepy to connect to a fjage platform and run a simple test.
# Make sure to start the fjage platform first, e.g. by running (in the fjage root directory):
#   ./fjage.sh
#
# Then run this script:
#   python3 example.py

import logging
from fjagepy import Gateway, Performative, Services, ShellExecReq

# Uncomment the following line to enable debug logging
logging.basicConfig(level=logging.DEBUG)

with Gateway('localhost', 5081) as gw:
    shell = gw.agentForService(Services.SHELL)
    if shell is None:
        print("No shell agent found, exiting")
        exit(1)
    print("Found shell agent: " + str(shell))
    req = ShellExecReq()
    req.command = 'ps'
    req.ans = True
    rsp = shell.request(req, timeout=5000)
    if rsp is None:
        print("No response received within timeout")
    elif rsp.perf == Performative.REFUSE:
        print("Request was refused")
    elif rsp.perf == Performative.AGREE:
        print("Command output:\n  " + rsp.ans)
    else:
        print("Unexpected response: " + str(rsp))

    # Get a parameter from Shell agent
    print(f"shell.language is : {shell.language}")

    # Get all parameters from Shell agent
    params = shell.get()
    print("Shell parameters:")
    for k, v in params.items():
        print(f"  {k}: {v}")

    # Set a parameter on Shell agent
    # This doesn't work because the Shell agent
    # doesn't have any settable parameters, but
    # it shows how one could do it.
    shell.language = 'python'
    print(f"shell.language is : {shell.language}")

