/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test;

import org.arl.fjage.LogFormatter;
import org.arl.fjage.shell.*;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class GroovyScriptEngineTest {

  private static class RecordingShell implements Shell {

    Object lastPrintln = null;
    Object lastError = null;

    void clear() {
      lastPrintln = null;
      lastError = null;
    }

    @Override public void init(ScriptEngine engine) { }
    @Override public void prompt(Object obj) { }
    @Override public void input(Object obj) { }
    @Override public void println(Object obj) { lastPrintln = obj; }
    @Override public void notify(Object obj) { }
    @Override public void error(Object obj) { lastError = obj; }
    @Override public String readLine(String prompt1, String prompt2, String line) { return null; }
    @Override public boolean isDumb() { return true; }
    @Override public void shutdown() { }

  }

  private GroovyScriptEngine engine;
  private RecordingShell shell;

  @Before
  public void beforeTesting() {
    LogFormatter.install(null);
    engine = new GroovyScriptEngine();
    shell = new RecordingShell();
    engine.bind(shell);
  }

  private Object exec(String cmd) {
    shell.clear();
    engine.exec(cmd);
    return shell.lastPrintln;
  }

  @Test
  public void testBadImportDoesNotBreakShell() {
    exec("import com.example.NoSuchClass");
    assertNotNull("bad import not reported", shell.lastError);
    assertEquals("2", exec("1+1"));
    assertNull(shell.lastError);
    exec("import static com.example.NoSuchClass.*");
    assertNotNull("bad static import not reported", shell.lastError);
    assertEquals("2", exec("1+1"));
    assertNull(shell.lastError);
  }

  @Test
  public void testImportWithSemicolon() {
    exec("import java.util.concurrent.ConcurrentSkipListSet;");
    assertNull(shell.lastError);
    assertEquals("[]", exec("new ConcurrentSkipListSet()"));
    assertNull(shell.lastError);
  }

  @Test
  public void testImportFollowedByCode() {
    assertEquals("[]", exec("import java.util.concurrent.ConcurrentSkipListSet;\nx = new ConcurrentSkipListSet()"));
    assertNull(shell.lastError);
    assertEquals("[1]", exec("import java.util.concurrent.LinkedBlockingDeque; import java.util.concurrent.*\nnew LinkedBlockingDeque([1])"));
    assertNull(shell.lastError);
    assertEquals("[]", exec("new ConcurrentSkipListSet()"));
    assertNull(shell.lastError);
  }

  @Test
  public void testImportWithoutSeparator() {
    exec("import java.util.concurrent.ConcurrentSkipListSet x = 1");
    assertNotNull("missing separator not reported", shell.lastError);
    exec("new ConcurrentSkipListSet()");
    assertNotNull("import persisted despite syntax error", shell.lastError);
  }

  @Test
  public void testImportAlias() {
    exec("import java.util.concurrent.ConcurrentSkipListSet as CSS");
    assertNull(shell.lastError);
    assertEquals("[]", exec("new CSS()"));
    assertNull(shell.lastError);
    assertEquals("[1]", exec("import java.util.LinkedList as LL; new LL([1])"));
    assertNull(shell.lastError);
    exec("import static java.lang.Math.max as biggest");
    assertNull(shell.lastError);
    assertEquals("3", exec("biggest(2, 3)"));
    assertNull(shell.lastError);
    exec("import com.example.NoSuchClass as Nope");
    assertNotNull("bad aliased import not reported", shell.lastError);
    assertEquals("2", exec("1+1"));
    assertNull(shell.lastError);
  }

}
