const { Performative, AgentID, Message, Gateway, MessageClass } = require('fjage');
const ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq');

(async function () {
    const shell = new AgentID('shell');
    const gw = new Gateway({
        hostname: 'localhost',
        port : '5081',
    });
    const req = new ShellExecReq();
    req.recipient = shell;
    req.cmd = 'a=2; a+2;';
    req.ans = true;
    let rsp = await gw.request(req);
    console.log(rsp);
    gw.close();
})()


