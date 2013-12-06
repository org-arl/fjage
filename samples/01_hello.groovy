// fjage tutorials - Hello World Agent
// http://github.com/org-arl/fjage
// http://org-arl.github.io/fjage/doc/html/quickstart.html
// By Dr. Mandar Chitre
// Acoustic Research Laboratory
// www.arl.nus.edu.sg
// Agent will log "Hello World" in the log file located in logs directory

import org.arl.fjage.*

class HelloWorldAgent extends Agent {
  void init() {
    add new OneShotBehavior({
      println 'Hello World!!!'
      //log.info 'Hello World!!!'
    })
  }
}

container.add 'hello', new HelloWorldAgent()
