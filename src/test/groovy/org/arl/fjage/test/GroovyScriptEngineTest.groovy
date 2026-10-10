package org.arl.fjage.test

import org.arl.fjage.shell.GroovyScriptEngine
import org.arl.fjage.shell.Shell
import org.junit.Test
import static org.junit.Assert.*

class GroovyScriptEngineTest {
  @Test
  void completionDistinguishesIncompleteAndInvalidInput() {
    def engine = new GroovyScriptEngine()
    try {
      ['if (true) {', 'foo(', 'def x =', 'def x = [1,', "println '''hello", '/* comment', 'println \"${', 'if (true) {\n println 1'].each { assertFalse(it, engine.isComplete(it)) }
      ['', '  ', 'println 1', 'def = 1', 'foo(]'].each { assertTrue(it, engine.isComplete(it)) }
    } finally { engine.shutdown() }
  }

  static class BrokenScript extends Script {
    BrokenScript() { throw new IllegalArgumentException('constructor failed') }
    Object run() { null }
  }

  @Test
  void constructorReportsOriginalFailure() {
    def engine = new GroovyScriptEngine()
    def errors = []
    engine.bind([error: { Object error -> errors.add(error) }] as Shell)
    try {
      engine.exec(BrokenScript)
      assertEquals(1, errors.size())
      assertTrue(errors.first() instanceof IllegalArgumentException)
      assertEquals('constructor failed', errors.first().message)
    } finally { engine.shutdown() }
  }
}
