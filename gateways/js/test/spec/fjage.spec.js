/* global global isBrowser isJsDom isNode Performative, AgentID, Message, Gateway, MessageClass, JSONMessage it expect expectAsync describe spyOn beforeAll afterAll beforeEach jasmine*/

const DIRNAME = '.';
const FILENAME = 'fjage-test.txt';
const TEST_STRING = 'this is a test';
const NEW_STRING = 'new test';
const GetFileReq = MessageClass('org.arl.fjage.shell.GetFileReq');
const GetFileRsp = MessageClass('org.arl.fjage.shell.GetFileRsp');
const ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq');
const PutFileReq = MessageClass('org.arl.fjage.shell.PutFileReq');
const SendMsgReq = MessageClass('org.arl.fjage.test.SendMsgReq');
const SendMsgRsp = MessageClass('org.arl.fjage.test.SendMsgRsp');
const TestCompleteNtf = MessageClass('org.arl.fjage.test.TestCompleteNtf');

const ValidFjageActions = ['agents', 'containsAgent', 'services', 'agentForService', 'agentsForService', 'send', 'shutdown'];
const ValidFjagePerformatives = ['REQUEST', 'AGREE', 'REFUSE', 'FAILURE', 'INFORM', 'CONFIRM', 'DISCONFIRM', 'QUERY_IF', 'NOT_UNDERSTOOD', 'CFP', 'PROPOSE', 'CANCEL', ];
const DEFAULT_CLOSE_DELAY = 100;

