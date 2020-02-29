import { Performative, AgentID, Message, Gateway, MessageClass } from '../../fjage.js';

const DIRNAME = '/tmp';
const FILENAME = 'fjage-test.txt';
const TEST_STRING = 'this is a test';
const NEW_STRING = 'new test';
const GetFileReq = MessageClass('org.arl.fjage.shell.GetFileReq');
const GetFileRsp = MessageClass('org.arl.fjage.shell.GetFileRsp');
const ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq');
const PutFileReq = MessageClass('org.arl.fjage.shell.PutFileReq');

const ValidFjageActions = ['agents', 'containsAgent', 'services', 'agentForService', 'agentsForService', 'send', 'shutdown'];
const ValidFjagePerformatives = ['REQUEST', 'AGREE', 'REFUSE', 'FAILURE', 'INFORM', 'CONFIRM', 'DISCONFIRM', 'QUERY_IF', 'NOT_UNDERSTOOD', 'CFP', 'PROPOSE', 'CANCEL', ];

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
  it('should send a message over WebSocket', function(done){
    const shell = new AgentID('shell');
    const gw = new Gateway();
    spyOn(gw.sock, 'send').and.callThrough();
    setTimeout(() => {
      gw.sock.send.calls.reset();
      const req = new ShellExecReq();
      req.recipient = shell;
      req.cmd = 'boo';
      gw.request(req);
      setTimeout(() => {
        expect(gw.sock.send).toHaveBeenCalled();
        done();
      },100);
    },100);
  });
  it('should send a WebSocket message of valid fjage message structure', function(done){
    const shell = new AgentID('shell');
    const gw = new Gateway();
    spyOn(gw.sock, 'send').and.callThrough();
    setTimeout(() => {
      gw.sock.send.calls.reset();
      const req = new ShellExecReq();
      req.recipient = shell;
      req.cmd = 'boo';
      gw.request(req);
      setTimeout(() => {
        expect(gw.sock.send).toHaveBeenCalledWith(fjageMessageChecker());
        done();
      },100);
    },100);
  });
  it('should send correct ShellExecReq of valid fjage message structure', function(done){
    const shell = new AgentID('shell');
    const gw = new Gateway();
    spyOn(gw.sock, 'send').and.callThrough();
    setTimeout(() => {
      gw.sock.send.calls.reset();
      const req = new ShellExecReq();
      req.recipient = shell;
      req.cmd = 'boo';
      gw.request(req);
      setTimeout(() => {
        expect(gw.sock.send).toHaveBeenCalledWith(ShellExecReqChecker());
        done();
      },100);
    },100);
  });
  it('should send correct ShellExecReq of valid fjage message structure created using param constructor', function(done){
    const shell = new AgentID('shell');
    const gw = new Gateway();
    spyOn(gw.sock, 'send').and.callThrough();
    setTimeout(() => {
      gw.sock.send.calls.reset();
      const req = new ShellExecReq({recipient: shell, cmd: 'boo'});
      gw.request(req);
      setTimeout(() => {
        expect(gw.sock.send).toHaveBeenCalledWith(ShellExecReqChecker());
        done();
      },100);
    },100);
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
    expect(aid.toJSON()).toBe('#agent-name');
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

describe('A MessageClass', function () {
  it('should be able to create a custom Message', function () {
    expect(() => {
      let msgName = 'NewMessage';
      const NewMessage = MessageClass(msgName);
      expect(typeof NewMessage).toEqual('function');
      expect(NewMessage.__clazz__).toEqual(msgName);
      let nm = new NewMessage();
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
});


describe('Shell GetFile/PutFile', function () {
  var gw, shell;
  beforeAll(() => {
    gw = new Gateway();
    shell = new AgentID('shell');
  });

  // Delete the test file after running
  // the GetFile/PutFile tests
  afterAll((done) => {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(() => {
      setTimeout(() => {
        gw.close();
        done();
      },100);
    });
  });

  // Create a new file with the contents of TEST_STRING
  beforeEach((done) => {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING)));
    const rsp = gw.request(pfr, 2000);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      done();
    });
  });

  it('should be able to send a ShellExecReq', function (done) {
    const req = new ShellExecReq();
    req.recipient = shell;
    req.cmd = 'boo';
    const rsp = gw.request(req);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      done();
    });
  });

  it('should be able get a file using GetFileReq', function (done) {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = gw.request(gfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg instanceof GetFileRsp).toBeTruthy();
      expect(msg.contents).not.toBeUndefined();
      expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING);
      done();
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
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg instanceof GetFileRsp).toBeTruthy();
      expect(msg.contents).not.toBeUndefined();
      expect(msg.contents.length).toBe(4);
      expect(msg.ofs).toBe(5);
      expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.substr(5, msg.contents.length));
      done();
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
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg instanceof GetFileRsp).toBeTruthy();
      expect(msg.contents).not.toBeUndefined();
      expect(msg.contents.length).toBe(TEST_STRING.length-9);
      expect(msg.ofs).toBe(9);
      expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.substr(9));
      done();
    });
  });

  it('should refuse to return the contents of the file if offset is beyond file length using GetFileReq', function (done) {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME + '/' + FILENAME;
    gfr.ofs = 27;
    gfr.len = 1;
    const rsp = gw.request(gfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.REFUSE);
      done();
    });
  });

  it('should be able to list all files in a directory using GetFileReq', function (done) {
    var gfr = new GetFileReq();
    gfr.recipient = shell;
    gfr.filename = DIRNAME ;
    const rsp = gw.request(gfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg instanceof GetFileRsp).toBeTruthy();
      const content = new TextDecoder('utf-8').decode(new Uint8Array(msg.contents));
      const lines = content.split('\n');
      expect(lines.map(line => {
        return line.substr(0,line.indexOf('\t'));
      })).toContain(FILENAME);
      done();
    });
  });

  it('should be delete files using PutFileReq', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
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
    });
  });

  it('should be able to edit the file correctly using PutFileReq when offset is 0', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING + ' ' + TEST_STRING)));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg instanceof GetFileRsp).toBeTruthy();
        expect(msg.contents).not.toBeUndefined();
        expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING + ' ' + TEST_STRING);
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    });
  });

  it('should be able to update the file correctly using PutFileReq when some content is removed', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING.slice(-4))));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg instanceof GetFileRsp).toBeTruthy();
        expect(msg.contents).not.toBeUndefined();
        expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.slice(-4));
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    });
  });

  it('should be able to edit the file correctly using PutFileReq when offset greater than 0', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.ofs = 10;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING)));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg instanceof GetFileRsp).toBeTruthy();
        expect(msg.contents).not.toBeUndefined();
        expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.substring(0,10)+TEST_STRING);
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    });
  });

  it('should be able to edit the file correctly using PutFileReq when offset less than 0', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.ofs = -4;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(NEW_STRING)));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg instanceof GetFileRsp).toBeTruthy();
        expect(msg.contents).not.toBeUndefined();
        expect(msg.contents.length).toBe(TEST_STRING.length-4+NEW_STRING.length);
        expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.substring(0,10) + NEW_STRING);
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    });
  });

  it('should be able to append a file using PutFileReq', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING + ' ' + TEST_STRING)));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg instanceof GetFileRsp).toBeTruthy();
        expect(msg.contents).not.toBeUndefined();
        expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING + ' ' + TEST_STRING);
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    });
  });

  it('should be able to save the file using PutFileReq when some content is removed', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING.slice(-4))));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg instanceof GetFileRsp).toBeTruthy();
        expect(msg.contents).not.toBeUndefined();
        expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.slice(-4));
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    });
  });

  it('should be able to append the file using PutFileReq using offset', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.ofs = 10;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(TEST_STRING)));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg instanceof GetFileRsp).toBeTruthy();
        expect(msg.contents).not.toBeUndefined();
        expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.substring(0,10)+TEST_STRING);
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    });
  });

  it('should be able to append the file using PutFileReq using offset less than 0', function (done) {
    const pfr = new PutFileReq();
    pfr.recipient = shell;
    pfr.filename = DIRNAME + '/' + FILENAME;
    pfr.ofs = -4;
    pfr.contents = Array.from((new TextEncoder('utf-8').encode(NEW_STRING)));
    const rsp = gw.request(pfr);
    expect(rsp).not.toBeNull();
    rsp.then(msg => {
      expect(msg).toBeTruthy();
      expect(msg.perf).toEqual(Performative.AGREE);
      var gfr = new GetFileReq();
      gfr.recipient = shell;
      gfr.filename = DIRNAME + '/' + FILENAME;
      const rsp2 = gw.request(gfr);
      expect(rsp2).not.toBeNull();
      rsp2.then((msg) => {
        expect(msg instanceof GetFileRsp).toBeTruthy();
        expect(msg.contents).not.toBeUndefined();
        expect(msg.contents.length).toBe(TEST_STRING.length-4+NEW_STRING.length);
        expect(new TextDecoder('utf-8').decode(new Uint8Array(msg.contents))).toEqual(TEST_STRING.substring(0,10) + NEW_STRING);
        done();
      }).catch((ex) => {
        console.error(ex);
        fail();
      });
    });
  });
});


function sendTestStatus(status) {
  var gw = new Gateway();
  let msg = new Message();
  msg.recipient = gw.agent('test');
  msg.perf = status ? Performative.AGREE : Performative.FAILURE;
  gw.send(msg);
  gw.close();
}

var autoReporter = {
  jasmineDone: function (result) {
    console.log('Finished suite: ' + result.overallStatus);
    const params = new URLSearchParams(window.location.search);
    if (params && params.get('send') == 'false') return;
    if (params && params.get('refresh') == 'true' && result.overallStatus == 'passed') {
      setTimeout(() => window.location.reload(),3000);
      return;
    }
    sendTestStatus(result.overallStatus == 'passed');
  }
};

jasmine.getEnv().addReporter(autoReporter);
