/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/


import org.arl.fjage.*
import org.arl.fjage.connectors.Connector
import org.arl.fjage.connectors.WebServer
import org.arl.fjage.connectors.WebSocketConnector
import org.arl.fjage.remote.MasterContainer
import org.junit.Test
import static org.junit.Assert.assertTrue

class fjagejsTest {

  @Test
  void fjageJSTest() {
    def testStatus = false
    def testPending = true
    def platform = new RealTimePlatform()
    def container = new MasterContainer(platform, 5081)
    WebServer.getInstance(8080).add("/", "/org/arl/fjage/web")
    WebServer.getInstance(8080).add("/test", new File('src/jsTest/groovy'))
    Connector conn = new WebSocketConnector(8080, "/shell/ws")
    container.addConnector(new WebSocketConnector(8080, "/ws", true))
    platform.start()
    container.add('test', new Agent(){
      @Override
      protected void init() {
        add(new MessageBehavior(){
          @Override
          void onReceive(Message msg) {
            println("Received: " + msg.performative + ',' + msg.recipient + ',' + msg.sender)
            testPending = false
            testStatus = msg.performative != Performative.FAILURE
          }
        })
      }
    })
    while (testPending) platform.delay(1000)
    platform.shutdown()
    assertTrue(testStatus)
  }
}

