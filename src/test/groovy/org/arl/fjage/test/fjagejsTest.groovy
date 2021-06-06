/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test

import org.arl.fjage.*
import org.arl.fjage.param.*
import org.arl.fjage.connectors.WebServer
import org.arl.fjage.connectors.WebSocketConnector
import org.arl.fjage.remote.MasterContainer
import org.arl.fjage.shell.EchoScriptEngine
import org.arl.fjage.shell.ShellAgent
import org.junit.Test
import org.arl.fjage.test.ParamServerAgent
import org.arl.fjage.test.EchoServerAgent

import static org.junit.Assert.assertTrue

class fjagejsTest {


  @Test
  void fjageJSTest() {
    def testRes = [
      "browser": [
        isComplete: false,
        didPass: false,
        trace: ""
      ],
      node: [
        isComplete: false,
        didPass: false,
        trace: ""
      ]
    ]
    def platform = new RealTimePlatform()
    def container = new MasterContainer(platform, 5081)
    container.addConnector(new WebSocketConnector(8080, "/ws", true))
    container.add("shell", new ShellAgent(new EchoScriptEngine()))
    platform.start()
    container.add('echo', new EchoServerAgent())
    container.add("S", new ParamServerAgent())
    container.add('test', new Agent(){
      @Override
      protected void init() {
        add(new MessageBehavior(){
          @Override
          void onReceive(Message msg) {
            println("Received something.. ${msg}")
            if (msg instanceof TestCompleteNtf){
              println("${msg.type} test complete : ${msg.status?"PASSED":"FAILED"}")
              testRes[msg.type].isComplete = true
              testRes[msg.type].status = msg.status
              testRes[msg.type].trace = msg.trace
            } else {
              println("No idea what to do with ${msg.class}")
            }
          }
        })
      }
    })
    def ret = 0
    if (System.getProperty('manualJSTest') == null){
      // Run Jasmine based test in puppeteer.
      println "Running automated tests using puppeteer"
      def proc = ['npm', '--prefix', 'gateways/js/', 'run', 'test'].execute()
      def sout = new StringBuilder(), serr = new StringBuilder()
      proc.consumeProcessOutput(sout, serr)
      proc.waitFor()
      ret = proc.exitValue()
      println "-------------------Logs from fjagejs-------------------------"
      println "STDOUT >> \n${sout.toString().trim()} \n"
      if (ret != 0){
        println "STDERR >> \n${serr.toString().trim()} \n"
      }
    }else{
      println "Waiting for user to run manual tests.."
      while (! testRes['node'].isComplete || ! testRes['browser'].isComplete){
        println("Waiting... ${testRes['node'].isComplete } : ${testRes['browser'].isComplete}")
        platform.delay(1000)
      }
      println "-------------------Logs from fjagejs-------------------------"
    }
    println "FjageJS Test complete : ${testResult ? "passed" : "failed"}."
    if (!testRes["node"].didPass){
      println  "JASMINE (node) >> \n${testRes["node"].trace.trim()}"
    }

    if (!testRes["browser"].didPass){
      println  "JASMINE (browser) >> \n${testRes["browser"].trace.trim()}"
    }
    println "-------------------------------------------------------------"
    container.shutdown()
    platform.shutdown()
    assertTrue(ret == 0 && testRes["node"].didPass && testRes["browser"].didPass)
  }
}

class TestCompleteNtf extends Message {
  boolean status
  String trace
  String type
  TestCompleteNtf() {
    super(Performative.INFORM)
  }
}