// fjage tutorials - Ticker Behavior
// http://github.com/org-arl/fjage
// http://org-arl.github.io/fjage/doc/html/behaviors.html
// By Dr. Mandar Chitre
// Acoustic Research Laboratory
// www.arl.nus.edu.sg
// Agent to show the use of ticker behavior

import org.arl.fjage.*

class MyAgent extends Agent {
	int n = 0
	void init () {
		// wakes up every 1s and do something
		add new TickerBehavior(1000, {

			// log every 1s
			println "${n}. tick"
			agent.n++
			
			// stop after 20 ticks
			if (n >= 20) stop();
		})
	}

	void shutdown () {
		log.info "Ticker Agent shutting down!!!"
	}
}

container.add 'ticker', new MyAgent()
