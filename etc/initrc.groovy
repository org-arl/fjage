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
if (devname == null) container = new MasterContainer(platform, port)
else container = new MasterContainer(platform, port, devname, baud, 'N81')
if (web) {
  WebServer.getInstance(8080).add("/shell", "/org/arl/fjage/web/shell")
  Connector conn = new WebSocketConnector(8080, "/shell/ws")
  shell = new ShellAgent(new ConsoleShell(conn), new GroovyScriptEngine())
} else {
  shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
}
shell.addInitrc("cls://org.arl.fjage.shell.fshrc");
container.add 'shell', shell
platform.start()
