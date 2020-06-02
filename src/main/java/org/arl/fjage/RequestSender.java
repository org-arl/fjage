package org.arl.fjage;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Fluent request sender interface.
 */
public interface RequestSender {

  /**
   * Adds a message consumer to be invoked when an AGREE response is received.
   * There may be more than one message consumer, and all message consumers will be invoked when this condition is met.
   * The Future will be marked as completed once the message consumer(s) are invoked.
   *
   * @param consumer Message consumer.
   * @return This request sender.
   */
  RequestSender onAgree(Consumer<Message> consumer);

  /**
   * Adds a message consumer to be invoked when a REFUSE response is received.
   * There may be more than one message consumer, and all message consumers will be invoked when this condition is met.
   * The Future will be marked as completed once the message consumer(s) are invoked.
   *
   * @param consumer Message consumer.
   * @return This request sender.
   */
  RequestSender onRefuse(Consumer<Message> consumer);

  /**
   * Adds a message consumer to be invoked when a FAILURE response is received.
   * There may be more than one message consumer, and all message consumers will be invoked when this condition is met.
   * The Future will be marked as completed once the message consumer(s) are invoked.
   *
   * @param consumer Message consumer.
   * @return This request sender.
   */
  RequestSender onFailure(Consumer<Message> consumer);

  /**
   * Adds a message consumer to be invoked when an INFORM message is received.
   * There may be more than one message consumer, and all message consumers will be invoked when this condition is met.
   * The Future will be marked as completed once the message consumer(s) are invoked.
   *
   * @param consumer Message consumer.
   * @return This request sender.
   */
  RequestSender onInform(Consumer<Message> consumer);

  /**
   * Adds a Runnable to be invoked when this operation times out.
   * There may be more than one timeout Runnable, but only the one associated with the triggered timeout is invoked.
   * The Future will be marked as completed once the Runnable is invoked.
   *
   * @param timeout  Timeout (ms).
   * @param runnable Runnable.
   * @return This request sender.
   */
  RequestSender onTimeout(long timeout, Runnable runnable);

  /**
   * Adds a message consumer to be invoked when a message that is not one of AGREE, REFUSE, FAILURE, INFORM is received.
   * There may be more than one message consumer, and all message consumers will be invoked when this condition is met.
   * The Future will be marked as completed once the message consumer(s) are invoked.
   *
   * @param consumer Message consumer.
   * @return This request sender.
   */
  RequestSender otherwise(Consumer<Message> consumer);

  /**
   * Sends the message asynchronously.
   * The returned Future will return the response message for any of the message types which have a message consumer associated.
   *
   * @return A Future returning the response message.
   */
  Future<Message> send();

  /**
   * Sends the message synchronously.
   *
   * @return A response message for any of the message types which have a message consumer associated, null if the operation timed out.
   */
  Message sendAndWait();
}
