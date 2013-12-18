// fjage tutorials - Weather Station User Agent
// http://github.com/org-arl/fjage
// http://org-arl.github.io/fjage/doc/html/messages.html
// By Dr. Mandar Chitre
// Acoustic Research Laboratory
// www.arl.nus.edu.sg
// Agent will send request for weather data from a weather station

import org.arl.fjage.*

class MyWeatherRequest extends Agent {
	void init() {
		// request weather information
		add new OneShotBehavior ({
			def req = new WeatherForecastReqMsg(city: 'London', country: 'UK', recipient: agent('weatherStation01'))
			def rsp = request req, 1000         // 1000 ms timeout for reply
			// display the information
			println "The lowest temperature today is ${rsp?rsp.minTemp:'unknown'}"
		})
	}
}

container.add 'weatherRequest01', new MyWeatherRequest()