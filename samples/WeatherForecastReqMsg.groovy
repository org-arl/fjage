// fjage tutorials - Weather Request Message
// http://github.com/org-arl/fjage
// http://org-arl.github.io/fjage/doc/html/messages.html
// By Dr. Mandar Chitre
// Acoustic Research Laboratory
// www.arl.nus.edu.sg

import org.arl.fjage.*

class WeatherForecastReqMsg extends Message {
  WeatherForecastReqMsg() {
    super(Performative.REQUEST)
  }
  String city, country
}
