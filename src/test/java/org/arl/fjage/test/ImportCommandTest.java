package org.arl.fjage.test;

import org.arl.fjage.shell.GroovyScriptEngine;
import org.arl.fjage.shell.DumbShell;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test for the shell import fix.
 */
public class ImportCommandTest {

  @Test
  public void testImportCommandDoesNotBreakShell() {
    // Create a shell and script engine
    DumbShell shell = new DumbShell();
    GroovyScriptEngine engine = new GroovyScriptEngine();
    engine.bind(shell);
    
    // Test that an invalid import doesn't break subsequent commands
    boolean result1 = engine.exec("import java.nio.Paths");  // This should not crash
    boolean result2 = engine.exec("println 'hello world'");  // This should still work
    
    assertTrue("Shell should continue working after invalid import", result2);
  }
  
  @Test
  public void testValidImportWorks() {
    // Create a shell and script engine
    DumbShell shell = new DumbShell();
    GroovyScriptEngine engine = new GroovyScriptEngine();
    engine.bind(shell);
    
    // Test that a valid import works
    boolean result = engine.exec("import java.util.Date");
    assertTrue("Valid import should work", result);
  }
  
  @Test
  public void testCorrectImportAfterIncorrectOne() {
    // Create a shell and script engine  
    DumbShell shell = new DumbShell();
    GroovyScriptEngine engine = new GroovyScriptEngine();
    engine.bind(shell);
    
    // Execute an incorrect import
    engine.exec("import java.nio.Paths");
    
    // Then execute a correct import - this should work
    boolean result = engine.exec("import java.nio.file.Paths");
    assertTrue("Correct import should work after incorrect one", result);
  }
}