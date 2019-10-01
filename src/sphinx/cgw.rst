C Gateway
=========

.. highlight:: C

Introduction
------------

This C package provides a Gateway interface to interact with the fjåge agents. The fjåge agents reside in one or more containers that provide agent management, directory and messaging services. Various containers may run on the same node or on different nodes in a network. This C gateway interface allows the external C programs to interact with fjåge agents.

The first step is to compile the library:

.. code-block:: sh

   git clone git@github.com:org-arl/fjage.git
   cd src/main/c
   make

if all goes well, you should have a `libfjage.a` file in the folder after compilation. You'll need this and the `fjage.h` file to link your C program against.

In your C program::

   #include "fjage.h"

Open a connection
-----------------

If a fjage server is running, we can create a connection using `Gateway` class::

   fjage_gw_t gw = fjage_tcp_open(hostname, port);

where `hostname` and `port` is the IP address and the port number of the device on which the fjåge server is running. This returns a gateway handle (or NULL on error) that is required by the rest of the API.

Send and receive messages
-------------------------

We have seen earlier that the agents interact with each other using messages. The C gateway can similarly send and receive messages to the agents running on containers running on diffeent machines. An example of request and response message are as shown below:

Request message::

   fjage_msg_t msg = fjage_msg_create("org.arl.fjage.Message", FJAGE_REQUEST);
   fjage_aid_t aid = fjage_aid_create("abc");
   fjage_msg_set_recipient(msg, myaid);
   fjage_send(gw, msg);

where `abc` is the name of the agent you are trying to send the message to. Once the message is sent, the message and the agentID needs to be freed::

   fjage_aid_destroy(aid);

However, a successfully sent message should not be freed by the caller.

Close a connection:
-------------------

In order to close the connection to the fjåge server::

   fjage_close(gw);

Simple example
--------------

A simple example of executing a shell command from remote connection is shown below::

   #include <stdio.h>
   #include "fjage.h"

   int main() {
      fjage_gw_t gw = fjage_tcp_open("localhost", 5081);
      if (gw == NULL) {
         printf("Connection failed\n");
         return 1;
      }
      fjage_aid_t aid = fjage_agent_for_service(gw, "org.arl.fjage.shell.Services.SHELL");
      if (aid == NULL) {
         printf("Could not find SHELL agent\n");
         fjage_close(gw);
         return 1;
      }
      fjage_msg_t msg = fjage_msg_create("org.arl.fjage.shell.ShellExecReq", FJAGE_REQUEST);
      fjage_msg_set_recipient(msg, aid);
      fjage_msg_add_string(msg, "cmd", "ps");
      fjage_msg_t rsp = fjage_request(gw, msg, 1000);
      if (rsp != NULL && fjage_msg_get_performative(rsp) == FJAGE_AGREE) printf("SUCCESS\n");
      else printf("FAILURE\n");
      if (rsp != NULL) fjage_msg_destroy(rsp);
      fjage_aid_destroy(aid);
      fjage_close(gw);
      return 0;
   }

This is compiled using `gcc -o demo.out demo.c libfjage.a` assuming that this file is saved as `demo.c`.

API documentation
-----------------

This only scratches the surface of what can be done the fjåge C gateway. For more, refer to the documentation in the C header file (`fjage.h` shown below) and examples in the test script (`test_fjage.c <https://github.com/org-arl/fjage/blob/master/src/main/c/test_fjage.c>`_).

.. literalinclude:: ../main/c/fjage.h
   :start-after: #define _FJAGE_H_
   :end-before: #endif

