Python Gateway for fjage
========================

This python package provides a `Gateway` class to interact with the fjage agents. The fjage agents reside in one or more containers that provide agent management, directory and messaging services. Various containers may run on the same node or on different nodes in a network. fjage uses JSON for communication between remote containers. The JSON objects are exchanged over some low level networked connection protocol like TCP. This package allows the developers to interact with fjage agents using an interface implemented in python. The python APIs use the package `fjagepy`.

General modules
---------------

The following modules provide all the functionalities needed to interact with the fjage agents running remotely:

    * `fjagepy.org_arl_fjage`
    * `fjagepy.org_arl_fjage_remote`
    * `fjagepy.org_arl_fjage_shell`


Usage
-----

Installation::

    pip install fjagepy

To import all general modules::

    from fjagepy import *

Useful links
------------

        * `fjagepy home <https://github.com/org-arl/fjage/tree/dev/src/main/python>`
        * `fjagepy documentation <>`
