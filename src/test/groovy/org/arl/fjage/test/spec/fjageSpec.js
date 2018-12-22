import { Performative, AgentID, Message, Gateway, MessageClass } from '../../fjage.js';

const DIRNAME = '/tmp';
const FILENAME = 'fjage-test.txt';
const TEST_STRING = 'this is a test';
var GetFileReq = MessageClass('org.arl.fjage.shell.GetFileReq');
var GetFileRsp = MessageClass('org.arl.fjage.shell.GetFileRsp');
var ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq');
var PutFileReq = MessageClass('org.arl.fjage.shell.PutFileReq');

describe('A Gateway', function () {
  it('should be able to be constructed', function (done) {
    var gw;
    var createGW = function () {
      gw = new Gateway();
    };
    expect(createGW).not.toThrow();
    expect(gw).toBeDefined();
    setTimeout(() => {
      gw.close();
      done();
    },100);
  });

  it('should have a successfully opened WebSocket connection', function (done) {
    const gw = new Gateway();
    setTimeout(() => {
      expect(gw.sock.readyState).toBe(1);
      gw.close();
      done();
    }, 200);
  });

  it('should cache Gateways to the same url+port', function (done) {
    const gw = new Gateway();
    const gw2 = new Gateway();
    expect(gw).toBe(gw2);
    setTimeout(() => {
      gw.close();
      done();
    },100);
  });

  it('should register itself with the global window.fjage object', function (done) {
    var gw = new Gateway();
    expect(window.fjage.gateways).toContain(gw);
    setTimeout(() => {
      gw.close();
      done();
    },100);
  });

  it('should close the socket when close is called on it', function (done) {
    const gw = new Gateway();
    setTimeout(() => {
      gw.close();
      setTimeout(() => {
        expect(gw.sock.readyState).toBe(gw.sock.CLOSED);
        done();
      }, 100);
    }, 100);
  });
  it('should remove itself from global array when closed', function (done) {
    const gw = new Gateway();
    setTimeout(() => {
      gw.close();
      setTimeout(() => {
        expect(window.fjage.gateways.find(el => el == gw)).toBeUndefined();
        done();
      }, 100);
    }, 100);
  });
});


describe('An AgentID', function () {
  var gw;

  beforeAll(() => {
    gw = new Gateway();
  });

  afterAll((done) => {
    setTimeout(() => {
      gw.close();
      done();
    },100);
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
    expect(aid.toString()).toBe('#agent-name');
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
    const msg2 = Message._deserialize(msg1._serialize());
    const TxFrameReq = MessageClass('org.arl.unet.phy.TxFrameReq');
    const txMsg = new TxFrameReq();
    expect(msg1).toEqual(msg2);
    expect(Message._deserialize(txMsg._serialize())).toEqual(txMsg);
  });
});


describe('Shell GetFile/PutFile', function () {
  var gw, shell;
  beforeAll(() => {
    gw = new Gateway();
    // gw.debug = true;
    shell = new AgentID('shell');
  });

  afterAll((done) => {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      setTimeout(() => {

        gw.close();
        done();
      },100);
    });
  });

  beforeEach((done) => {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING)));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      expect(msg.perf).toEqual(Performative.AGREE);
      done();
    }).catch((ex) => {
      console.error(ex);
      fail();
    });
  });

  it('should be able to send a ShellExecReq', function (done) {
    const req = new ShellExecReq();
    req.recipient = shell;
    req.cmd = 'boo';
    const rsp = gw.request(req);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      expect(msg.perf).toEqual(Performative.AGREE);
      done();
    }).catch((ex) => {
      console.error(ex);
      fail();
    });
  });

  it('should be able get a file using GetFileReq', function (done) {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = gw.request(gfr);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      expect(msg instanceof GetFileRsp).toBeTruthy();
      expect(msg.contents).not.toBeUndefined();
      expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING);
      done();
    }).catch((ex) => {
      console.error(ex);
      fail();
    });
  });

  it('should be able get a section of a file using GetFileReq using offset and length', function (done) {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    gfr.ofs = 5;
    gfr.len = 4;
    const rsp = gw.request(gfr);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      expect(msg instanceof GetFileRsp).toBeTruthy();
      expect(msg.contents).not.toBeUndefined();
      expect(msg.contents.length).toBe(4);
      expect(msg.ofs).toBe(5);
      expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.substr(5, msg.contents.length));
      done();
    }).catch((ex) => {
      console.error(ex);
      fail();
    });
  });

  it('should be able get a section of a file using GetFileReq using offset and 0 length', function (done) {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    gfr.ofs = 9;
    gfr.len = 0;
    const rsp = gw.request(gfr);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      expect(msg instanceof GetFileRsp).toBeTruthy();
      expect(msg.contents).not.toBeUndefined();
      expect(msg.contents.length).toBe(TEST_STRING.length-9);
      expect(msg.ofs).toBe(9);
      expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.substr(9));
      done();
    }).catch((ex) => {
      console.error(ex);
      fail();
    });
  });

  it('should refuse to return the contents of the file if offset is beyond filelength using GetFileReq', function (done) {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    gfr.ofs = 27;
    gfr.len = 1;
    const rsp = gw.request(gfr);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      expect(msg.perf).toEqual(Performative.REFUSE);
      done();
    }).catch((ex) => {
      console.error(ex);
      fail();
    });
  });

  it('should be able to list all files in a directory using GetFileReq', function (done) {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME ;
    const rsp = gw.request(gfr);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      expect(msg instanceof GetFileRsp).toBeTruthy();
      const content = new TextDecoder('utf-8').decode(new Uint8Array(msg.contents));
      const lines = content.split('\n');
      expect(lines.map(line => {
        return line.substr(0,line.indexOf('\t'));
      })).toContain(FILENAME);
      done();
    }).catch((ex) => {
      console.error(ex);
      fail();
    });
  });

  it('should be delete files using PutFileReq', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then((msg) => {
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg.perf).toEqual(Performative.FAILURE);
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    }).catch((ex) => {
      console.error(ex);
      fail();
    });
  });
});


function sendTestStatus(status) {
  var gw = new Gateway();
  let msg = new Message();
  msg.recipient = gw.agent('test');
  msg.performative = status ? Performative.AGREE : Performative.FAILURE;
  gw.send(msg);
  gw.close();
}

var autoReporter = {
  jasmineDone: function (result) {
    console.log('Finished suite: ' + result.overallStatus);
    sendTestStatus(result.overallStatus == 'passed');
  }
};

jasmine.getEnv().addReporter(autoReporter);
