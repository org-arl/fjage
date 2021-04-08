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

import static org.junit.Assert.assertTrue

class fjagejsTest {


  @Test
  void fjageJSTest() {
    def testResult = false
    def testTrace = ""
    def testPending = true
    def platform = new RealTimePlatform()
    def container = new MasterContainer(platform, 5081)
    container.add("shell", new ShellAgent(new EchoScriptEngine()))
    WebServer.getInstance(8080).add("/", "/org/arl/fjage/web")
    WebServer.getInstance(8080).add("/test", new File('src/test/groovy/org/arl/fjage/test'))
    container.addConnector(new WebSocketConnector(8080, "/ws", true))
    platform.start()
    container.add('test', new Agent(){

      protected Message processRequest(Message msg) {
        if (msg instanceof SendMsgReq){
          // println("Received SendMsgReq [${msg.type}] : ${msg.num} : ${msg.sender}")
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
        return null
      }

      @Override
      protected void init() {
        add(new MessageBehavior(){
          @Override
          void onReceive(Message msg) {
            if (msg.performative == Performative.REQUEST) {
              // println("Received request : ${msg.class}")
              Message rsp = processRequest(msg)
              if (rsp == null) rsp = new Message(msg, Performative.NOT_UNDERSTOOD)
              send rsp
            } else if (msg.performative == Performative.INFORM) {
              // println("Received ntf : ${msg.class} :: ${msg.status}" )
              if (msg instanceof TestCompleteNtf){
                testResult = msg.status
                testTrace = msg.trace
                testPending = false
              }
            } else {
              println("No idea what to do with " + msg.class)
            }
          }
        })
      }
    })
    container.add("S", new ParamServerAgent())
    def ret = 0
    if (System.getProperty('manualJSTest') == null){
      // Run Jasmine based test in puppeteer.
      println "Running automated tests using puppeteer"
      def proc = "node src/test/groovy/org/arl/fjage/test/server.js".execute()
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
      while (testPending){
        platform.delay(1000)
      }
      println "-------------------Logs from fjagejs-------------------------"
    }
    println "FjageJS Test complete : ${testResult ? "passed" : "failed"}."
    if (!testResult){
      println  "JASMINE >> \n${testTrace.trim()}"
    }
    println "-------------------------------------------------------------"
    container.shutdown()
    platform.shutdown()
    assertTrue(ret == 0 && testResult)
  }
}

class TestCompleteNtf extends Message {
  boolean status
  String trace
  TestCompleteNtf() {
    super(Performative.INFORM)
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
