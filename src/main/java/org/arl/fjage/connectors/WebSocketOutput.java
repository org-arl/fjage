package org.arl.fjage.connectors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bridges byte-stream output to ordered WebSocket text messages. */
final class WebSocketOutput extends PseudoOutputStream implements Runnable {
  private final boolean lineMode;
  private final Predicate<String> send;
  private final Runnable onClose;
  private final Logger log;
  private final Thread writer;
  private long written, completed;
  private volatile boolean closed;

  WebSocketOutput(String name, boolean lineMode, Predicate<String> send, Runnable onClose, Logger log) {
    this.lineMode = lineMode;
    this.send = send;
    this.onClose = onClose;
    this.log = log;
    writer = new Thread(this, "WebSocketOutput:"+name);
    writer.setDaemon(true);
    writer.setPriority(Thread.MIN_PRIORITY);
  }

  void start() {
    writer.start();
  }

  @Override
  public synchronized void write(int value) throws IOException {
    super.write(value);
    written++;
  }

  @Override
  public void write(byte[] bytes) throws IOException {
    write(bytes, 0, bytes.length);
  }

  @Override
  public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
    super.write(bytes, offset, length);
    written += length;
  }

  synchronized boolean awaitCompletion(long timeout) {
    long target = written;
    long remaining = TimeUnit.MILLISECONDS.toNanos(timeout);
    long start = System.nanoTime();
    try {
      while (completed < target && !closed) {
        if (remaining <= 0) return false;
        TimeUnit.NANOSECONDS.timedWait(this, remaining);
        remaining = TimeUnit.MILLISECONDS.toNanos(timeout) - (System.nanoTime()-start);
      }
      return completed >= target;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    super.close();
    notifyAll();
  }

  @Override
  public void run() {
    var decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
    ByteBuffer input = ByteBuffer.allocate(8192);
    input.limit(0);
    CharBuffer output = CharBuffer.allocate(4096);
    boolean needInput = true;
    try {
      while (!Thread.currentThread().isInterrupted()) {
        String text;
        int count;
        if (lineMode) {
          byte[] bytes = q.readDelimited((byte)'\n');
          if (bytes == null) break;
          text = new String(bytes, StandardCharsets.UTF_8);
          count = bytes.length;
        } else {
          if (needInput) {
            input.compact();
            int length = q.read(input.array(), input.position(), input.remaining());
            if (length < 0) break;
            input.position(input.position()+length);
            input.flip();
          }
          int start = input.position();
          output.clear();
          var result = decoder.decode(input, output, false);
          if (result.isError()) result.throwException();
          needInput = result.isUnderflow();
          count = input.position()-start;
          if (count == 0) continue;
          text = output.flip().toString();
        }
        if (closed || !send.test(text)) break;
        synchronized (this) {
          completed += count;
          notifyAll();
        }
      }
    } catch (IOException ex) {
      if (!closed) log.log(Level.WARNING, "WebSocket output read failure", ex);
    } finally {
      close();
      onClose.run();
    }
  }
}
