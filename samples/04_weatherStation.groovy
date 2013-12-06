// fjage tutorials - Weather Station Agent
// http://github.com/org-arl/fjage
// http://org-arl.github.io/fjage/doc/html/messages.html
// By Dr. Mandar Chitre
// Acoustic Research Laboratory
// www.arl.nus.edu.sg
// Agent will send updated weather data to any requesting
// users using generic messages

import org.arl.fjage.*

class MyWeatherStation extends Agent {
  int minTemp = 10
  int maxTemp = 25
  int probRain = 0.25

  void init() {
    add new OneShotBehavior ({
      log.info "Weather Station up and waiting for requests"
    })

    add new MessageBehavior({ msg ->
      //check for message fields
      if (msg.performative == Performative.REQUEST && msg.type == 'WeatherForecast') {
        log.info "Weather forecast request for ${msg.city}, ${msg.country}"

        //create generic response
        def rsp = new GenericMessage(msg, Performative.INFORM)

        //fill in the data
        rsp.minTemp = agent.minTemp--
        rsp.maxTemp = agent.maxTemp++
        rsp.probRain = agent.probRain

        //update temperature
        if (agent.minTemp <= 0) agent.minTemp = 10
        if (agent.maxTemp >= 30) agent.maxTemp = 25

        //send response
        send rsp
      }
    })
  }
}

container.add 'weatherStation02', new MyWeatherStation()
