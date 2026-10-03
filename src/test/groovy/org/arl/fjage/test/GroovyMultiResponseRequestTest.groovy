package org.arl.fjage.test

import org.arl.fjage.*
import org.junit.Test

import java.time.Duration

import static org.arl.fjage.test.AbstractFluentRequestTest.TestDeliverySucceededNtf
import static org.arl.fjage.test.AbstractFluentRequestTest.TestRequest

class GroovyMultiResponseRequestTest
    extends AbstractFluentRequestTest {

  @Test
  void testAgreeThenInform() {
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
              .replyDelivered(Duration.ofSeconds(5))
              .build()

          final Message response = prepareMultiResponseRequest(testRequest)
              .onAgree({ message -> emitTestEvent("AGREE"); false })
              .onInform({ message -> emitTestEvent("INFORM"); message instanceof TestDeliverySucceededNtf })
              .onTimeout(Duration.ofSeconds(8).toMillis(), { getWaiter().fail("timeout not expected") })
              .sendAndWait()

          getWaiter().assertTrue(response instanceof TestDeliverySucceededNtf)
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("AGREE")
    expectOneAndOnlyOneEvent("INFORM")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testHandlerWithoutResultKeepsListening() {
    // ---- given ----
    final AgentID testService = getContainer().add(new TestServiceAgent())
    final TestRequestFactory testRequestFactory = new TestRequestFactory(testService)
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final TestRequest testRequest = testRequestFactory.newBuilder()
              .replyAfter(Duration.ofSeconds(1), Performative.AGREE)
              .replyDelivered(Duration.ofSeconds(1))
              .build()

          prepareMultiResponseRequest(testRequest)
              .onAgree({ message -> emitTestEvent("AGREE") })
              .onInform({ message -> emitTestEvent("INFORM"); true })
              .onTimeout(Duration.ofSeconds(8).toMillis(), { getWaiter().fail("timeout not expected") })
              .send()
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("AGREE")
    expectOneAndOnlyOneEvent("INFORM")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }
}
