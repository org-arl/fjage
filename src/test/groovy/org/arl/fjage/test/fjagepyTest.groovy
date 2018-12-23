package org.arl.fjage.test

import org.arl.fjage.*
import org.arl.fjage.remote.*
import org.arl.fjage.remote.MasterContainer
import org.junit.Test
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse


class fjagepyTest {

	@Test
  	void fjagePythonTest() {
  		def testStatus = false
    	def platform = new RealTimePlatform()
    	def container = new MasterContainer(platform, 5081)
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
	    def ret = 0;
	    println "Running automated tests.";
	    def proc = "python3 src/test/groovy/org/arl/fjage/test/BasicTests.py".execute();
	    def sout = new StringBuilder(), serr = new StringBuilder()
	    proc.consumeProcessOutput(sout, serr)
	    proc.waitFor();
	    ret = proc.exitValue();
	    println "NPM : out> $sout err> $serr \n ret = $ret \n testStatus = $testStatus"
	    platform.shutdown()
    	assertTrue(ret == 0 && testStatus)	
  	}
}