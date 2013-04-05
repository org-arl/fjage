import org.arl.fjage.*
import org.arl.fjage.shell.*

GroovyAgentExtensions.enable()

platform = new RealTimePlatform()
container = new Container(platform)
shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
shell.setInitrc 'etc/fjageshrc.groovy'
container.add 'shell', shell
platform.start()
