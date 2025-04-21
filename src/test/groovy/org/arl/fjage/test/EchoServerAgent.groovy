package org.arl.fjage.test

import org.arl.fjage.*

public class EchoServerAgent extends Agent {
    @Override
    protected void init() {
        add(new MessageBehavior(){
            @Override
            void onReceive(Message msg) {
                if (msg.performative == Performative.REQUEST) {
                    Message rsp = processRequest(msg)
                    if (rsp == null) rsp = new Message(msg, Performative.NOT_UNDERSTOOD)
                    send rsp
                } else {
                    println("No idea what to do with " + msg.class)
                }
            }
        })
    }

    protected Message processRequest(Message msg) {
        if (! msg instanceof SendMsgReq) return null
        add(new OneShotBehavior() {
            @Override
            public void action() {
                (1..msg.num).each{
                    def rsp = new SendMsgRsp(msg)
                    rsp.id = it
                    rsp.type = msg.type
                    send rsp
                }
            }
        })
        return new Message(msg, Performative.AGREE)
    }
}

class SendMsgReq extends Message {
  int num
  int type
}

class SendMsgRsp extends Message {
  int id
  int type
  SendMsgRsp(Message req) {
    super(req, Performative.INFORM)   // create a response with inReplyTo = req
  }
}
