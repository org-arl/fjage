/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test

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
    def platform = new RealTimePlatform()
    def container = new MasterContainer(platform, 5081)
    WebServer.getInstance(8080).add("/", "/org/arl/fjage/web")
    WebServer.getInstance(8080).add("/test", new File('src/test/groovy/org/arl/fjage/test'))
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
            testStatus = msg.performative != Performative.FAILURE
          }
        })
      }
    })
    // Run Jasmine based test in puppeteer.
    def proc = "node src/test/groovy/org/arl/fjage/test/server.js".execute();
    def sout = new StringBuilder(), serr = new StringBuilder()
    proc.consumeProcessOutput(sout, serr)
    proc.waitFor();
    def ret = proc.exitValue();
    println "NPM : out> $sout err> $serr \n ret = $ret \n testStatus = $testStatus"
    platform.shutdown()
    assertTrue(ret == 0 && testStatus)
  }
}

