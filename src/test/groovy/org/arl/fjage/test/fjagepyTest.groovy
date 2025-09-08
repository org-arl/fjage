package org.arl.fjage.test

import org.arl.fjage.*
import org.arl.fjage.connectors.WebSocketHubConnector
import org.arl.fjage.remote.MasterContainer
import org.arl.fjage.shell.EchoScriptEngine
import org.arl.fjage.shell.ShellAgent
import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertEquals

class fjagepyTest {

  @Test
  void fjagePythonTest() {
    def testResult = [
      isComplete: false,
      didPass: false,
      trace: ""
    ]
    def platform = new RealTimePlatform()
    def container = new MasterContainer(platform, 5081)
    container.addConnector(new WebSocketHubConnector(8080, "/ws", true))
    container.add("shell", new ShellAgent(new EchoScriptEngine()))
    platform.start()
    container.add('echo', new EchoServerAgent())
    container.add("S", new ParamServerAgent())
    container.add("pub", new PublishServerAgent())
    container.add('test', new Agent(){
      @Override
      protected void init() {
        add(new MessageBehavior(){
          @Override
          void onReceive(Message msg) {
            println("Received something.. ${msg}")
            if (msg instanceof TestCompleteNtf){
              println("fjagepy test complete : ${msg.status?"PASSED":"FAILED"}")
              testResult.isComplete = true
              testResult.didPass = msg.status
              testResult.trace = msg.trace
            } else {
              println("No idea what to do with ${msg.class}")
            }
          }
        })
      }
    })
    def ret = 0
    def st = platform.currentTimeMillis();
    if (System.getProperty('manualPyTest') == null){
      println "Running automated tests."
      def proc = "python -m pytest".execute([], new File("gateways/python"))
      def sout = new StringBuilder()
      def serr = new StringBuilder()
      proc.consumeProcessOutput(sout, serr)
      proc.waitFor()
      ret = proc.exitValue()
      println "-------------------Logs from python-------------------------"
      println "STDOUT >> \n${sout.toString().trim()} \n"
      if (ret != 0){
        println "STDERR >> \n${serr.toString().trim()} \n"
      }
    }else{
      println "Waiting for user to run manual tests.."
      while (!testResult.isComplete){
        platform.delay(1000)
      }
      println "-------------------Logs from python-------------------------"
    }
    if (!testResult.didPass){
      println  "Python >> \n${testResult.trace.trim()}"
    }
    println "-------------------------------------------------------------"
    container.shutdown()
    platform.shutdown()
    assertTrue(ret == 0)
  }
}
