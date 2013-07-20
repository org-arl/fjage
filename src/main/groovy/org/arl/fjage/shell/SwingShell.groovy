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
import org.arl.fjage.*
import groovy.swing.SwingBuilder
import java.awt.BorderLayout

/**
 * Swing GUI command shell.
 */
class SwingShell implements Shell {

  Color normalFG = Color.black
  Color responseFG = new Color(128, 96, 0)
  Color errorFG = Color.red
  Color notificationFG = Color.blue
  Color markerBG = Color.black
  Font font = new Font('Arial', Font.PLAIN, 14)

  private SwingBuilder swing = new SwingBuilder()
  private ScriptEngine engine
  private def cmd, details
  private def cmdLog, ntfLog
  private DefaultListModel cmdLogModel = new DefaultListModel();
  private DefaultListModel ntfLogModel = new DefaultListModel();
  private java.util.List<String> history = []
  private int historyNdx = -1

  private class ListEntry {
    Object data
    Color bg
    Color fg
  }

  void start(ScriptEngine engine) {
    this.engine = engine
    createGUI()
  }

  void println(def obj, OutputType type) {
    def model, component, fg
    switch (type) {
      case OutputType.NORMAL:
        model = cmdLogModel
        component = cmdLog
        fg = normalFG
        break
      case OutputType.RESPONSE:
        model = cmdLogModel
        component = cmdLog
        fg = responseFG
        break
      case OutputType.ERROR:
        model = cmdLogModel
        component = cmdLog
        fg = errorFG
        break
      case OutputType.NOTIFICATION:
        model = ntfLogModel
        component = ntfLog
        fg = notificationFG
        break
    }
    swing.edt {
      if (obj instanceof String) {
        obj.readLines().each {
          model.addElement(new ListEntry(data: it, fg: fg))
        }
      } else {
        model.addElement(new ListEntry(data: obj, fg: fg))
      }
      component.clearSelection()
      component.ensureIndexIsVisible(model.size()-1)
    }
  }

  private void cls() {
    swing.edt {
      cmdLogModel.clear()
      ntfLogModel.clear()
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

  private String expose(def obj) {
    def s = obj?.properties.findAll { it.key != 'class' }.collect { "${it.key}: ${it.value}" }
    s.join('\n')
  }

  private void createGUI() {
    swing.edt {
      frame(title:'UnetGUI', defaultCloseOperation: JFrame.DISPOSE_ON_CLOSE, size: [1024, 768], show: true, locationRelativeTo: null, windowOpened: { cmd.requestFocus() }) {
        lookAndFeel('system')
        menuBar() {
          menu(text: 'File', mnemonic: 'F') {
            menuItem(text: 'Exit', accelerator: KeyStroke.getKeyStroke('meta Q'), actionPerformed: { System.exit(0) })
          }
          menu(text: 'Edit', mnemonic: 'E') {
            menuItem(text: 'Clear', accelerator: KeyStroke.getKeyStroke('meta K') , actionPerformed: { cls() })
            menuItem(text: 'Mark', accelerator: KeyStroke.getKeyStroke('meta M'), actionPerformed: { mark() })
          }
        }
        splitPane {
          panel(constraints: 'left', preferredSize: [512, -1]) {
            borderLayout()
            label(text: 'Commands', constraints: BorderLayout.NORTH)
            scrollPane(constraints: BorderLayout.CENTER) {
              cmdLog = list(model: cmdLogModel, font: font, cellRenderer: new CmdListCellRenderer())
            }
            cmd = textField(constraints: BorderLayout.SOUTH, font: font, actionPerformed: {
              def s = cmd.text.trim()
              if (s.length() > 0) {
                println("> $s", OutputType.NORMAL)
                if (history.size() == 0 || history.last() != s) history << s
                historyNdx = -1
                engine.exec(s, this)
              }
              cmd.text = ''
            })
          }
          splitPane(orientation: JSplitPane.VERTICAL_SPLIT, dividerLocation: 384) {
            panel(constraints: 'top') {
              borderLayout()
              label(text: 'Notification details', constraints: BorderLayout.NORTH)
              scrollPane(constraints: BorderLayout.CENTER) {
                details = textArea(editable: false)
              }
            }
            panel(constraints: 'bottom') {
              borderLayout()
              label(text: 'Notifications', constraints: BorderLayout.NORTH)
              scrollPane(constraints: BorderLayout.CENTER) {
                ntfLog = list(model: ntfLogModel, font: font, cellRenderer: new CmdListCellRenderer(), valueChanged: {
                  int ndx = ntfLog.selectedIndex
                  if (ndx < 0) details.text = ''
                  else details.text = expose(ntfLogModel[ndx].data)
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
          } else if (code == KeyEvent.VK_DOWN) {
            if (historyNdx < 0) return
            if (++historyNdx < history.size()) cmd.text = history[historyNdx]
            else {
              cmd.text = ''
              historyNdx = -1
            }
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
        value = value.data?.toString()?:''
      }
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      if (!isSelected) {
        if (bg) label.setBackground(bg)
        if (fg) label.setForeground(fg)
      }
      return label
    }
  }
  
}
