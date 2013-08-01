/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell

import java.awt.*
import java.awt.event.*
import java.awt.datatransfer.*
import javax.swing.*
import javax.swing.table.*
import org.arl.fjage.*
import groovy.swing.SwingBuilder
import java.awt.BorderLayout
import org.apache.commons.lang3.StringEscapeUtils

/**
 * Swing GUI command shell.
 */
class SwingShell implements Shell {

  static def location = [0, 0]

  Color inputFG = Color.black
  Color outputFG = new Color(128, 96, 0)
  Color errorFG = Color.red
  Color receivedFG = Color.blue
  Color sentFG = Color.black
  Color markerBG = Color.black
  Color busyBG = Color.pink
  Color idleBG = Color.white
  Color selectedBG = new Color(255, 255, 192)
  Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12)
  boolean shutdownOnExit = true

  private JFrame window
  private name
  private SwingBuilder swing = new SwingBuilder()
  private ScriptEngine engine
  private def cmd, details, mbar
  private def cmdLog, ntfLog
  private DefaultListModel cmdLogModel = new DefaultListModel()
  private DefaultListModel ntfLogModel = new DefaultListModel()
  private def detailsModel
  private java.util.List<String> history = []
  private int historyNdx = -1
  private def gui = [:]
  private def timer

  private class ListEntry {
    Object data
    OutputType type
    Color bg
    Color fg
    String prefix
  }

  SwingShell() {
    name = 'Command Shell'
  }

  SwingShell(String name) {
    this.name = name
  }

  void start(ScriptEngine engine) {
    this.engine = engine
    createGUI()
    gui.menubar = mbar
    gui.details = details
    gui.cmd = cmd
    gui.cmdLog = cmdLog
    gui.ntfLog = ntfLog
    engine.setVariable('gui', gui)
  }

  void shutdown() {
    window.dispose()
  }

  void println(def obj, OutputType type) {
    def model, component, fg, prefix
    switch (type) {
      case OutputType.INPUT:
        model = cmdLogModel
        component = cmdLog
        fg = inputFG
        break
      case OutputType.OUTPUT:
        model = cmdLogModel
        component = cmdLog
        fg = outputFG
        break
      case OutputType.ERROR:
        model = cmdLogModel
        component = cmdLog
        fg = errorFG
        break
      case OutputType.RECEIVED:
        model = ntfLogModel
        component = ntfLog
        fg = receivedFG
        if (obj instanceof Message) prefix = "${obj.sender} > "
        break
      case OutputType.SENT:
        model = ntfLogModel
        component = ntfLog
        fg = sentFG
        if (obj instanceof Message) prefix = "${obj.recepient} < "
        break
      default:
        return
    }
    swing.edt {
      if (obj instanceof String) {
        obj.readLines().each {
          model.addElement(new ListEntry(data: it, type: type, fg: fg, prefix: prefix))
        }
      } else {
        model.addElement(new ListEntry(data: obj, type: type, fg: fg, prefix: prefix))
      }
      if (type != OutputType.RECEIVED && type != OutputType.SENT) component.clearSelection()
      component.ensureIndexIsVisible(model.size()-1)
    }
  }

  private void cls() {
    swing.edt {
      cmdLogModel.clear()
      ntfLogModel.clear()
      detailsModel.rowsModel.value.clear()
      detailsModel.fireTableDataChanged()
    }
  }

  private void mark() {
    swing.edt {
      cmdLogModel.addElement(new ListEntry(bg: markerBG))
      ntfLogModel.addElement(new ListEntry(bg: markerBG))
      cmdLog.ensureIndexIsVisible(cmdLogModel.size()-1)
      ntfLog.ensureIndexIsVisible(cmdLogModel.size()-1)
    }
  }

  private String bytesToHexString(byte[] data) {
    int n = data.length
    if (n > 65) n = 65
    StringBuffer sb = new StringBuffer(2*n)
    for (int i = 0; i < n; i++) {
      if (i > 0 && i%4 == 0) sb.append(' ')
      int v = data[i] & 0xff
      if (v < 16) sb.append('0')
      sb.append(Integer.toHexString(v))
    }
    return sb.toString()
  }

  private void updateDetailsModel(def obj) {
    detailsModel.rowsModel.value.clear()
    if (obj) {
      obj.properties.findAll { it.key != 'class' }.each {
        String s = it.value as String
        if (it.value instanceof byte[]) s = bytesToHexString(it.value)
        if (s != null) {
          if (s.length() > 128) s = s.substring(0, 128) + '...'
          detailsModel.rowsModel.value.add([key: it.key, value: s])
        }
      }
      if (obj instanceof Map) {     // for GenericMessage
        obj.each {
          String s = it.value as String
          if (it.value instanceof byte[]) s = bytesToHexString(it.value)
          if (s != null) {
            if (s.length() > 128) s = s.substring(0, 128) + '...'
            detailsModel.rowsModel.value.add([key: it.key, value: s])
          }
        }
      }
    }
    detailsModel.fireTableDataChanged()
  }

  private void redrawList(def model) {
    // hack, but seems to be the only way to get a JList to repaint completely
    model.addElement('')
    model.removeElement(model.lastElement())
  }

  private void exit() {
    if (shutdownOnExit) engine.exec('shutdown', null)
  }

  private void createGUI() {
    location[0] += 100
    location[1] += 100
    swing.edt {
      window = frame(title: name, /*iconImage: ,*/ defaultCloseOperation: JFrame.DISPOSE_ON_CLOSE, size: [1024, 768], show: true, location: location, windowOpened: { cmd.requestFocus() }, windowClosed: { exit() }) {
        lookAndFeel('system')
        mbar = menuBar() {
          menu(text: 'File', mnemonic: 'F') {
            menuItem(text: 'Exit', accelerator: KeyStroke.getKeyStroke('meta Q'), actionPerformed: { window.dispose() })
          }
          menu(text: 'Edit', mnemonic: 'E') {
            menuItem(text: 'Abort operation', accelerator: KeyStroke.getKeyStroke('ctrl C'), actionPerformed: { if (engine.isBusy()) engine.abort() })
            menuItem(text: 'Clear workspace', accelerator: KeyStroke.getKeyStroke('meta K') , actionPerformed: { cls() })
            menuItem(text: 'Insert marker', accelerator: KeyStroke.getKeyStroke('meta M'), actionPerformed: { mark() })
            menuItem(text: 'Copy to workspace', accelerator: KeyStroke.getKeyStroke('ctrl W'), actionPerformed: {
              int ndx = ntfLog.selectedIndex
              if (ndx >= 0) engine.setVariable('ntf', ntfLogModel[ndx].data)
              ndx = cmdLog.selectedIndex
              if (ndx >= 0) {
                def msg = cmdLogModel[ndx].data
                if (msg instanceof Message) engine.setVariable('ans', msg)
              }
            })
          }
        }
        splitPane {
          panel(constraints: 'left', preferredSize: [512, -1]) {
            borderLayout()
            label(text: 'Commands', constraints: BorderLayout.NORTH)
            scrollPane(constraints: BorderLayout.CENTER, componentResized: { redrawList(cmdLogModel) }) {
              cmdLog = list(model: cmdLogModel, font: font, cellRenderer: new CmdListCellRenderer(), valueChanged: {
                int ndx = cmdLog.selectedIndex
                if (ndx >= 0) {
                  ntfLog.clearSelection()
                  def msg = cmdLogModel[ndx].data
                  if (msg instanceof Message) updateDetailsModel(msg)
                  else updateDetailsModel(null)
                }
              }, transferHandler: new TransferHandler() {
                void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
                  def ndx = cmdLog.selectedIndices
                  def text = null
                  ndx.each {
                    String s = cmdLogModel[it].data as String
                    if (cmdLogModel[it].type == OutputType.INPUT) s = s.substring(2)
                    if (text) text += "\n$s"
                    else text = s
                  }
                  if (text) clipboard.setContents(new StringSelection(text), null)
                }
              })
            }
            cmd = textField(constraints: BorderLayout.SOUTH, background: idleBG, font: font, actionPerformed: {
              if (!engine.isBusy()) {
                def s = cmd.text.trim()
                if (nested(s)) return
                if (s.length() > 0) {
                  println("> $s", OutputType.INPUT)
                  if (history.size() == 0 || history.last() != s) history << s
                  historyNdx = -1
                  engine.exec(s, this)
                }
                cmd.text = ''
                if (timer == null) {
                  timer = new javax.swing.Timer(100, {
                    if (engine.isBusy()) cmd.background = busyBG
                    else {
                      cmd.background = idleBG
                      timer.stop()
                      timer = null
                    }
                  } as ActionListener)
                  timer.start()
                }
              }
            })
          }
          splitPane(orientation: JSplitPane.VERTICAL_SPLIT, dividerLocation: 256) {
            details = tabbedPane(constraints: 'top') {
              panel(name: 'Details') {
                borderLayout()
                scrollPane(constraints: BorderLayout.CENTER) {
                  table {
                    detailsModel = tableModel {
                      propertyColumn(header: 'Property', propertyName: 'key', preferredWidth: 100, editable: false)
                      propertyColumn(header: 'Value', propertyName: 'value', preferredWidth: 300, editable: false)
                    }
                  }
                }
              }
            }
            panel(constraints: 'bottom') {
              borderLayout()
              label(text: 'Messages', constraints: BorderLayout.NORTH)
              scrollPane(constraints: BorderLayout.CENTER, componentResized: { redrawList(ntfLogModel) }) {
                ntfLog = list(model: ntfLogModel, font: font, cellRenderer: new CmdListCellRenderer(), valueChanged: {
                  int ndx = ntfLog.selectedIndex
                  if (ndx >= 0) {
                    cmdLog.clearSelection()
                    updateDetailsModel(ntfLogModel[ndx].data)
                  }
                }, transferHandler: new TransferHandler() {
                void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
                  def ndx = ntfLog.selectedIndices
                  def text = null
                  ndx.each {
                    String s = ntfLogModel[it].data as String
                    if (text) text += "\n$s"
                    else text = s
                  }
                  if (text) clipboard.setContents(new StringSelection(text), null)
                }
              })
              }
            }
          }
        }
      }
      def im = cmd.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
      im.put(KeyStroke.getKeyStroke('ESCAPE'), 'esc')
      im.put(KeyStroke.getKeyStroke('ctrl L'), 'cls')
      def am = cmd.actionMap
      am.put('esc', { cmd.selectAll() } as AbstractAction)
      am.put('cls', { cls() } as AbstractAction)
      cmd.addKeyListener(new KeyListener() {
        void keyTyped(KeyEvent e) { }
        void keyReleased(KeyEvent e) { }
        void keyPressed(KeyEvent e) {
          int code = e.getKeyCode()
          if (code == KeyEvent.VK_UP) {
            if (historyNdx == 0) return
            if (historyNdx < 0) historyNdx = history.size()
            if (--historyNdx >= 0) cmd.text = history[historyNdx]
            cmd.caretPosition = cmd.text.length()
            e.consume()
          } else if (code == KeyEvent.VK_DOWN) {
            if (historyNdx < 0) return
            if (++historyNdx < history.size()) cmd.text = history[historyNdx]
            else {
              cmd.text = ''
              historyNdx = -1
            }
            cmd.caretPosition = cmd.text.length()
            e.consume()
          }
        }
      })
    }
  }

  private boolean nested(String s) {
    int nest1 = 0
    int nest2 = 0
    int nest3 = 0
    int quote1 = 0
    int quote2 = 0
    s.length().times { i ->
      switch (s.charAt(i)) {
        case '{':
          nest1++
          break
        case '}':
          if (nest1 > 0) nest1--
          break
        case '(':
          nest2++
          break
        case ')':
          if (nest2 > 0) nest2--
          break
        case '[':
          nest3++
          break
        case ']':
          if (nest3 > 0) nest3--
          break
        case '\'':
          quote1 = 1-quote1
          break
        case '"':
          quote2 = 1-quote2
          break
      }
    }
    return nest1+nest2+nest3+quote1+quote2 > 0
  }

  private class CmdListCellRenderer extends DefaultListCellRenderer {
    Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      def bg = null
      def fg = null
      if (value instanceof ListEntry) {
        bg = value.bg
        fg = value.fg
        if (value.prefix) value = value.prefix + value.data?.toString()
        else value = value.data?.toString()
      }
      if (value == null) value = ''
      else if (value == '') value = ' '
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      String s = StringEscapeUtils.escapeHtml4(label.text)
      label.text = "<html><body style='width: ${0.8*list.parent.width}px;'>$s</body></html>";
      if (isSelected) {
        label.setBackground(selectedBG)
        if (fg) label.setForeground(fg)
      } else {
        if (bg) label.setBackground(bg)
        if (fg) label.setForeground(fg)
      }
      return label
    }
  }
  
}
