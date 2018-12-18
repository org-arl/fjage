import org.arl.fjage.*
import org.arl.fjage.remote.*
import org.arl.fjage.shell.*

platform = new RealTimePlatform()
String devname =  System.properties.getProperty('devname')
if (devname != null) {
  int baud
  try {
    baud =  Integer.parseInt(System.properties.getProperty('baud'))
  } catch (Exception ex) {
    baud = 9600
  }
  println "Connecting to $devname@$baud..."
  container = new SlaveContainer(platform, devname, baud, null)
} else {
  String hostname =  System.properties.getProperty('hostname')
  if (hostname == null || hostname.length() == 0) hostname = 'localhost'
  int port
  try {
    port =  Integer.parseInt(System.properties.getProperty('port'))
  } catch (Exception ex) {
    port = 5081
  }
  println "Connecting to $hostname:$port..."
  container = new SlaveContainer(platform, hostname, port)
}
shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
shell.addInitrc("cls://org.arl.fjage.shell.fshrc");
container.add 'rshell', shell
platform.start()