var gwOpts;
var gObj = {};
var testType;
if (isBrowser){
  gObj = window;
  gwOpts = {
    hostname: 'localhost',
    port : '8080',
    pathname: '/ws/'
  };
  testType = 'browser';
} else if (isJsDom || isNode){
  gObj = global;
  gwOpts = {
    hostname: 'localhost',
    port : '5081',
    pathname: ''
  };
  testType = 'node';
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function fjageMessageChecker() {
  return {
    asymmetricMatch: function(compareTo) {
      var ret = true;
      var msg;
      try{
        msg = JSON.parse(compareTo);
      }catch(e){
        return false;
      }
      // ret = ret && msg.id && msg.id.length == 32;
      ret = ret && msg.action && ValidFjageActions.includes(msg.action);
      ret = ret && msg.relay;

      if (!msg.message) return ret;

      ret = ret && (!msg.message.clazz || typeof msg.message.clazz === 'string');

      if (!msg.message.data) return ret;

      ret = ret && !!msg.message.data.msgID && msg.message.data.msgID.length == 32;
      ret = ret && !!msg.message.data.sender;
      ret = ret && !!msg.message.data.recipient;
      ret = ret && msg.message.data.perf && ValidFjagePerformatives.includes(msg.message.data.perf);
      return ret;
    },
    jasmineToString: function() {
      return '<fjageMessageChecker>';
    }
  };
}

function ShellExecReqChecker() {
  return {
    asymmetricMatch: function(compareTo) {
      var ret = true;
      var msg;
      try{
        msg = JSON.parse(compareTo);
      }catch(e){
        return false;
      }
      // ret = ret && msg.id && msg.id.length == 32;
      ret = ret && msg.action && ValidFjageActions.includes(msg.action);
      ret = ret && msg.relay;

      ret = ret && !!msg.message;
      ret = ret && msg.message.clazz == 'org.arl.fjage.shell.ShellExecReq';
      ret = ret && !!msg.message.data;
      ret = ret && !!msg.message.data.msgID && msg.message.data.msgID.length == 32;
      ret = ret && !!msg.message.data.sender;
      ret = ret && !!msg.message.data.recipient;
      ret = ret && msg.message.data.perf && ValidFjagePerformatives.includes(msg.message.data.perf);
      return ret;
    },
    jasmineToString: function() {
      return '<ShellExecReqChecker>';
    }
  };
}

describe('A Gateway', function () {
  it('should be able to be constructed', async function () {
    var gw;
    var createGW = function () {
      gw = new Gateway(gwOpts);
    };
    expect(createGW).not.toThrow();
    expect(gw).toBeDefined();
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should have a successfully opened the underlying connector', async function () {
    const gw = new Gateway(gwOpts);
    await delay(700);
    expect([1, 'open']).toContain(gw.connector.sock.readyState);
    gw.close();
  });

  it('should cache Gateways to the same url+port in browser', async function () {
    if (!isBrowser) return;
    const gw = new Gateway(gwOpts);
    const gw2 = new Gateway(gwOpts);
    expect(gw).toBe(gw2);
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should register itself with the global fjage object', async function () {
    if (!isBrowser) return;
    var gw = new Gateway(gwOpts);
    expect(gObj.fjage.gateways).toContain(gw);
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should close the socket when close is called on it', async function () {
    const gw = new Gateway(gwOpts);
    await delay(300);
    gw.close();
    await delay(300);
    expect([gw.connector.sock.CLOSED, 'closed']).toContain(gw.connector.sock.readyState);
  });

  it('should remove itself from global array when closed', async function () {
    if (!isBrowser) return;
    const gw = new Gateway(gwOpts);
    await delay(300);
    gw.close();
    await delay(300);
    expect(gObj.fjage.gateways.find(el => el == gw)).toBeUndefined();
  });

  it('should send a message over a socket', async function() {
    const shell = new AgentID('shell');
    const gw = new Gateway(gwOpts);
    await delay(300); // delay allow for imports
    spyOn(gw.connector.sock, 'send').and.callThrough();
    gw.connector.sock.send.calls.reset();
    const req = new ShellExecReq();
    req.recipient = shell;
    req.command = 'boo';
    gw.request(req);
    await delay(300);
    expect(gw.connector.sock.send).toHaveBeenCalled();
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should send a socket message of valid fjage message structure', async function() {
    const shell = new AgentID('shell');
    const gw = new Gateway(gwOpts);
    await delay(300); // delay allow for imports
    spyOn(gw.connector.sock, 'send').and.callThrough();
    gw.connector.sock.send.calls.reset();
    const req = new ShellExecReq();
    req.recipient = shell;
    req.command = 'boo';
    gw.request(req);
    await delay(300);
    expect(gw.connector.sock.send).toHaveBeenCalledWith(fjageMessageChecker());
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should send correct ShellExecReq of valid fjage message structure', async function() {
    const shell = new AgentID('shell');
    const gw = new Gateway(gwOpts);
    await delay(300); // delay allow for imports
    spyOn(gw.connector.sock, 'send').and.callThrough();
    gw.connector.sock.send.calls.reset();
    const req = new ShellExecReq();
    req.recipient = shell;
    req.command = 'boo';
    gw.request(req);
    await delay(300);
    expect(gw.connector.sock.send).toHaveBeenCalledWith(ShellExecReqChecker());
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should send correct ShellExecReq of valid fjage message structure created using param constructor', async function() {
    const shell = new AgentID('shell');
    const gw = new Gateway(gwOpts);
    await delay(300); // delay allow for imports
    spyOn(gw.connector.sock, 'send').and.callThrough();
    gw.connector.sock.send.calls.reset();
    const req = new ShellExecReq({recipient: shell, cmd: 'boo'});
    gw.request(req);
    await delay(300);
    expect(gw.connector.sock.send).toHaveBeenCalledWith(ShellExecReqChecker());
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should only store the latest 128 messages in the receive queue', async function() {
    const gw = new Gateway(gwOpts);
    gw.flush();
    let smr = new SendMsgReq();
    smr.num = 256;
    smr.type = 0;
    smr.perf = Performative.REQUEST;
    smr.recipient = gw.agent('echo');
    gw.send(smr);
    await delay(3800); // allow time for all messages to be received
    expect(gw.queue.length).toBe(128);
    var ids = gw.queue.map(m => m.id).filter( id => !!id).sort((a,b) => a-b);
    expect(ids[ids.length-1]-ids[0]).toBe(ids.length-1);
    expect(ids[ids.length-1]).toBeGreaterThanOrEqual(128);
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should be able to send and receive many messages asynchronously', async function() {
    const NMSG = 64;
    const gw = new Gateway(gwOpts);
    gw.flush();
    var rxed = new Array(NMSG).fill(false);

    for (var type = 1; type <= NMSG; type++){
      let smr = new SendMsgReq();
      smr.num = 1;
      smr.type = type;
      smr.perf = Performative.REQUEST;
      smr.recipient = gw.agent('echo');
      gw.send(smr);
    }

    for (type = 1; type <= NMSG; type++){
      let m = await gw.receive(m => m instanceof SendMsgRsp,2000);
      if (m && m.type){
        rxed[m.type-1] = true;
      }else{
        console.warn('Error getting SendMsgRsp #'+type+' : ' + m);
      }
    }
    expect(rxed.filter(r => !r).length).toBe(0);
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should update the connected property when the underlying transport is disconnected/reconnected', async function() {
    const gw = new Gateway(gwOpts);
    gw.connector._reconnectTime = 300;
    await delay(700);
    expect([1, 'open']).toContain(gw.connector.sock.readyState);
    if (isBrowser) gw.connector.sock.close();
    else gw.connector.sock.destroy();
    await delay(100);
    expect(gw.connected).toBe(false);
    await delay(1000);
    expect(gw.connected).toBe(true);
    gw.connector._reconnectTime = 5000;
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should generate trigger connListener when the underlying transport is disconnected/reconnected', async function() {
    const gw = new Gateway(gwOpts);
    let spy = jasmine.createSpy('connListener');
    gw.addConnListener(spy);
    gw.connector._reconnectTime = 300;
    await delay(700);
    expect([1, 'open']).toContain(gw.connector.sock.readyState);
    spy.calls.reset();
    if (isBrowser) gw.connector.sock.close();
    else gw.connector.sock.destroy();
    await delay(100);
    expect(spy).toHaveBeenCalledWith(false);
    spy.calls.reset();
    await delay(1000);
    expect(spy).toHaveBeenCalledWith(true);
    gw.connector._reconnectTime = 5000;
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should be able to subscribe to topics', async function() {
    const gw = new Gateway(gwOpts);
    const pub = gw.agent('pub');
    // The publisher agent publishes a message every second 500ms
    let pubCount = 0;
    gw.addMessageListener(m => {
      if (m.__clazz__ == 'org.arl.fjage.GenericMessage' && m.sender == 'pub') {
        pubCount++;
      }
    });
    await delay(600);
    // Since we're not ticking, we should not receive any messages
    expect(pubCount).toBe(0);
    // Enable publishing
    pub.set('tick', true);
    await delay(600);
    // Since we're not subscribing to any topics, we should not receive any messages
    expect(pubCount).toBe(0);
    // Subscribe to the topic
    gw.subscribe(gw.topic('test-topic'));
    await delay(600);
    // We should receive messages now
    expect(pubCount).toBeGreaterThan(0);
    // Unsubscribe from the topic
    gw.unsubscribe(gw.topic('test-topic'));
    pub.set('tick', false);
    gw.close();
  });

  it('should cancel requests when disconnected', async function() {
    const gw = new Gateway(Object.assign({}, gwOpts, {returnNullOnFailedResponse: false, cancelPendingOnDisconnect: true}));
    const shell = gw.agent('shell');
    expectAsync(shell.get('language')).toBeRejectedWithError();
    if (isBrowser) gw.connector.sock.close();
    else gw.connector.sock.destroy();
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should be able to request for all agents from the master container', async function() {
    const gw = new Gateway(gwOpts);
    let agents = await gw.agents();
    expect(agents).toBeDefined();
    expect(Array.isArray(agents)).toBeTruthy();
    expect(agents.length).toBeGreaterThan(0);
    let aids = agents.map(a => a.name);
    expect(aids).toContain('shell');
    expect(aids).toContain('S');
    expect(aids).toContain('echo');
    expect(aids).toContain('test');
    gw.close();
  });

  it('should be able to check if an agent exists on the master container', async function() {
    const gw = new Gateway(gwOpts);
    let aid = new AgentID('S', false, gw);
    let exists = await gw.containsAgent(aid);
    expect(exists).toEqual(true);
    aid = new AgentID('T', false, gw);
    exists = await gw.containsAgent(aid);
    expect(exists).toEqual(false);
    gw.close();
  });

  it('should be able to get the agent for a service', async function() {
    const gw = new Gateway(gwOpts);
    let aid = await gw.agentForService('server');
    expect(aid).toBeDefined();
    expect(aid.name).toBe('S');
    gw.close();
  });

  it('should be able to get all agents for a service', async function() {
    const gw = new Gateway(gwOpts);
    let aids = await gw.agentsForService('org.arl.fjage.test.Services.TEST');
    expect(aids).toBeDefined();
    expect(Array.isArray(aids)).toBeTruthy();
    expect(aids.length).toBeGreaterThan(0);
    // it should contain ParamServerAgent and EchoServerAgent only
    let aidNames = aids.map(a => a.name);
    expect(aidNames).toContain('S');
    expect(aidNames).toContain('echo');
    gw.close();
  });

});

describe('An AgentID', function () {
  var gw;

  beforeAll(() => {
    gw = new Gateway(gwOpts);
  });

  afterAll(async () => {
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should be able to be constructed', function () {
    var aid;
    const createAID = function () {
      aid = new AgentID('agent-name', true, gw);
    };
    expect(createAID).not.toThrow();
    expect(aid).toBeDefined();
  });

  it('should have working getters and toString', function () {
    const aid = new AgentID('agent-name', true, gw);
    expect(aid.getName()).toBe('agent-name');
    expect(aid.isTopic()).toBe(true);
    expect(aid.toJSON()).toBe('#agent-name');
  });

  it('should get the value of a single parameter', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get('y');
    expect(val).toEqual(2);
  });

  it('should return null if asked to get the value of unknown parameter', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get('k');
    expect(val).toEqual(null);
  });

  it('should set the value of a single parameter and return the new value', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.set('a', 42);
    expect(val).toEqual(42);
    val = await aid.set('a', 0);
    expect(val).toEqual(0);
  });

  it('should return null if asked to set the value of unknown parameter',  async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.set('k', 42);
    expect(val).toEqual(null);
  });

  it('should get the values of an array of parameters', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get(['y', 's']);
    expect(val).toEqual([2, 'xxx']);
  });

  it('should set the values of an array of parameters and return the new values',  async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.set(['a','b'], [42, -32.876]);
    expect(val).toEqual([42, -32.876]);
    val = await aid.set(['a','b'], [0, 42]);
    expect(val).toEqual([0, 42]);
  });

  it('should get the values of all parameter on a Agent', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get();
    expect(val).toEqual({
      'org.arl.fjage.test.Params.x': 1,
      'org.arl.fjage.test.Params.y': 2,
      'org.arl.fjage.test.Params.z': 2,
      'org.arl.fjage.test.Params.s': 'xxx',
      'org.arl.fjage.test.Params.a': 0,
      'org.arl.fjage.test.Params.b': 42
    });
  });

  it('should get the value of a single indexed parameter', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get('z', 1);
    expect(val).toEqual(4);
  });

  it('should return null if asked to get the value of unknown indexed parameter', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get('k', 1);
    expect(val).toEqual(null);
  });

  it('should set the value of a single indexed parameter and return the new value',  async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.set('z', 42, 1);
    expect(val).toEqual(42);
    val = await aid.set('z', 4, 1);
    expect(val).toEqual(4);
  });

  it('should return null if asked to set the value of unknown indexed parameter', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get('k', 1);
    expect(val).toEqual(null);
  });

  it('should get the values of an array of indexed parameters',  async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get(['y', 's'], 1);
    expect(val).toEqual([3, 'yyy']);
  });

  it('should set the values of an array of indexed parameters and return the new values', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.set(['z', 's'], [42, 'boo'], 1);
    expect(val).toEqual([42, 'yyy']);
    val = await aid.set('z', 4, 1);
    expect(val).toEqual(4);
  });

  it('should get the values of all indexed parameter on a Agent', async function () {
    const aid = new AgentID('S', false, gw);
    let val = await aid.get(null, 1);
    expect(val).toEqual({
      'org.arl.fjage.test.Params.z': 4,
      'org.arl.fjage.test.Params.s': 'yyy',
      'org.arl.fjage.test.Params.y': 3
    });
  });

  it('should be able to get all the agents on the platform', async function () {
    let val = await gw.agents();
    let aids = val.map(a => a.name);
    expect(aids).toContain('shell');
    expect(aids).toContain('S');
    expect(aids).toContain('echo');
    expect(aids).toContain('test');
  });

  it('should be able to check if an agent exists on the container', async function () {
    let aid = new AgentID('S', false, gw);
    let val = await gw.containsAgent(aid);
    expect(val).toEqual(true);
    aid = new AgentID('T', false, gw);
    val = await gw.containsAgent(aid);
    expect(val).toEqual(false);
  });

});

