/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import org.arl.fjage.*;

/**
 * Shell agent runs in a container and allows execution of shell commands and scripts.
 */
public class ShellAgent extends Agent {

  protected Shell shell = null;
  protected ScriptEngine engine = null;

  public ShellAgent(Shell shell, ScriptEngine engine) {
    this.shell = shell;
    this.engine = engine;
    if (shell != null) shell.init();
    if (engine != null) engine.bind(shell);
  }

  @Override
  public void init() {
    log.info("Agent "+getName()+" init");
    register(Services.SHELL);
    if (shell != null) {
      add(new CyclicBehavior() {
        String s = null;
        @Override
        public void action() {
          String prompt = null;
          if (engine != null) prompt = engine.getPrompt();
          s = shell.readLine(prompt, s);
          if (s != null) {
            s = s.trim();
            log.info("> "+s);
            if (engine.isComplete(s)) {
              if (s.equals("%shutdown")) {
                getPlatform().shutdown();
                stop();
                return;
              }
              if (engine != null) engine.exec(s);
              s = null;
            }
          } else {
            // abort requested
            if (engine.isBusy()) engine.abort();
          }
        }
      });
    }
  }

  @Override
  public void shutdown() {
    log.info("Agent "+getName()+" shutdown");
    if (shell != null) shell.shutdown();
    if (engine != null) engine.shutdown();
  }

}
