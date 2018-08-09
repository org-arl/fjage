import org.arl.fjage.*
import org.arl.fjage.remote.*
import org.arl.fjage.shell.*

boolean gui = System.properties.getProperty('fjage.gui') == 'true'
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
if (gui) shell = new ShellAgent(new SwingShell(), new GroovyScriptEngine())
else shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
container.add 'shell', shell
platform.start()
