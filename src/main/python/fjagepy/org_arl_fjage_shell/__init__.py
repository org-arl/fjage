from fjagepy.org_arl_fjage import Message
from fjagepy.org_arl_fjage import Performative


class ShellExecReq(Message):
    """Request to execute shell command/script.

    :param cmd: command to execute.
    :param script: script file to execute.
    :param args: arguments to pass to script.

    Guidelines for directly operating on the attributes are as follows:
    1. IMPORTANT: ShellExecReq can either have a command or script, but not both
    2. cmd can be any command (str) supported by the shell
    3. script is a dictionary which contains the path to the script file. E.g. "script":{"path":"samples/01_hello.groovy"}
    4. script has to be accompanied with arguments.
    5. args is a list containing arguments to the script. E.g. []
    """

    def __init__(self, **kwargs):

        super(ShellExecReq, self).__init__()
        self.perf = Performative.REQUEST
        self.cmd = None
        self.script = None
        self.args = None
        self.__dict__.update(kwargs)
