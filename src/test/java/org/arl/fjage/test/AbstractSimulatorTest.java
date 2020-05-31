package org.arl.fjage.test;

import net.jodah.concurrentunit.Waiter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.arl.fjage.Container;
import org.arl.fjage.DiscreteEventSimulator;
import org.arl.fjage.LogHandlerProxy;
import org.arl.fjage.Platform;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract class for simulator-based tests.
 */
public abstract class AbstractSimulatorTest {

  private final Logger log = Logger.getLogger(getClass().getName());
  private Platform platform;
  private Container container;
  private Waiter waiter;
  private List<TestEvent> testEvents;
  private List<Expectation> expectations;

  @BeforeClass
  public static void beforeSimulatorTestClass() {
    setLogLevel(Container.class.getName(), Level.WARNING);
    setLogLevel(DiscreteEventSimulator.class.getName(), Level.WARNING);
  }

  private static void setLogLevel(String loggerName, Level level) {
    Logger.getLogger(loggerName).setLevel(level);
  }

  @Before
  public void beforeSimulatorTest() {
    platform = new DiscreteEventSimulator();
    container = new Container(platform);
    waiter = new Waiter();
    testEvents = new CopyOnWriteArrayList<>();
    expectations = new CopyOnWriteArrayList<>();
    LogHandlerProxy.install(platform, log);
  }

  /**
   * Returns the platform.
   *
   * @return Platform.
   */
  protected Platform getPlatform() {
    return platform;
  }

  /**
   * Returns the container.
   *
   * @return Container.
   */
  protected Container getContainer() {
    return container;
  }