describe('An AgentID setup to reject promises', function () {
  var gw;

  beforeAll(() => {
    gw = new Gateway(Object.assign({}, gwOpts, {returnNullOnFailedResponse: false}));
  });

  afterAll(async () => {
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  it('should reject the promise if asked to get the value of unknown parameter', async function () {
    const aid = new AgentID('S', false, gw);
    return expectAsync(aid.get('k')).toBeRejected();
  });

  it('should reject the promise if asked to set the value of unknown parameter',  async function () {
    const aid = new AgentID('S', false, gw);
    return expectAsync(aid.set('k', 42)).toBeRejected();
  });

  it('should reject the promise if asked to get the value of unknown indexed parameter', async function () {
    const aid = new AgentID('S', false, gw);
    return expectAsync(aid.get('k', 1)).toBeRejected();
  });

  it('should reject the promise if asked to set the value of unknown indexed parameter', async function () {
    const aid = new AgentID('S', false, gw);
    await expectAsync(aid.set('k', 42, 1)).toBeRejected();
  });

});

describe('A JSONMessage', function () {
  it('should serialise and deserialise correctly', function () {
    const TxFrameReq = MessageClass('org.arl.unet.phy.TxFrameReq');
    const txMsg = new TxFrameReq();
    const jsonMsg = new JSONMessage();
    jsonMsg.action = 'send';
    jsonMsg.relay = false;
    jsonMsg.message = txMsg;
    const raw = jsonMsg.toJSON();
    const parsedJsonMsg = new JSONMessage(raw);
    expect(parsedJsonMsg.action).toEqual('send');
    expect(parsedJsonMsg.message instanceof TxFrameReq).toBeTruthy();
    expect(parsedJsonMsg.message.msgID).toEqual(txMsg.msgID);
    expect(parsedJsonMsg.message.perf).toEqual(txMsg.perf);
  });

  it('should be able to deserialise base64 encoded numeric arrays', function () {
    const DATA_ARRAY = [72, 101, 108, 108, 111, 44, 32, 87, 111, 114, 108, 100, 33];
    const str = '{"action":"send","relay":false,"message":{"clazz":"org.arl.fjage.test.TestMessage","data":{"msgID":"12345678901234567890123456789012","sender":"test","recipient":"echo","perf":"REQUEST","data":{"clazz":"[B","data":"SGVsbG8sIFdvcmxkIQ=="}}}}';
    const jsonMsg = new JSONMessage(str);
    expect(jsonMsg.message.data).toEqual(DATA_ARRAY);
  });
});



describe('A Message', function () {
  it('should be able to be consuctured', function () {
    var msg;
    const createMSG = function () {
      msg = new Message();
    };
    expect(createMSG).not.toThrow();
    expect(msg).toBeDefined();
  });

  it('should have a unique msgID', function () {
    const msg1 = new Message();
    const msg2 = new Message();
    expect(msg1.msgID).not.toBe(msg2.msgID);
  });

  it('should serialise and deserialise correctly', function () {
    const msg1 = new Message();
    const msg2 = Message.fromJSON(msg1.toJSON());
    const TxFrameReq = MessageClass('org.arl.unet.phy.TxFrameReq');
    const txMsg = new TxFrameReq();
    expect(msg1).toEqual(msg2);
    expect(Message.fromJSON(txMsg.toJSON())).toEqual(txMsg);
  });
});

describe('A MessageClass', function () {
  it('should be able to create a custom Message', function () {
    expect(() => {
      let msgName = 'NewMessage';
      const NewMessage = MessageClass(msgName);
      expect(typeof NewMessage).toEqual('function');
      expect(NewMessage.__clazz__).toEqual(msgName);
      new NewMessage();
    }).not.toThrow();
  });

  it('should be able to create a custom Message with parent Message', function () {
    expect(() => {
      let msgName = 'New2Message';
      let parentName = 'ParentMessage';
      const ParentMessage = MessageClass(parentName);
      const NewMessage = MessageClass(msgName, ParentMessage);
      expect(typeof NewMessage).toEqual('function');
      expect(NewMessage.__clazz__).toEqual(msgName);
      let nm = new NewMessage();
      expect(nm instanceof NewMessage).toBeTruthy();
      expect(nm instanceof ParentMessage).toBeTruthy();
    }).not.toThrow();
  });

  it('should make sure that a MessageClass name that ends with Req has a perf of REQUEST', function () {
    let msgName = 'NewReq';
    const NewReq = MessageClass(msgName);
    let nr = new NewReq();
    expect(nr.perf).toEqual(Performative.REQUEST);
  });
});

describe('Shell GetFile/PutFile', function () {
  var gw, shell;
  beforeAll(() => {
    gw = new Gateway(gwOpts);
    shell = new AgentID('shell');
  });

  // Delete the test file after running
  // the GetFile/PutFile tests
  afterAll(async () => {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = await gw.request(pfr);
    expect(rsp).not.toBeNull();
    await delay(DEFAULT_CLOSE_DELAY);
    gw.close();
  });

  // Create a new file with the contents of TEST_STRING
  beforeEach(async () => {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING)));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeDefined();
    expect(rsp.perf).toBeDefined();
    expect(rsp.perf).toEqual(Performative.AGREE);
  });

  it('should be able to send a ShellExecReq', async function () {
    const req = new ShellExecReq();
    req.recipient = shell;
    req.command = 'boo';
    const rsp = await gw.request(req);
    expect(rsp).toBeDefined();
    expect(rsp.perf).toBeDefined();
    expect(rsp.perf).toEqual(Performative.AGREE);
  });

  it('should be able get a file using GetFileReq', async function () {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = await gw.request(gfr);
    expect(rsp).toBeTruthy();
    expect(rsp instanceof GetFileRsp).toBeTruthy();
    expect(rsp.contents).not.toBeUndefined();
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp.contents))).toEqual(TEST_STRING);
  });

  it('should be able get a section of a file using GetFileReq using offset and length', async function () {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    gfr.offset = 5;
    gfr.length = 4;
    const rsp = await gw.request(gfr);
    expect(rsp).toBeTruthy();
    expect(rsp instanceof GetFileRsp).toBeTruthy();
    expect(rsp.contents).not.toBeUndefined();
    expect(rsp.contents.length).toBe(4);
    expect(rsp.offset).toBe(5);
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp.contents))).toEqual(TEST_STRING.substr(5, rsp.contents.length));
  });

  it('should be able get a section of a file using GetFileReq using offset and 0 length', async function () {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    gfr.offset = 9;
    gfr.length = 0;
    const rsp = await gw.request(gfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp instanceof GetFileRsp).toBeTruthy();
    expect(rsp.contents).not.toBeUndefined();
    expect(rsp.contents.length).toBe(TEST_STRING.length-9);
    expect(rsp.offset).toBe(9);
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp.contents))).toEqual(TEST_STRING.substr(9));
  });

  it('should refuse to return the contents of the file if offset is beyond file length using GetFileReq', async function () {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    gfr.offset = 27;
    gfr.length = 1;
    const rsp = await gw.request(gfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.REFUSE);
  });

  it('should be able to list all files in a directory using GetFileReq', async function () {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME ;
    const rsp = await gw.request(gfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp instanceof GetFileRsp).toBeTruthy();
    const content = new TextDecoder('utf-8').decode(new Uint8Array(rsp.contents));
    const lines = content.split('\n');
    expect(lines.map(line => { return line.substr(0,line.indexOf('\t')); })).toContain(FILENAME);
  });

  it('should be delete files using PutFileReq', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr);
    expect(rsp2.perf).toEqual(Performative.FAILURE);
  });

  it('should be able to edit the file correctly using PutFileReq when offset is 0', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING + ' ' + TEST_STRING)));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr);
    expect(rsp2 instanceof GetFileRsp).toBeTruthy();
    expect(rsp2.contents).not.toBeUndefined();
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp2.contents))).toEqual(TEST_STRING + ' ' + TEST_STRING);
  });

  it('should be able to update the file correctly using PutFileReq when some content is removed', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING.slice(-4))));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr);
    expect(rsp2 instanceof GetFileRsp).toBeTruthy();
    expect(rsp2.contents).not.toBeUndefined();
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp2.contents))).toEqual(TEST_STRING.slice(-4));
  });

  it('should be able to edit the file correctly using PutFileReq when offset greater than 0', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.offset = 10;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING)));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr);
    expect(rsp2 instanceof GetFileRsp).toBeTruthy();
    expect(rsp2.contents).not.toBeUndefined();
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp2.contents))).toEqual(TEST_STRING.substring(0,10)+TEST_STRING);
  });

  it('should be able to edit the file correctly using PutFileReq when offset less than 0', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.offset = -4;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(NEW_STRING)));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr, 3000);
    expect(rsp2 instanceof GetFileRsp).toBeTruthy();
    expect(rsp2.contents).not.toBeUndefined();
    expect(rsp2.contents.length).toBe(TEST_STRING.length-4+NEW_STRING.length);
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp2.contents))).toEqual(TEST_STRING.substring(0,10) + NEW_STRING);
  });

  it('should be able to append a file using PutFileReq', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING + ' ' + TEST_STRING)));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr);
    expect(rsp2 instanceof GetFileRsp).toBeTruthy();
    expect(rsp2.contents).not.toBeUndefined();
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp2.contents))).toEqual(TEST_STRING + ' ' + TEST_STRING);
  });

  it('should be able to save the file using PutFileReq when some content is removed', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING.slice(-4))));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr);
    expect(rsp2 instanceof GetFileRsp).toBeTruthy();
    expect(rsp2.contents).not.toBeUndefined();
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp2.contents))).toEqual(TEST_STRING.slice(-4));
  });

  it('should be able to append the file using PutFileReq using offset', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.offset = 10;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING)));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr);
    expect(rsp2 instanceof GetFileRsp).toBeTruthy();
    expect(rsp2.contents).not.toBeUndefined();
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp2.contents))).toEqual(TEST_STRING.substring(0,10)+TEST_STRING);
  });

  it('should be able to append the file using PutFileReq using offset less than 0', async function () {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.offset = -4;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(NEW_STRING)));
    const rsp = await gw.request(pfr, 3000);
    expect(rsp).toBeTruthy();
    expect(rsp.perf).toEqual(Performative.AGREE);
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp2 = await gw.request(gfr);
    expect(rsp2 instanceof GetFileRsp).toBeTruthy();
    expect(rsp2.contents).not.toBeUndefined();
    expect(rsp2.contents.length).toBe(TEST_STRING.length-4+NEW_STRING.length);
    expect(new TextDecoder('utf-8').decode(new Uint8Array(rsp2.contents))).toEqual(TEST_STRING.substring(0,10) + NEW_STRING);
  });
});


