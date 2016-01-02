import org.arl.fjage.*
import org.arl.fjage.remote.*
import org.arl.fjage.shell.*

String hostname =  System.properties.getProperty('hostname')
if (hostname == null || hostname.length() == 0) hostname = 'localhost'
int port
try {
  port =  Integer.parseInt(System.properties.getProperty('port'))
} catch (Exception ex) {
  port = 5081
}
println "Connecting to $hostname:$port..."
platform = new RealTimePlatform()
container = new SlaveContainer(platform, hostname, port)
shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
container.add 'rshell', shell
platform.start()
