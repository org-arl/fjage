package org.arl.fjage.test

import org.arl.fjage.*
import org.junit.Test

import java.time.Duration

import static org.arl.fjage.test.AbstractFluentRequestTest.TestDeliverySucceededNtf
import static org.arl.fjage.test.AbstractFluentRequestTest.TestRequest

class GroovyFluentRequestTest
    extends AbstractFluentRequestTest {

  @Test
  void testAgree() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent())
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService)
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(5), Performative.AGREE)
              .build()

          prepareRequest(testRequest)
              .onAgree({ message -> emitTestEvent("EVENT1") })
              .onAgree({ message -> emitTestEvent("EVENT2") })
              .onRefuse({ message -> getWaiter().fail("REFUSE not expected") })
              .onFailure({ message -> getWaiter().fail("FAILURE not expected") })
              .onInform({ message -> getWaiter().fail("INFORM not expected") })
              .otherwise({ message -> getWaiter().fail("OTHERWISE not expected") })
              .onTimeout(Duration.ofSeconds(10).toMillis(), { getWaiter().fail("timeout not expected") })
              .send()
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1")
    expectOneAndOnlyOneEvent("EVENT2")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testRefuse() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent())
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService)
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(5), Performative.REFUSE)
              .build()

          prepareRequest(testRequest)
              .onAgree({ message -> getWaiter().fail("AGREE not expected") })
              .onRefuse({ message -> emitTestEvent("EVENT1") })
              .onRefuse({ message -> emitTestEvent("EVENT2") })
              .onFailure({ message -> getWaiter().fail("FAILURE not expected") })
              .onInform({ message -> getWaiter().fail("INFORM not expected") })
              .otherwise({ message -> getWaiter().fail("OTHERWISE not expected") })
              .onTimeout(Duration.ofSeconds(10).toMillis(), { getWaiter().fail("timeout not expected") })
              .send()
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1")
    expectOneAndOnlyOneEvent("EVENT2")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testFailure() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent())
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService)
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(5), Performative.FAILURE)
              .build()

          prepareRequest(testRequest)
              .onAgree({ message -> getWaiter().fail("AGREE not expected") })
              .onRefuse({ message -> getWaiter().fail("REFUSE not expected") })
              .onFailure({ message -> emitTestEvent("EVENT1") })
              .onFailure({ message -> emitTestEvent("EVENT2") })
              .onInform({ message -> getWaiter().fail("INFORM not expected") })
              .otherwise({ message -> getWaiter().fail("OTHERWISE not expected") })
              .onTimeout(Duration.ofSeconds(10).toMillis(), { getWaiter().fail("timeout not expected") })
              .send()
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1")
    expectOneAndOnlyOneEvent("EVENT2")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testInform() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent())
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService)
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyDelivered(Duration.ofSeconds(5))
              .build()

          prepareRequest(testRequest)
              .onAgree({ message -> getWaiter().fail("AGREE not expected") })
              .onRefuse({ message -> getWaiter().fail("REFUSE not expected") })
              .onFailure({ message -> getWaiter().fail("FAILURE not expected") })
              .onInform({ message ->
                if (message instanceof TestDeliverySucceededNtf) {
                  emitTestEvent("EVENT1")
                } else {
                  getWaiter().fail(String.format("%s not expected", message.getClass().getName()))
                }
              })
              .onInform({ message ->
                if (message instanceof TestDeliverySucceededNtf) {
                  emitTestEvent("EVENT2")
                } else {
                  getWaiter().fail(String.format("%s not expected", message.getClass().getName()))
                }
              })
              .otherwise({ message -> getWaiter().fail("OTHERWISE not expected") })
              .onTimeout(Duration.ofSeconds(10).toMillis(), { getWaiter().fail("timeout not expected") })
              .send()
        }))
      }
    })
  }

  @Test
  void testOtherwise() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent())
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService)
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(5), Performative.NOT_UNDERSTOOD)
              .build()

          prepareRequest(testRequest)
              .onAgree({ message -> getWaiter().fail("AGREE not expected") })
              .onRefuse({ message -> getWaiter().fail("REFUSE not expected") })
              .onFailure({ message -> getWaiter().fail("FAILURE not expected") })
              .onInform({ message -> getWaiter().fail("INFORM not expected") })
              .otherwise({ message -> emitTestEvent("EVENT1") })
              .otherwise({ message -> emitTestEvent("EVENT2") })
              .onTimeout(Duration.ofSeconds(10).toMillis(), { getWaiter().fail("timeout not expected") })
              .send()
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1")
    expectOneAndOnlyOneEvent("EVENT2")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testTimeout() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent())
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService)
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyDelivered(Duration.ofSeconds(5))
              .build()

          prepareRequest(testRequest)
              .onAgree({ message -> getWaiter().fail("AGREE not expected") })
              .onRefuse({ message -> getWaiter().fail("REFUSE not expected") })
              .onFailure({ message -> getWaiter().fail("FAILURE not expected") })
              .onInform({ message -> getWaiter().fail("INFORM not expected") })
              .otherwise({ message -> getWaiter().fail("OTHERWISE not expected") })
              .onTimeout(Duration.ofSeconds(10).toMillis(), { getWaiter().fail("timeout not expected") })
              .onTimeout(Duration.ofSeconds(2).toMillis(), { emitTestEvent("EVENT1") })
              .send()
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testAgreeWait() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent())
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService)
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(5), Performative.AGREE)
              .build()

          final Message response = prepareRequest(testRequest)
              .onAgree({ message -> emitTestEvent("EVENT1") })
              .onAgree({ message -> emitTestEvent("EVENT2") })
              .onRefuse({ message -> getWaiter().fail("REFUSE not expected") })
              .onFailure({ message -> getWaiter().fail("FAILURE not expected") })
              .onInform({ message -> getWaiter().fail("INFORM not expected") })
              .otherwise({ message -> getWaiter().fail("OTHERWISE not expected") })
              .onTimeout(Duration.ofSeconds(10).toMillis(), { getWaiter().fail("timeout not expected") })
              .sendAndWait()

          getWaiter().assertNotNull(response)
          getWaiter().assertEquals(Performative.AGREE, response.getPerformative())
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1")
    expectOneAndOnlyOneEvent("EVENT2")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }
}
