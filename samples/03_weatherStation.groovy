// fjage tutorials - Weather Station Agent
// http://github.com/org-arl/fjage
// http://org-arl.github.io/fjage/doc/html/messages.html
// By Dr. Mandar Chitre
// Acoustic Research Laboratory
// www.arl.nus.edu.sg
// Agent will send weather data to any requesting users

import org.arl.fjage.*

// response message
class WeatherForecast extends org.arl.fjage.Message {

  WeatherForecast(Message req) {
    super(req, Performative.INFORM)   // create a response with inReplyTo = req
    city = req.city
    country = req.country
  }
  String city, country
  float minTemp, maxTemp, probRain
}

class MyWeatherStation extends Agent {

  void init() {
    add new MessageBehavior(WeatherForecastReqMsg, { req ->
      log.info "Weather forecast request for ${req.city}, ${req.country}"
      def rsp = new WeatherForecast(req)
      rsp.minTemp = 10
      rsp.maxTemp = 25
      rsp.probRain = 0.25
      // sending response with data
      send rsp
    })
  }
}

container.add 'weatherStation01', new MyWeatherStation()