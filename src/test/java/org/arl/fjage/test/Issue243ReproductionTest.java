package org.arl.fjage.test;

import org.arl.fjage.shell.GroovyScriptEngine;
import org.arl.fjage.shell.DumbShell;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test that reproduces the exact scenario from the issue report.
 */
public class Issue243ReproductionTest {

  @Test
  public void testIssue243Scenario() {
    // Create a shell and script engine
    DumbShell shell = new DumbShell();
    GroovyScriptEngine engine = new GroovyScriptEngine();
    engine.bind(shell);
    
    // Reproduce the exact scenario from the issue:
    // 1. Type incorrect import
    boolean result1 = engine.exec("import java.nio.Paths");
    
    // 2. Try to use the class (this should fail gracefully)
    boolean result2 = engine.exec("Paths.get('/tmp/profiles.groovy')");
    
    // 3. Try the correct import (this should work)
    boolean result3 = engine.exec("import java.nio.file.Paths");
    
    // 4. Try another command (this should work)
    boolean result4 = engine.exec("java.nio.file.Paths");
    
    // 5. Try basic commands (these should all work)
    boolean result5 = engine.exec("ps");
    boolean result6 = engine.exec("println 'test'");
    
    // The key assertion: the shell should remain functional
    assertTrue("Shell should remain functional after incorrect import", result5);
    assertTrue("Shell should be able to execute basic commands", result6);
  }
  
  @Test
  public void testMultipleIncorrectImportsStillAllowsFunctionality() {
    // Create a shell and script engine
    DumbShell shell = new DumbShell();
    GroovyScriptEngine engine = new GroovyScriptEngine();
    engine.bind(shell);
    
    // Execute multiple incorrect imports (simulating user confusion)
    engine.exec("import java.nio.Paths");
    engine.exec("import java.nio.Paths");
    engine.exec("import java.nio.Paths");
    
    // The shell should still work for basic commands
    boolean result = engine.exec("println 'Shell is still working'");
    assertTrue("Shell should work after multiple incorrect imports", result);
    
    // And correct imports should still work
    boolean correctImport = engine.exec("import java.nio.file.Paths");
    assertTrue("Correct import should work", correctImport);
  }
}