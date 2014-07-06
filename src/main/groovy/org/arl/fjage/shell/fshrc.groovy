package org.arl.fjage.shell
@groovy.transform.BaseScript org.arl.fjage.shell.BaseGroovyScript fjageGroovyScript

shellImport 'org.arl.fjage.*'

doc['help'] = 'help [topic] - provide help on a specified topic'
doc['ps'] = 'ps - list all the agents running on the local container'
doc['exit'] = 'exit - terminate the current shell session'
doc['who'] = 'who - display list of variables in workspace'
doc['run'] = '''\
run - run a Groovy script

Scripts are stored in a folder defined by the \'scripts\' variable in the
workspace. If no such variable is defined, they are in the current folder.

Examples:
  run \'myscript\'                   // run a script called myscript.groovy
  <myscript                        // alternative syntax for running myscript.groovy
'''
doc['println'] = '''\
println - display message on console

Usage:
  println output, [type]

Examples:
  println \'hello there!\'
  println \'that failed!\', org.arl.fjage.shell.OutputType.ERROR
'''
doc['delay'] = '''\
delay - delay execution by the specified number of milliseconds

Example:
  delay 1000                      // delay for 1000 ms
'''
doc['shutdown'] = 'shutdown - shutdown the local platform'
doc['logLevel'] = '''\
logLevel - set loglevel (optionally for a named logger)

Usage:
  logLevel [name],level

Examples:
  logLevel INFO                  // set loglevel to INFO
  loglevel \'org.arl\', ALL        // set loglevel for logger org.arl to ALL
'''
doc['subscribe'] = '''\
subscribe - subscribe to notifications from a named topic

Examples:
  subscribe topic(\'MyTopic\')     // subscribe to notifications from MyTopic
  subscribe agent(\'abc\')         // subscribe to notifications from agent abc
'''
doc['unsubscribe'] = '''\
unsubscribe - unsubscribe from notifications for a named topic

Examples:
  unsubscribe topic(\'MyTopic\')   // unsubscribe notifications from MyTopic
  unsubscribe agent(\'abc\')       // unsubscribe notifications from agent abc
'''
doc['shellImport'] = '''\
shellImport - add specified package/classes to list of imports

Examples:
  shellImport \'org.arl.fjage.*\'    // import package org.arl.fjage
  shellImport \'mypackage.MyClass\'  // import class mypackage.MyClass

At the shell prompt (but not in a script), shellImport can be abbreviated
to import. For example:
  import org.arl.fjage.*             // import package org.arl.fjage
'''
doc['agent'] = '''\
agent - return an agent id for the named agent

Usage:
  agent(name)

Example:
  a = agent(\'shell\')
'''
doc['agentForService'] = '''\
agentForService - find an agent id providing the specified service

Examples:
  a = agentForService Services.SHELL  // find agents providing shell service
'''
doc['agentsForService'] = '''\
agentsForService - get a list of all agent ids providing the specified service

Examples:
  a = agentsForService Services.SHELL // list all agents providing a service
'''
doc['send'] = '''\
send - send the given message

Examples:
  send new Message(agent(\'shell\'))    // send a message to agent shell
'''
doc['request'] = '''\
request - send the given request and wait for a response

Usage:
  request req,[timeout]

Examples:
  rsp = request req     // send req and wait for response for default timeout
  rsp = request req,100 // send req and wait for response for 100 ms
'''
doc['receive'] = '''\
receive - wait for a message

Usage:
  receive [filter], [timeout]
  receive [msg], [timeout]

Examples:
  msg = receive                     // get any message with default timeout
  msg = receive 100                 // get any message within 100 ms
  msg = receive req                 // get a response message for request req
  msg = receive A                   // get message that of class A
  msg = receive { it instanceof A } // get message that of class A
  msg = receive req                 // get message response to req
'''

////// GUI specific closures

if (!defined('gui')) return

doc['guiAddMenu'] = '''\
guiAddMenu - add menu item to GUI shell

Usage:
  guiAddMenu title, subtitle, task, [options]

Examples:
  guiAddMenu 'Custom', 'Do it!', { println 'Just do it!' }
  guiAddMenu 'Custom', 'Copy', { copy() }, [acc: 'meta C']  // accelerator
  guiAddMenu 'Custom', 'Enable', null, [checked: true]      // checkbox
'''

