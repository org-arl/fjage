package org.arl.fjage.loadtest;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.*;

/**
 * Captures WARNING+ log records emitted by fjage during a test scenario.
 */
public class LogCapture extends Handler implements AutoCloseable {

  private final Queue<LogRecord> records = new ConcurrentLinkedQueue<>();
  private final Logger root = Logger.getLogger("");

  public LogCapture() {
    setLevel(Level.WARNING);
    root.addHandler(this);
  }

  @Override
  public void publish(LogRecord r) {
    if (r.getLevel().intValue() >= Level.WARNING.intValue()) records.add(r);
  }

  @Override
  public void flush() {
  }

  @Override
  public void close() {
    root.removeHandler(this);
  }

  private List<String> filter(Level level) {
    List<String> out = new ArrayList<>();
    for (LogRecord r : records) {
      if (r.getLevel().intValue() >= level.intValue()) {
        String s = r.getLevel() + " [" + r.getLoggerName() + "] " + r.getMessage();
        if (r.getThrown() != null) s += " :: " + r.getThrown();
        out.add(s);
      }
    }
    return out;
  }

  public List<String> severes() {
    return filter(Level.SEVERE);
  }

  public List<String> warnings() {
    return filter(Level.WARNING);
  }

  public void clear() {
    records.clear();
  }
}
