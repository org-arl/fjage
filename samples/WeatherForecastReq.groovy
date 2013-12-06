// fjage tutorials - Weather Request Message
// http://github.com/org-arl/fjage
// http://org-arl.github.io/fjage/doc/html/messages.html
// By Dr. Mandar Chitre
// Acoustic Research Laboratory
// www.arl.nus.edu.sg

import org.arl.fjage.*

class WeatherForecastReq extends Message {
  WeatherForecastReq() {
    super(Performative.REQUEST)
  }
  String city, country
}
