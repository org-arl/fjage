/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell

import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.table.*
import org.arl.fjage.*
import groovy.swing.SwingBuilder
import java.awt.BorderLayout

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
  Font font = new Font('Courier', Font.PLAIN, 14)
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

  private class ListEntry {
    Object data
    OutputType type
    Color bg
    Color fg
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
    def model, component, fg
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
        break
      case OutputType.SENT:
        model = ntfLogModel
        component = ntfLog
        fg = sentFG
        break
      default:
        return
    }
    swing.edt {
      if (obj instanceof String) {
        obj.readLines().each {
          model.addElement(new ListEntry(data: it, type: type, fg: fg))
        }
      } else {
        model.addElement(new ListEntry(data: obj, type: type, fg: fg))
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
            scrollPane(constraints: BorderLayout.CENTER) {
              cmdLog = list(model: cmdLogModel, font: font, cellRenderer: new CmdListCellRenderer(), selectionMode: ListSelectionModel.SINGLE_SELECTION, valueChanged: {
                int ndx = cmdLog.selectedIndex
                if (ndx >= 0) {
                  ntfLog.clearSelection()
                  def msg = cmdLogModel[ndx].data
                  if (msg instanceof Message) updateDetailsModel(msg)
                  else updateDetailsModel(null)
                }
              })
            }
            cmd = textField(constraints: BorderLayout.SOUTH, font: font, actionPerformed: {
              if (!engine.isBusy()) {
                def s = cmd.text.trim()
                if (s.length() > 0) {
                  println("> $s", OutputType.INPUT)
                  if (history.size() == 0 || history.last() != s) history << s
                  historyNdx = -1
                  engine.exec(s, this)
                }
                cmd.text = ''
              }
            })
          }
          splitPane(orientation: JSplitPane.VERTICAL_SPLIT, dividerLocation: 384) {
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
              scrollPane(constraints: BorderLayout.CENTER) {
                ntfLog = list(model: ntfLogModel, font: font, cellRenderer: new CmdListCellRenderer(), selectionMode: ListSelectionModel.SINGLE_SELECTION, valueChanged: {
                  int ndx = ntfLog.selectedIndex
                  if (ndx >= 0) {
                    cmdLog.clearSelection()
                    updateDetailsModel(ntfLogModel[ndx].data)
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
      am.put('esc', { cmd.text = ''; historyNdx = -1 } as AbstractAction)
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

  private class CmdListCellRenderer extends DefaultListCellRenderer {
    Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      def bg = null
      def fg = null
      if (value instanceof ListEntry) {
        bg = value.bg
        fg = value.fg
        value = value.data?.toString()
      }
      if (value == null) value = ''
      else if (value == '') value = ' '
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      if (!isSelected) {
        if (bg) label.setBackground(bg)
        if (fg) label.setForeground(fg)
      }
      return label
    }
  }
  
}
