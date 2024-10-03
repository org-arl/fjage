import org.arl.fjage.*
import org.arl.fjage.remote.*
import org.arl.fjage.shell.*
import org.arl.fjage.connectors.*

boolean web = System.properties.getProperty('fjage.web') == 'true'
int port = 5081
try {
  port =  Integer.parseInt(System.properties.getProperty('fjage.port'))
} catch (Exception ex) {
  // do nothing
}
String devname = System.properties.getProperty('fjage.devname')
int baud = 9600
if (devname != null) {
  try {
    baud =  Integer.parseInt(System.properties.getProperty('fjage.baud'))
  } catch (Exception ex) {
    // do nothing
  }
}

platform = new RealTimePlatform()
container = new MasterContainer(platform, port)
if (devname != null)  container.addConnector(new SerialPortConnector(devname, baud, 'N81'))
if (web) {
  WebServer webserver = WebServer.getInstance(8080);
  webserver.add("/", "/org/arl/fjage/web")
  Connector conn = new WebSocketHubConnector(8080, "/shell/ws")
  shell = new ShellAgent(new ConsoleShell(conn), new GroovyScriptEngine())
  webserver.addWebSocketConnector('/ws', container)
//  container.addConnector(new WebSocketConnector(8080, "/ws", true))
} else {
  shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
}
container.add 'shell', shell
platform.start()

String url = "http://localhost:8080/shell/index.html"
if (web) println 'fjåge web shell: ' + url