async function sendTestStatus(status, trace, type) {
  var gw = new Gateway(gwOpts);
  let msg = new TestCompleteNtf();
  msg.recipient = gw.agent('test');
  msg.perf = Performative.INFORM;
  msg.status = status;
  msg.trace = trace;
  msg.type = type;
  gw.send(msg);
  gw.close();
}

var failedSpecs = [];
const autoReporter = {
  specDone: function (result) {
    result.status == 'failed' && failedSpecs.push(result);
  },

  jasmineDone: async function(result){
    let trace = `# ${failedSpecs.length} test(s) failed ${result.order && result.order.random ? '- with seed ' + result.order.seed : ''}\n`;
    failedSpecs.forEach(f =>{
      trace += `## ${f.fullName}\n`;
      f.failedExpectations.forEach(e => {
        trace += `### ${e.message}\n`;
        trace += e.stack;
        trace += '\n';
      });
    });
    if (isBrowser){
      const params = new URLSearchParams(window.location.search);
      if (params && params.get('refresh') == 'true' && result.overallStatus == 'passed') {
        setTimeout(() => window.location.reload(),3000);
        return;
      }
      if (params && params.get('send') == 'false') return;
    }
    // console.log("Jasmine Result : ", result);
    // let passed = result.overallStatus == 'passed' || result.overallStatus == 'incomplete';
    let passed = result.overallStatus == 'passed';
    await sendTestStatus(passed, trace, testType);
    await delay(500);
  }
};

jasmine.getEnv().addReporter(autoReporter);