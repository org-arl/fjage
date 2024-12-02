import { AgentID, Gateway, MessageClass } from './node_modules/fjage/dist/esm/fjage.js'

const ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq');

(async function () {
    const shell = new AgentID('shell');
    const gw = new Gateway({
        hostname: 'localhost',
        port : '8080',
        pathname: '/ws/'
    });
    const req = new ShellExecReq();
    req.recipient = shell;
    req.command = 'a=2; a+2;';
    req.ans = true;
    let rsp = await gw.request(req);
    console.log(rsp);
    gw.close();
})()


