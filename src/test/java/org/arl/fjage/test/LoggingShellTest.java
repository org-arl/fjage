/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.arl.fjage.*;
import org.arl.fjage.param.*;
import org.arl.fjage.shell.*;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LoggingShellTest {

  private static final Logger log = Logger.getLogger(LoggingShellTest.class.getName());

  @Before
  public void beforeTesting() {
    LogFormatter.install(null);
  }

  private static class RecordingHandler extends Handler {

    final List<LogRecord> records = Collections.synchronizedList(new ArrayList<LogRecord>());

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
      // do nothing
    }

    @Override
    public void close() {
      // do nothing
    }

    LogRecord find(String s) {
      synchronized (records) {
        for (LogRecord r: records)
          if (r.getMessage() != null && r.getMessage().contains(s)) return r;
      }
      return null;
    }

    boolean contains(String s) {
      return find(s) != null;
    }

  }

  private static class RecordingShell implements Shell {

    Object lastPrintln = null;
    Object lastNotify = null;
    Object lastError = null;

    @Override
    public void init(ScriptEngine engine) {
      // do nothing
    }

    @Override
    public void prompt(Object obj) {
      // do nothing
    }

    @Override
    public void input(Object obj) {
      // do nothing
    }

    @Override
    public void println(Object obj) {
      lastPrintln = obj;
    }

    @Override
    public void notify(Object obj) {
      lastNotify = obj;
    }

    @Override
    public void error(Object obj) {
      lastError = obj;
    }

    @Override
    public String readLine(String prompt1, String prompt2, String line) {
      return null;
    }

    @Override
    public boolean isDumb() {
      return true;
    }

    @Override
    public void shutdown() {
      // do nothing
    }

  }

  @Test
  public void testLoggingShell() {
    log.info("testLoggingShell");
    Logger logger = Logger.getLogger(LoggingShell.class.getName());
    Level oldLevel = logger.getLevel();
    RecordingHandler handler = new RecordingHandler();
    handler.setLevel(Level.ALL);
    logger.setLevel(Level.FINE);
    logger.addHandler(handler);
    try {
      RecordingShell rec = new RecordingShell();
      LoggingShell shell = new LoggingShell(rec);
      assertSame(rec, shell.getDelegate());
      assertTrue(shell.isDumb());
      shell.println("hello");
      assertEquals("hello", rec.lastPrintln);
      assertTrue("println not logged", handler.contains("< hello"));
      shell.error("oops");
      assertEquals("oops", rec.lastError);
      assertTrue("error not logged", handler.contains("< ERROR: oops"));
      shell.notify("A >> MSG");
      assertEquals("A >> MSG", rec.lastNotify);
      assertFalse("notify should not be logged", handler.contains("A >> MSG"));
      char[] big = new char[20000];
      Arrays.fill(big, 'x');
      shell.println(new String(big));
      LogRecord r = handler.find(" <<snip>> ");
      assertNotNull("long output not truncated", r);
      assertTrue("truncated output too long", r.getMessage().length() < 100);
      handler.records.clear();
      logger.setLevel(Level.INFO);
      shell.println("quiet");
      assertEquals("quiet", rec.lastPrintln);
      assertTrue("output logged even when level disabled", handler.records.isEmpty());
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(oldLevel);
    }
  }

  @Test
  public void testShellAgentOutputLogging() throws IOException {
    log.info("testShellAgentOutputLogging");
    // enable FINE on the package logger (as a user would with "logLevel 'org.arl.fjage.shell', FINE"),
    // but attach the capture handler directly to the agent logger, since agent loggers do not
    // propagate records to parent handlers (see LogHandlerProxy.install)
    Logger pkgLogger = Logger.getLogger("org.arl.fjage.shell");
    Logger logger = Logger.getLogger(ShellAgent.class.getName());
    Level oldLevel = pkgLogger.getLevel();
    RecordingHandler handler = new RecordingHandler();
    handler.setLevel(Level.ALL);
    pkgLogger.setLevel(Level.FINE);
    logger.addHandler(handler);
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    Platform platform = new RealTimePlatform();
    try {
      Container container = new Container(platform);
      container.add("shell", new ShellAgent(new DumbShell(pis, bos), new EchoScriptEngine()));
      platform.start();
      platform.delay(2000);
      pos.write("hello\n".getBytes());
      pos.flush();
      long t0 = System.currentTimeMillis();
      while (!handler.contains("< hello") && System.currentTimeMillis()-t0 < 10000)
        platform.delay(100);
      LogRecord r = handler.find("< hello");
      assertNotNull("shell output not logged", r);
      assertTrue("output logged under unexpected logger: "+r.getLoggerName(),
                 r.getLoggerName().startsWith("org.arl.fjage.shell.ShellAgent"));
      assertTrue("shell input not logged", handler.contains("> hello"));
    } finally {
      platform.shutdown();
      logger.removeHandler(handler);
      pkgLogger.setLevel(oldLevel);
      pos.close();
    }
  }

  @Test
  public void testGroovyScriptOutputLogging() {
    log.info("testGroovyScriptOutputLogging");
    Logger lsLogger = Logger.getLogger(LoggingShell.class.getName());
    Logger gseLogger = Logger.getLogger(GroovyScriptEngine.class.getName());
    Level oldLsLevel = lsLogger.getLevel();
    Level oldGseLevel = gseLogger.getLevel();
    RecordingHandler lsHandler = new RecordingHandler();
    RecordingHandler gseHandler = new RecordingHandler();
    lsHandler.setLevel(Level.ALL);
    gseHandler.setLevel(Level.ALL);
    lsLogger.setLevel(Level.FINE);
    gseLogger.setLevel(Level.FINE);
    lsLogger.addHandler(lsHandler);
    gseLogger.addHandler(gseHandler);
    try {
      RecordingShell rec = new RecordingShell();
      GroovyScriptEngine engine = new GroovyScriptEngine();
      engine.bind(new LoggingShell(rec));
      engine.exec("println 'hi from script'");
      assertEquals("hi from script", rec.lastPrintln);
      assertTrue("script println not logged", lsHandler.contains("< hi from script"));
      engine.exec("42");
      assertEquals("42", rec.lastPrintln);
      assertTrue("command result not logged", lsHandler.contains("< 42"));
      assertFalse("redundant RESULT log not suppressed", gseHandler.contains("RESULT:"));
      GroovyScriptEngine engine2 = new GroovyScriptEngine();
      engine2.bind(rec);
      engine2.exec("42");
      assertTrue("RESULT log missing for unwrapped shell", gseHandler.contains("RESULT: 42"));
    } finally {
      lsLogger.removeHandler(lsHandler);
      gseLogger.removeHandler(gseHandler);
      lsLogger.setLevel(oldLsLevel);
      gseLogger.setLevel(oldGseLevel);
    }
  }

  private static class LogLevelClientAgent extends Agent {

    volatile boolean done = false;
    volatile Object levelRsp = null;
    volatile Set<Parameter> listed = null;

    @Override
    public void init() {
      add(new OneShotBehavior() {
        @Override
        public void action() {
          delay(500);
          AgentID shell = agent("shell");
          Message rsp = request(new ParameterReq(shell).set(new NamedParameter("logLevel"), Level.FINE), 2000);
          if (rsp instanceof ParameterRsp) levelRsp = ((ParameterRsp)rsp).get(new NamedParameter("logLevel"));
          rsp = request(new ParameterReq(shell), 2000);
          if (rsp instanceof ParameterRsp) listed = ((ParameterRsp)rsp).parameters();
          done = true;
        }
      });
    }

  }

  @Test
  public void testShellLogLevelEndToEnd() throws IOException {
    log.info("testShellLogLevelEndToEnd");
    GroovyExtensions.enable();
    RecordingHandler handler = new RecordingHandler();
    handler.setLevel(Level.ALL);
    // attach directly to the agent logger: agent loggers do not propagate records
    // to parent handlers (see LogHandlerProxy.install)
    Logger logger = Logger.getLogger(ShellAgent.class.getName());
    logger.addHandler(handler);
    PipedOutputStream pos = new PipedOutputStream();
    PipedInputStream pis = new PipedInputStream(pos);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    Platform platform = new RealTimePlatform();
    try {
      Container container = new Container(platform);
      ShellAgent agent = new ShellAgent(new DumbShell(pis, bos), new GroovyScriptEngine());
      container.add("shell", agent);
      platform.start();
      platform.delay(2000);
      pos.write("shell.logLevel = FINE\n".getBytes());
      pos.flush();
      long t0 = System.currentTimeMillis();
      while (agent.getLogLevel() != Level.FINE && System.currentTimeMillis()-t0 < 10000)
        platform.delay(100);
      assertEquals("shell.logLevel = FINE command failed", Level.FINE, agent.getLogLevel());
      pos.write("println 'hello e2e'\n".getBytes());
      pos.flush();
      t0 = System.currentTimeMillis();
      while (!handler.contains("< hello e2e") && System.currentTimeMillis()-t0 < 10000)
        platform.delay(100);
      assertTrue("command input not logged", handler.contains("> println 'hello e2e'"));
      assertNotNull("command output not logged", handler.find("< hello e2e"));
    } finally {
      platform.shutdown();
      logger.removeHandler(handler);
      pos.close();
    }
  }

  @Test
  public void testShellLogLevelParam() {
    log.info("testShellLogLevelParam");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    ShellAgent shell = new ShellAgent(new EchoScriptEngine());
    container.add("shell", shell);
    LogLevelClientAgent client = new LogLevelClientAgent();
    container.add("client", client);
    platform.start();
    while (!client.done)
      platform.delay(100);
    platform.shutdown();
    assertEquals("logLevel parameter set failed", Level.FINE, client.levelRsp);
    assertEquals("agent log level not changed", Level.FINE, shell.getLogLevel());
    assertNotNull("parameter listing failed", client.listed);
    for (Parameter p: client.listed)
      assertFalse("logLevel should be hidden from parameter listing", "logLevel".equals(p.name()));
  }

}
