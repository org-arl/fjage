import org.arl.fjage.*
import org.arl.fjage.remote.*
import org.arl.fjage.shell.*
import org.arl.fjage.connectors.*
import java.nio.file.Path

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
def history = Path.of(".fjage-shell-history")
Connector conn = null
if (web) {
  try {
    WebServer.getInstance(8080).addStatic("/", "/org/arl/fjage/web")
    conn = new WebSocketHubConnector(8080, "/shell/ws")
    container.openWebSocketServer(8080, "/ws")
  } catch (UncheckedIOException ex) {
    // fall back to the console shell if the web server is unavailable
    println "fjåge web shell unavailable: ${ex.message}"
    web = false
  }
}
shell = new ShellAgent(conn ? new ConsoleShell(conn, history) : new ConsoleShell(history), new GroovyScriptEngine())
container.add 'shell', shell
platform.start()

String url = "http://localhost:8080/shell/index.html"
if (web) println 'fjåge web shell: ' + url
