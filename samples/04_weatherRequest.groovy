// fjage tutorials - Weather Station User Agent
// http://github.com/org-arl/fjage
// http://org-arl.github.io/fjage/doc/html/messages.html
// By Dr. Mandar Chitre
// Acoustic Research Laboratory
// www.arl.nus.edu.sg
// Agent will send request for weather data periodically
// from a weather station using a generic message format

import org.arl.fjage.*

class MyWeatherRequest extends Agent {
	void init() {
		// request weather information every 5 seconds
		add new TickerBehavior (5000, {
			// create a generic message
			def req = new GenericMessage(agent('weatherStation02'), Performative.REQUEST)

			// add in details
			req.type = 'WeatherForecast'
			req.city = 'London'
			req.country = 'UK'

			// send request
			def rsp = request req, 1000         // 1000 ms timeout for reply
			println "Lowest temperature is ${rsp?rsp.minTemp:'unknown'} deg"
			println "Highest temperature is ${rsp?rsp.maxTemp:'unknown'} deg"
		})
	}
}

container.add 'weatherRequest02', new MyWeatherRequest()