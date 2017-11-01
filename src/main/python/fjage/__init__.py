import os
import errno
import logging

# with import * include following modules in the list
__all__ = ["AgentID", "Performative", "Message", "GenericMessage", "ShellExecReq", "Action", "Gateway"]

version = 1.0


def initLogging():
    # create logger
    logger = logging.getLogger('org.arl.fjage')
    logger.setLevel(logging.DEBUG)
    filename = "logs/log-python.txt"
    if not os.path.exists(os.path.dirname(filename)):
        try:
            os.makedirs(os.path.dirname(filename))
        except OSError as exc:
            if exc.errno != errno.EEXIST:
                raise

    # create console handler and set level to debug
    ch = logging.FileHandler(filename)
    ch.setLevel(logging.DEBUG)

    # create formatter
    formatter = logging.Formatter('%(created)11.3f|%(levelname)s|%(filename)s@%(lineno)d:%(funcName)s|%(message)s', datefmt='%s')

    # add formatter to ch
    ch.setFormatter(formatter)

    # add ch to logger
    logger.addHandler(ch)


# Start logging
initLogging()
