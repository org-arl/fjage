Interacting via a Shell
=======================

.. note:: Work in progress

If we wanted to provide a remote shell that users could `telnet` into, rather than the console shell, we would replace `new ConsoleShell()` with `new TcpShell(8001)` where 8001 is the TCP/IP port number that is to provide the interactive shell. We could then access the shell by `telnet localhost 8001`.
