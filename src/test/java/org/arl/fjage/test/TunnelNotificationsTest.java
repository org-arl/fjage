package org.arl.fjage.test;

import org.arl.fjage.*;
import org.arl.fjage.param.*;
import org.arl.fjage.remote.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TunnelNotificationsTest {

  private static class MyMessageListener implements MessageListener {
    public List<Message> msgs = Collections.synchronizedList(new ArrayList<>());
    @Override
    public boolean onReceive(Message msg) {
      msgs.add(msg);
      return false;
    }
  }

  @Test
  public void testConnectionNotifications() {
    Platform platform = new RealTimePlatform();
    Container c1 = new Container(platform);
    Container c2 = new Container(platform);
    MyMessageListener l1 = new MyMessageListener();
    c1.addListener(l1);
    platform.start();

    Tunnel t1 = new Tunnel(0);
    c1.add("t1", t1);
    platform.delay(250);

    Tunnel t2 = new Tunnel("localhost", t1.getPort());
    c2.add("t2", t2);
    platform.delay(500);

    boolean foundConn = false;
    synchronized (l1.msgs) {
      for (Message m: l1.msgs) {
        if (m instanceof TunnelConnectionNtf) {
          foundConn = true;
          break;
        }
      }
    }
    assertTrue("TunnelConnectionNtf should be published on server container", foundConn);

    // Request parameters and check connIDs parameter is present
    Message req = new ParameterReq();
    req.setRecipient(new AgentID("t1"));
    req.setSender(new AgentID("tester"));
    c1.send(req);
    platform.delay(250);

    ParameterRsp prsp = null;
    for (Message m: l1.msgs) if (m instanceof ParameterRsp) prsp = (ParameterRsp)m;
    assertNotNull("Expected a ParameterRsp in container listener", prsp);
    Object val = prsp.get(TunnelParam.connIDs);
    assertNotNull("connIDs parameter should be present", val);
    assertTrue("connIDs should be a Map", val instanceof Map<?, ?>);
    Map<?, ?> ids = (Map<?, ?>) val;
    assertFalse("connIDs should contain at least one connection id", ids.isEmpty());

    platform.shutdown();
  }

}
