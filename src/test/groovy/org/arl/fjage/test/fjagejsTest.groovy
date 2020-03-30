/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test

import org.arl.fjage.*
import org.arl.fjage.param.*;
import org.arl.fjage.connectors.WebServer
import org.arl.fjage.connectors.WebSocketConnector
import org.arl.fjage.remote.MasterContainer
import org.arl.fjage.shell.EchoScriptEngine
import org.arl.fjage.shell.ShellAgent
import org.junit.Test
import org.arl.fjage.test.ParamServerAgent

import static org.junit.Assert.assertTrue

class fjagejsTest {

  @Test
  void fjageJSTest() {
    def testResult = false
    def testPending = true
    def platform = new RealTimePlatform()
    def container = new MasterContainer(platform, 5081)
    container.add("shell", new ShellAgent(new EchoScriptEngine()))
    WebServer.getInstance(8080).add("/", "/org/arl/fjage/web")
    WebServer.getInstance(8080).add("/test", new File('src/test/groovy/org/arl/fjage/test'))
    container.addConnector(new WebSocketConnector(8080, "/ws", true))
    platform.start()
    container.add('test', new Agent(){
      @Override
      protected void init() {
        add(new MessageBehavior(){
          @Override
          void onReceive(Message msg) {
            println("Received: " + msg.performative + ',' + msg.recipient + ',' + msg.sender)
            testResult = msg.performative == Performative.AGREE
            testPending = false
          }
        })
      }
    })
    container.add("S", new ParamServerAgent());
    def ret = 0
    if (System.getProperty('manualJSTest') == null){
      // Run Jasmine based test in puppeteer.
      println "Running automated tests using puppeteer"
      def proc = "node src/test/groovy/org/arl/fjage/test/server.js".execute()
      def sout = new StringBuilder(), serr = new StringBuilder()
      proc.consumeProcessOutput(sout, serr)
      proc.waitFor()
      ret = proc.exitValue()
      println "NPM : out = $sout \n err = $serr \n ret = $ret \n testStatus = $testResult"
    }else{
      println "waiting for user to run manual tests"
      while (testPending){
        platform.delay(1000)
      }
      println "test complete " + testPending + " : " + testResult
    }
    container.shutdown()
    platform.shutdown()
    assertTrue(ret == 0 && testResult)
  }
}
