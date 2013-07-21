import org.arl.fjage.*
import org.arl.fjage.shell.*

boolean gui = System.properties.getProperty('fjage.gui') == 'true'

platform = new RealTimePlatform()
container = new Container(platform)
if (gui) shell = new ShellAgent(new SwingShell(), new GroovyScriptEngine())
else shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
container.add 'shell', shell
platform.start()
