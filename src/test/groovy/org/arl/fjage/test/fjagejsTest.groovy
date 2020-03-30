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

public enum Params implements Parameter {
  x, y, z, a, b, s
}

public class ParamServerAgent extends Agent {
  // These will have automatic Getter/Setter Param.a and Param.b
  int a = 0;
  float b = 42.0;
  int z = 2;

  public int x = 1;

  // These are local variables (storage)
  private int z1 = 4;

  // This will be mapped to Param.y
  public float getY() { return 2; }
  public float getY(int index) { if (index == 1) return 3; }

  // This will be mapped to Param.s
  public String getS() { return "xxx"; }
  public String getS(int index) { if (index == 1) return "yyy"; }
  public String setS(int index, String val) { if (index == 1) return "yyy"; }

  // This will be mapped to Param.z for index 1
  public int getZ(int index) {
    if (index == 1) return z1;
  }

  public int setZ(int index, int val) {
    if (index == 1) {
      z1 = val;
      return z1;
    }
  }

  // This will allow Param.x to be only settable
  public int setX(int x) { return x+1; }
  public ParamServerAgent() {
    super();
  }

  @Override
  public void init() {
    register("server");
    add(new ParameterMessageBehavior(Params){
      @Override
      protected List<? extends Parameter> getParameterList(int ndx) {
        if (ndx < 0) return getParameterList();
        else if (ndx == 1) return getParameterList();
      }
    });
  }
}
