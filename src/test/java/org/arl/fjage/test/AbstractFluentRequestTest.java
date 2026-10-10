package org.arl.fjage.test;

import org.arl.fjage.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AbstractFluentRequestTest
    extends AbstractSimulatorTest {

  protected static class TestDeliveryFailedNtf
      extends Message {

    private static final long serialVersionUID = 1L;

    public TestDeliveryFailedNtf(Message request) {
      super(request, Performative.INFORM);
    }
  }

  protected static class TestDeliverySucceededNtf
      extends Message {

    private static final long serialVersionUID = 1L;

    public TestDeliverySucceededNtf(Message request) {
      super(request, Performative.INFORM);
    }
  }

  private record TestReplyEntry(Duration duration, TestReplyType type, Performative performative) {
    static TestReplyEntry generic(Duration duration, Performative performative) {
      return new TestReplyEntry(duration, TestReplyType.GENERIC, performative);
    }
    static TestReplyEntry delivered(Duration duration) {
      return new TestReplyEntry(duration, TestReplyType.DELIVERED, null);
    }
    static TestReplyEntry deliveryFailed(Duration duration) {
      return new TestReplyEntry(duration, TestReplyType.DELIVERY_FAILED, null);
    }
  }

  private enum TestReplyType {

    GENERIC,
    DELIVERED,
    DELIVERY_FAILED,
  }

  protected static class TestRequest
      extends Message {

    private static final long serialVersionUID = 1L;

    private final List<TestReplyEntry> testReplyEntryList;

    public TestRequest(AgentID recipient) {
      this(recipient, null);
    }

    public TestRequest(AgentID recipient, List<TestReplyEntry> testReplyEntryList) {
      super();

      setRecipient(recipient);
      this.testReplyEntryList = testReplyEntryList == null ? null : List.copyOf(testReplyEntryList);
    }

    public List<TestReplyEntry> getTestReplyEntryList() {
      return testReplyEntryList;
    }
  }

  protected static class TestRequestFactory {

    private final AgentID agentId;

    public TestRequestFactory(AgentID agentId) {
      super();

      this.agentId = agentId;
    }

    public Builder newBuilder() {
      return new Builder();
    }

    public class Builder {

      private final List<TestReplyEntry> testReplyEntryList = new ArrayList<>();

      public Builder replyAfter(Duration duration, Performative performative) {
        testReplyEntryList.add(TestReplyEntry.generic(duration, performative));
        return this;
      }

      public Builder replyDelivered(Duration duration) {
        testReplyEntryList.add(TestReplyEntry.delivered(duration));
        return this;
      }

      public Builder replyDeliveryFailed(Duration duration) {
        testReplyEntryList.add(TestReplyEntry.deliveryFailed(duration));
        return this;
      }

      public TestRequest build() {
        return new TestRequest(agentId, testReplyEntryList);
      }
    }
  }

  protected static class TestServiceAgent
      extends Agent {

    @Override
    protected void init() {
      super.init();

      register("TEST");

      add(new MessageBehavior(TestRequest.class) {

        @Override
        public void onReceive(Message message) {
          if (message instanceof TestRequest) {
            final TestRequest request = (TestRequest) message;
            handle(request, request.getTestReplyEntryList());
          }
        }
      });
    }

    private void handle(Message request, List<TestReplyEntry> testReplyEntryList) {
      if ((testReplyEntryList == null) || testReplyEntryList.isEmpty()) {
        return;
      }
      final TestReplyEntry entry = testReplyEntryList.get(0);
      add(new CustomWakerBehavior(entry.duration().toMillis(), request, testReplyEntryList));
    }

    private class CustomWakerBehavior
        extends WakerBehavior {

      private final Message request;
      private final List<TestReplyEntry> testReplyEntryList;

      public CustomWakerBehavior(long millis, Message request, List<TestReplyEntry> testReplyEntryList) {
        super(millis);

        this.request = request;
        this.testReplyEntryList = testReplyEntryList == null ? null : List.copyOf(testReplyEntryList);
      }

      @Override
      public void onWake() {
        final TestReplyEntry entry = testReplyEntryList.get(0);
        Message reply = switch (entry.type()) {
          case GENERIC -> new Message(request, entry.performative());
          case DELIVERED -> new TestDeliverySucceededNtf(request);
          case DELIVERY_FAILED -> new TestDeliveryFailedNtf(request);
        };
        if (entry.performative() != null) reply.setPerformative(entry.performative());
        send(reply);
        handle(request, testReplyEntryList.subList(1, testReplyEntryList.size()));
      }
    }
  }
}
