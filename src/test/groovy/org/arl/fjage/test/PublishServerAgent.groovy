package org.arl.fjage.test

import org.arl.fjage.*
import org.arl.fjage.param.Parameter
import org.arl.fjage.param.ParameterMessageBehavior

public class PublishServerAgent extends Agent {
  Boolean tick = false

  @Override
  protected void init() {
    add(new ParameterMessageBehavior(PublishServerAgentParams.class))
    add(new TickerBehavior(500, {
      if (!tick) return
      GenericMessage msg = new GenericMessage(Performative.INFORM)
      msg.recipient = topic("test-topic")
      msg.tempearture = Math.random() * 100
      send msg
    }))
  }
}

enum PublishServerAgentParams implements Parameter{
  tick
}