  /**
   * Runs the test.
   *
   * @param duration Duration.
   */
  protected void run(Duration duration) {
    final boolean[] shutdownFlag = new boolean[]{false};
    if (duration != null) {
      platform.schedule(new TimerTask() {

        @Override
        public void run() {
          shutdownFlag[0] = true;
          waiter.resume();
        }
      }, duration.toMillis());
    }
    platform.start();
    log.info("Simulation started");
    AssertionError assertionError = null;
    try {
      while (!shutdownFlag[0] && platform.isRunning()) {
        try {
          waiter.await();
          for (final Expectation expectation : expectations) {
            expectation.check(testEvents);
          }
        } catch (InterruptedException e) {
          Assert.fail(e.toString());
        } catch (TimeoutException e) {
          Assert.fail("test simulation timed out");
        } catch (AssertionError e) {
          assertionError = e;
          break;
        }
      }
    } finally {
      //log.info("Simulation shutting down...");
      platform.shutdown();
      while (platform.isRunning()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          break;
        }
      }
      log.info("Simulation finished");
      if (assertionError != null) {
        throw assertionError;
      }
      for (final Expectation expectation : expectations) {
        expectation.checkAtEnd(testEvents);
      }
    }
  }

  /**
   * Emits a test event.
   *
   * @param id Test event ID.
   */
  protected void emitTestEvent(String id) {
    //log.info(String.format("emitTestEvent(%s)", id));
    final TestEvent testEvent = new DefaultTestEvent(platform.currentTimeMillis(), id);
    testEvents.add(testEvent);
    waiter.resume();
  }

  /**
   * Registers the expectation that there will be one and only one test event with the specified ID.
   *
   * @param id Test event ID.
   */
  protected void expectOneAndOnlyOneEvent(String id) {
    expectations.add(new OccurrencesExpectation(id, 1, 1));
  }

  /**
   * Registers the expectation that there will be a specified number of test events with the specified ID.
   *
   * @param id Test event ID.
   */
  protected void expectEventCount(String id, int count) {
    expectations.add(new OccurrencesExpectation(id, count, count));
  }

  /**
   * Registers the expectation that the elapsed time between two specified test events will be within a certain range.
   * Note that if there are more than one test events with the specified ID, only the first test event will be used.
   *
   * @param id1            ID of first test event.
   * @param id2            ID of second test event.
   * @param minElapsedTime Minimum elapsed time (ms).
   * @param maxElapsedTime Maximum elapsed time (ms).
   */
  protected void expectElapsedTimeBetweenEvents(String id1, String id2, long minElapsedTime, long maxElapsedTime) {
    expectations.add(new ElapsedTimeExpectation(id1, id2, minElapsedTime, maxElapsedTime));
  }

  /**
   * Registers the expectation that the elapsed time between consecutive test events with the specified ID will be
   * within a certain range.
   * Note that if there are more than one test events with the specified ID, only the first test event will be used.
   *
   * @param id             ID of test event.
   * @param minElapsedTime Minimum elapsed time (ms).
   * @param maxElapsedTime Maximum elapsed time (ms).
   */
  protected void expectElapsedTimeBetweenEvents(String id, long minElapsedTime, long maxElapsedTime) {
    expectations.add(new RepeatingElapsedTimeExpectation(id, minElapsedTime, maxElapsedTime));
  }

  /**
   * Registers the expectation that the average elapsed time between consecutive test events with the specified ID will
   * be within a certain range.
   * Note that if there are more than one test events with the specified ID, only the first test event will be used.
   *
   * @param id                    ID of test event.
   * @param minAverageElapsedTime Minimum average elapsed time (ms).
   * @param maxAverageElapsedTime Maximum average elapsed time (ms).
   */
  protected void expectAverageElapsedTimeBetweenEvents(String id, long minAverageElapsedTime,
                                                       long maxAverageElapsedTime) {
    expectations.add(new AverageElapsedTimeExpectation(id, minAverageElapsedTime, maxAverageElapsedTime));
  }

  private interface Expectation {

    void check(List<TestEvent> testEvents);

    void checkAtEnd(List<TestEvent> testEvents);
  }

  private static abstract class AbstractExpectation
      implements Expectation {

    protected Stream<TestEvent> findEventById(List<TestEvent> testEvents, String id) {
      return testEvents.stream()
          .filter(event -> StringUtils.equals(event.getId(), id));
    }
  }

  private static abstract class AbstractIdExpectation
      extends AbstractExpectation {

    private final String id;

    private AbstractIdExpectation(String id) {
      super();

      this.id = id;
    }

    public String getId() {
      return id;
    }
  }

  private static class OccurrencesExpectation
      extends AbstractIdExpectation {

    private final Integer min;
    private final Integer max;

    private OccurrencesExpectation(String id, Integer min, Integer max) {
      super(id);

      this.min = min;
      this.max = max;

      if ((min == null) && (max == null)) {
        throw new IllegalArgumentException("min and max cannot both be null");
      }
      if ((min != null) && (max != null)) {
        if (min > max) {
          throw new IllegalArgumentException("min cannot be greater than max");
        }
      }
    }

    @Override
    public void check(List<TestEvent> testEvents) {
      final long count = findEventById(testEvents, getId())
          .count();
      if ((max != null) && (count > max)) {
        Assert.fail(String.format("Expected: at most %,d '%s' test events, actual: %,d", min, getId(), count));
      }
    }

    @Override
    public void checkAtEnd(List<TestEvent> testEvents) {
      final long count = findEventById(testEvents, getId())
          .count();
      if ((min != null) && (count < min)) {
        Assert.fail(String.format("Expected: at least %,d '%s' test events, actual: %,d", min, getId(), count));
      }
      if ((max != null) && (count > max)) {
        Assert.fail(String.format("Expected: at most %,d '%s' test events, actual: %,d", min, getId(), count));
      }
    }
  }

  private static class ElapsedTimeExpectation
      extends AbstractExpectation {

    private final String id1;
    private final String id2;
    private final long minElapsedTime;
    private final long maxElapsedTime;

    private ElapsedTimeExpectation(String id1, String id2, long minElapsedTime, long maxElapsedTime) {
      super();

      this.id1 = id1;
      this.id2 = id2;
      this.minElapsedTime = minElapsedTime;
      this.maxElapsedTime = maxElapsedTime;
    }

    @Override
    public void check(List<TestEvent> testEvents) {
      final Optional<TestEvent> event1Optional = findEventById(testEvents, id1)
          .findFirst();
      final Optional<TestEvent> event2Optional = findEventById(testEvents, id2)
          .findFirst();
      if (event1Optional.isPresent() && event2Optional.isPresent()) {
        final TestEvent event1 = event1Optional.get();
        final TestEvent event2 = event2Optional.get();
        final long elapsedTime = event2.getTimestamp() - event1.getTimestamp();
        if (elapsedTime < 0) {
          Assert.fail(String.format("Test event '%s' should not occur after test event '%s'", id1, id2));
          return;
        }
      }
    }

    @Override
    public void checkAtEnd(List<TestEvent> testEvents) {
      final Optional<TestEvent> event1Optional = findEventById(testEvents, id1)
          .findFirst();
      final Optional<TestEvent> event2Optional = findEventById(testEvents, id2)
          .findFirst();
      if (!event1Optional.isPresent()) {
        Assert.fail(String.format("Test event '%s' not present", id1));
        return;
      }
      if (!event2Optional.isPresent()) {
        Assert.fail(String.format("Test event '%s' not present", id2));
        return;
      }
      final TestEvent event1 = event1Optional.get();
      final TestEvent event2 = event2Optional.get();
      final long elapsedTime = event2.getTimestamp() - event1.getTimestamp();
      if (elapsedTime < 0) {
        Assert.fail(String.format("Test event '%s' should not occur after test event '%s'", id1, id2));
        return;
      }
      if (elapsedTime < minElapsedTime) {
        Assert.fail(String.format(
            "Minimum elapsed time between tests events '%s' and '%s' should be at least %,dms, actual: %,dms",
            id1, id2, minElapsedTime, elapsedTime));
        return;
      }
      if (elapsedTime > maxElapsedTime) {
        Assert.fail(String.format(
            "Maximum elapsed time between tests events '%s' and '%s' should be at most %,dms, actual: %,dms",
            id1, id2, maxElapsedTime, elapsedTime));
      }
    }
  }

  private static class RepeatingElapsedTimeExpectation
      extends AbstractIdExpectation {

    private final long minElapsedTime;
    private final long maxElapsedTime;

    private RepeatingElapsedTimeExpectation(String id, long minElapsedTime, long maxElapsedTime) {
      super(id);

      this.minElapsedTime = minElapsedTime;
      this.maxElapsedTime = maxElapsedTime;
    }

    @Override
    public void check(List<TestEvent> testEvents) {
      final List<TestEvent> collectedTestEvents = findEventById(testEvents, getId()).collect(Collectors.toList());
      if (collectedTestEvents.size() > 1) {
        doCheck(collectedTestEvents);
      }
    }

    @Override
    public void checkAtEnd(List<TestEvent> testEvents) {
      final List<TestEvent> collectedTestEvents = findEventById(testEvents, getId()).collect(Collectors.toList());
      if (collectedTestEvents.isEmpty()) {
        Assert.fail(String.format("Test event '%s' not present", getId()));
        return;
      }
      if (collectedTestEvents.size() == 1) {
        Assert.fail(String.format("More than one '%s' test event is expected", getId()));
        return;
      }
      doCheck(collectedTestEvents);
    }

    private void doCheck(List<TestEvent> collectedTestEvents) {
      for (int i = 0; i < collectedTestEvents.size() - 1; i++) {
        final TestEvent event1 = collectedTestEvents.get(i);
        final TestEvent event2 = collectedTestEvents.get(i + 1);
        final long elapsedTime = event2.getTimestamp() - event1.getTimestamp();
        if (elapsedTime < minElapsedTime) {
          Assert.fail(String.format(
              "Minimum elapsed time between '%s' tests events should be at least %,dms, actual: %,dms",
              getId(), minElapsedTime, elapsedTime));
          return;
        }
        if (elapsedTime > maxElapsedTime) {
          Assert.fail(String.format(
              "Maximum elapsed time between '%s' tests events should be at most %,dms, actual: %,dms",
              getId(), maxElapsedTime, elapsedTime));
        }
      }
    }
  }

  private static class AverageElapsedTimeExpectation
      extends AbstractIdExpectation {

    private final long minAverageElapsedTime;
    private final long maxAverageElapsedTime;

    private AverageElapsedTimeExpectation(String id, long minAverageElapsedTime, long maxAverageElapsedTime) {
      super(id);

      this.minAverageElapsedTime = minAverageElapsedTime;
      this.maxAverageElapsedTime = maxAverageElapsedTime;
    }

    @Override
    public void check(List<TestEvent> testEvents) {
      // skip
    }

    @Override
    public void checkAtEnd(List<TestEvent> testEvents) {
      final List<TestEvent> collectedTestEvents = findEventById(testEvents, getId()).collect(Collectors.toList());
      if (collectedTestEvents.isEmpty()) {
        Assert.fail(String.format("Test event '%s' not present", getId()));
        return;
      }
      if (collectedTestEvents.size() == 1) {
        Assert.fail(String.format("More than one '%s' test event is expected", getId()));
        return;
      }
      long totalElapsedTime = 0;
      int count = 0;
      for (int i = 0; i < collectedTestEvents.size() - 1; i++) {
        final TestEvent event1 = collectedTestEvents.get(i);
        final TestEvent event2 = collectedTestEvents.get(i + 1);
        final long elapsedTime = event2.getTimestamp() - event1.getTimestamp();
        totalElapsedTime += elapsedTime;
        count++;
      }
      final double averageElapsedTime = (double) totalElapsedTime / (double) count;
      if (averageElapsedTime < minAverageElapsedTime) {
        Assert.fail(String.format(
            "Minimum average elapsed time between '%s' tests events should be at least %,dms, actual: %,fms",
            getId(), minAverageElapsedTime, averageElapsedTime));
        return;
      }
      if (averageElapsedTime > maxAverageElapsedTime) {
        Assert.fail(String.format(
            "Maximum average elapsed time between '%s' tests events should be at most %,dms, actual: %,fms",
            getId(), maxAverageElapsedTime, averageElapsedTime));
      }
    }
  }

  private interface TestEvent {

    long getTimestamp();

    String getId();
  }

  private static class DefaultTestEvent
      implements TestEvent {

    private final long timestamp;
    private final String id;

    public DefaultTestEvent(long timestamp, String id) {
      super();

      this.timestamp = timestamp;
      this.id = id;
    }

    @Override
    public long getTimestamp() {
      return timestamp;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("timestamp", timestamp)
          .append("id", id)
          .toString();
    }
  }
}