guiAddMenu = { String title, String subtitle, Closure task, Map opt = [:] ->
  def swing = new groovy.swing.SwingBuilder()
  def menubar = gui.menubar
  def menu = null
  for (int i = 0; i < menubar.menuCount; i++) {
    def m = menubar.getMenu(i)
    if (m.text == title) {
      menu = m
      break
    }
  }
  if (!menu) {
    menu = swing.menu(text: title)
    swing.edt {
      menubar.add(menu)
    }
  }
  for (int i = 0; i < menu.itemCount; i++) {
    def m = menu.getItem(i)
    if (m.text == subtitle) {
      menu.remove(i)
      break
    }
  }
  def menuitem
  if (opt.checked instanceof Boolean) menuitem = swing.checkBoxMenuItem(text: subtitle, state: opt.checked)
  else menuitem = swing.menuItem(text: subtitle)
  if (task) menuitem.actionPerformed = {
    try {
      task(it.source)
    } catch (Exception ex) {
      println(ex.toString(), org.arl.fjage.shell.OutputType.ERROR)
    }
  }
  if (opt.acc) menuitem.accelerator = javax.swing.KeyStroke.getKeyStroke(opt.acc)
  swing.edt {
    menu.add(menuitem)
    menubar.revalidate()
  }
  return menuitem
}

doc['guiGetMenu'] = '''\
guiGetMenu - find menu item in GUI shell

Usage:
  item = getGuiMenu(title, [subtitle])

If only title is specified, returns JMenu. If subtitle is specified,
returns JMenuItem.

Example:
  getGuiMenu('Custom', 'Do it!').enabled = false
'''

guiGetMenu = { String title, String subtitle = null ->
  def swing = new groovy.swing.SwingBuilder()
  def menubar = gui.menubar
  def menu = null
  for (int i = 0; i < menubar.menuCount; i++) {
    def m = menubar.getMenu(i)
    if (m.text == title) {
      menu = m
      break
    }
  }
  if (!menu) return null
  if (!subtitle) return menu
  for (int i = 0; i < menu.itemCount; i++) {
    def m = menu.getItem(i)
    if (m.text == subtitle) return m
  }
  return null
}

doc['guiRemoveMenu'] = '''\
guiRemoveMenu - removes menu from from GUI shell

Usage:
  guiRemoveMenu title, subtitle

Examples:
  guiRemoveMenu 'Custom', 'Do it!'
'''

guiRemoveMenu = { String title, String subtitle ->
  def swing = new groovy.swing.SwingBuilder()
  def menubar = gui.menubar
  def menu = null
  for (int i = 0; i < menubar.menuCount; i++) {
    def m = menubar.getMenu(i)
    if (m.text == title) {
      menu = m
      break
    }
  }
  if (!menu) return false
  for (int i = 0; i < menu.itemCount; i++) {
    def m = menu.getItem(i)
    if (m.text == subtitle) {
      swing.edt {
        menu.remove(i)
        menubar.revalidate()
      }
      return true
    }
  }
  return false
}

doc['guiAddPanel'] = '''\
guiAddPanel - adds a details panel to GUI shell

Usage:
  guiAddPanel title, panel

panel must be a AWT or Swing component.

Example:
  guiAddPanel 'Demo', new javax.swing.JPanel()  // add panel called 'Demo'
'''

guiAddPanel = { String title, java.awt.Component panel ->
  def swing = new groovy.swing.SwingBuilder()
  def tabs = gui.details
  swing.edt {
    tabs.add title, panel
    tabs.selectedComponent = panel
    tabs.revalidate()
  }
}

doc['guiGetPanel'] = '''\
guiGetPanel - gets and optionally activates a GUI shell details panel

Usage:
  panel = guiGetPanel title, [select]

If select is true, the panel is activated.

Example:
  guiGetPanel 'Demo', true                  // activate panel called 'Demo'
'''

guiGetPanel = { String title, boolean select = false ->
  def swing = new groovy.swing.SwingBuilder()
  def tabs = gui.details
  for (int i = 0; i < tabs.tabCount; i++)
    if (tabs.getTitleAt(i) == title) {
      if (select)
        swing.edt {
          tabs.selectedIndex = i
          tabs.revalidate()
        }
      return tabs.getComponentAt(i)
    }
  return null
}

doc['guiRemovePanel'] = '''\
guiRemovePanel - remove a details panel from the GUI shell

Usage:
  guiRemovePanel title

Example:
  guiRemovePanel 'Demo'                     // remove panel called 'Demo'
'''

guiRemovePanel = { String title ->
  def swing = new groovy.swing.SwingBuilder()
  def tabs = gui.details
  for (int i = 0; i < tabs.tabCount; i++)
    if (tabs.getTitleAt(i) == title) {
      swing.edt {
        tabs.remove(i)
        tabs.revalidate()
      }
      return true
    }
  return false
}
