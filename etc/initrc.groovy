import org.arl.fjage.*
import org.arl.fjage.shell.*

GroovyExtensions.enable()

platform = new RealTimePlatform()
container = new Container(platform)
shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
container.add 'shell', shell
platform.start()
