import { Performative, AgentID, Message, GenericMessage, Gateway, MessageClass } from '../../fjage.js';

var gw = new Gateway();

describe("A Gateway", function() {
  var foo = 1;
  it("should be able to be consuctured", function() {
    var gw1;
    var createGW = function(){
      gw1 = new Gateway();
    }

    expect(createGW).not.toThrow();
    expect(gw1).toBeDefined();
  });

  it("should have a successfully opened WebSocket connection", function() {
    var gw1 = new Gateway();
    expect(gw1.sock.readyState).toBe(1);
  });

  it("constructor should cache Gateways to the same url+port", function() {
    var gw1 = new Gateway();
    var gw2 = new Gateway();
    expect(gw1).toBe(gw2);
  });

  it("registers itself with the global window.fjage object", function() {
    var gw1 = new Gateway();
    expect(window.fjage.gateways).toContain(gw1)
  });

});


describe("An AgentID", function() {
  var foo = 1;
  it("should be able to be consuctured", function() {
    var aid;
    var createAID = function(){
      aid = new AgentID('agent-name', true, gw);
    }

    expect(createAID).not.toThrow();
    expect(aid).toBeDefined();
  });

  it("should have working getters and toString", function() {
    var aid = new AgentID('agent-name', true, gw);
    expect(aid.getName()).toBe('agent-name');
    expect(aid.isTopic()).toBe(true);
    expect(aid.toString()).toBe('#agent-name');
  });

});

describe("An Message", function() {
  var foo = 1;
  it("should be able to be consuctured", function() {
    var msg;
    var createMSG = function(){
      msg = new Message();
    }

    expect(createMSG).not.toThrow();
    expect(msg).toBeDefined();
  });

  it("should have a unique msgID", function() {
    var msg1 = new Message();
    var msg2 = new Message();

    expect(msg1.msgID).not.toBe(msg2.msgID);
  });

  it("should serialise and deserialise correctly.", function() {
    var msg1 = new Message();
    var msg2 = Message._deserialize(msg1._serialize());
    var TxFrameReq = MessageClass('org.arl.unet.phy.TxFrameReq');
    var txMsg = new TxFrameReq();


    expect(msg1).toEqual(msg2);
    expect(Message._deserialize(txMsg._serialize())).toEqual(txMsg);

  });

});







function sendTestStatus(status){
  let msg = new Message();
  msg.recipient = gw.agent('test');
  msg.performative = status ? Performative.AGREE : Performative.FAILURE;
  gw.send(msg);
}

var autoReporter = {
  jasmineDone: function(result) {
    console.log('Finished suite: '+ result.overallStatus);
    sendTestStatus(result.overallStatus == "passed");
  }
}

jasmine.getEnv().addReporter(autoReporter);
