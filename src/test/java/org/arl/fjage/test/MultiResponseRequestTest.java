package org.arl.fjage.test;

import org.arl.fjage.*;
import org.junit.Test;

import java.time.Duration;

public class MultiResponseRequestTest
    extends AbstractFluentRequestTest {

  @Test
  public void testAgreeThenInform() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent());
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService);
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        add(new WakerBehavior(5000, () -> {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(5), Performative.AGREE)
              .replyDelivered(Duration.ofSeconds(5))
              .build();

          final Message response = prepareMultiResponseRequest(testRequest)
              .onAgree(message -> {
                emitTestEvent("AGREE");
                return false;
              })
              .onInform(message -> {
                emitTestEvent("INFORM");
                return true;
              })
              .onRefuse(message -> {
                getWaiter().fail("REFUSE not expected");
                return true;
              })
              .onFailure(message -> {
                getWaiter().fail("FAILURE not expected");
                return true;
              })
              .otherwise(message -> {
                getWaiter().fail("OTHERWISE not expected");
                return true;
              })
              .onTimeout(Duration.ofSeconds(8).toMillis(), () -> getWaiter().fail("timeout not expected"))
              .sendAndWait();

          getWaiter().assertTrue(response instanceof TestDeliverySucceededNtf);
        }));
      }
    });

    // ---- expect ----
    expectOneAndOnlyOneEvent("AGREE");
    expectOneAndOnlyOneEvent("INFORM");
    expectElapsedTimeBetweenEvents("AGREE", "INFORM", 5000, 5000);

    // ---- run ----
    run(Duration.ofMinutes(5));
  }

  @Test
  public void testMultipleInforms() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent());
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService);
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        add(new WakerBehavior(5000, () -> {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(1), Performative.INFORM)
              .replyAfter(Duration.ofSeconds(1), Performative.INFORM)
              .replyDelivered(Duration.ofSeconds(1))
              .replyAfter(Duration.ofSeconds(1), Performative.INFORM)
              .build();

          prepareMultiResponseRequest(testRequest)
              .onInform(message -> {
                emitTestEvent("INFORM");
                return message instanceof TestDeliverySucceededNtf;
              })
              .onInform(message -> {
                emitTestEvent("INFORM2");
                return false;
              })
              .onTimeout(Duration.ofSeconds(10).toMillis(), () -> getWaiter().fail("timeout not expected"))
              .send();
        }));
      }
    });

    // ---- expect ----
    expectEventCount("INFORM", 3);
    expectEventCount("INFORM2", 3);

    // ---- run ----
    run(Duration.ofMinutes(5));
  }

  @Test
  public void testEveryResponseRestartsTimeout() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent());
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService);
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        add(new WakerBehavior(5000, () -> {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(6), Performative.AGREE)
              .replyDelivered(Duration.ofSeconds(6))
              .build();

          final Message response = prepareMultiResponseRequest(testRequest)
              .onInform(message -> {
                emitTestEvent("INFORM");
                return true;
              })
              .onTimeout(Duration.ofSeconds(8).toMillis(), () -> getWaiter().fail("timeout not expected"))
              .sendAndWait();

          getWaiter().assertTrue(response instanceof TestDeliverySucceededNtf);
        }));
      }
    });

    // ---- expect ----
    expectOneAndOnlyOneEvent("INFORM");

    // ---- run ----
    run(Duration.ofMinutes(5));
  }

  @Test
  public void testTimeoutAfterResponse() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent());
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService);
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        add(new WakerBehavior(5000, () -> {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(2), Performative.AGREE)
              .build();

          final Message response = prepareMultiResponseRequest(testRequest)
              .onAgree(message -> {
                emitTestEvent("AGREE");
                return false;
              })
              .onTimeout(Duration.ofSeconds(5).toMillis(), () -> emitTestEvent("TIMEOUT"))
              .sendAndWait();

          getWaiter().assertNull(response);
        }));
      }
    });

    // ---- expect ----
    expectOneAndOnlyOneEvent("AGREE");
    expectOneAndOnlyOneEvent("TIMEOUT");
    expectElapsedTimeBetweenEvents("AGREE", "TIMEOUT", 5000, 5000);

    // ---- run ----
    run(Duration.ofMinutes(5));
  }

  @Test
  public void testRefuseEndsRequestByDefault() {
    testEndsRequestByDefault(Performative.REFUSE);
  }

  @Test
  public void testFailureEndsRequestByDefault() {
    testEndsRequestByDefault(Performative.FAILURE);
  }

  @Test
  public void testFailureHandlerMayContinue() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent());
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService);
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        add(new WakerBehavior(5000, () -> {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(1), Performative.FAILURE)
              .replyDelivered(Duration.ofSeconds(1))
              .build();

          final Message response = prepareMultiResponseRequest(testRequest)
              .onFailure(message -> false)
              .onInform(message -> true)
              .onTimeout(Duration.ofSeconds(10).toMillis(), () -> getWaiter().fail("timeout not expected"))
              .sendAndWait();

          getWaiter().assertTrue(response instanceof TestDeliverySucceededNtf);
          emitTestEvent("DONE");
        }));
      }
    });

    // ---- expect ----
    expectOneAndOnlyOneEvent("DONE");

    // ---- run ----
    run(Duration.ofMinutes(5));
  }

  private void testEndsRequestByDefault(Performative performative) {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent());
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService);
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        add(new WakerBehavior(5000, () -> {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(1), Performative.AGREE)
              .replyAfter(Duration.ofSeconds(1), performative)
              .build();

          final Message response = prepareMultiResponseRequest(testRequest)
              .onInform(message -> true)
              .onTimeout(Duration.ofSeconds(10).toMillis(), () -> getWaiter().fail("timeout not expected"))
              .sendAndWait();

          getWaiter().assertNotNull(response);
          getWaiter().assertEquals(performative, response.getPerformative());
          emitTestEvent("DONE");
        }));
      }
    });

    // ---- expect ----
    expectOneAndOnlyOneEvent("DONE");

    // ---- run ----
    run(Duration.ofMinutes(5));
  }
}
