package org.arl.fjage.test

import org.arl.fjage.*
import org.arl.fjage.remote.MasterContainer
import org.junit.Test

import static org.junit.Assert.assertTrue

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
	    def ret = 0
		if (System.getProperty('manualPyTest') == null){
			println "Running automated tests."
			def proc = "python src/test/groovy/org/arl/fjage/test/BasicTests.py".execute()
			def sout = new StringBuilder(), serr = new StringBuilder()
			proc.consumeProcessOutput(sout, serr)
			proc.waitFor()
			ret = proc.exitValue()
			println "Python : out = $sout \n err = $serr \n ret = $ret \n testStatus = $testStatus"
		}else{
			println "waiting for user to run manual tests"
			while (!testStatus){
				platform.delay(1000)
			}
		}
		container.shutdown()
		platform.shutdown()
		assertTrue(ret == 0 && testStatus)
  	}

}
