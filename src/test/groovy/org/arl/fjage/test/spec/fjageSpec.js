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
    expect(gw1).not.toBe(null);
  });

  it("should have a successfully opened WebSocket connection", function() {
    var gw1
    var createGW = function(){
      gw1 = new Gateway();
    }

    expect(createGW).not.toThrow();
    expect(gw1.sock.readyState).toBe(1);
  });

    it("constructor should cache Gateways to the same url+port", function() {
    var gw1
    var createGW = function(){
       gw1 = new Gateway();
    }

    expect(createGW).not.toThrow();
    expect(gw1).toBe(gw);
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
