package org.arl.fjage;

import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * Fluent request sender interface for requests that may receive several responses.
 * <p>
 * Each handler returns true to end the request, or false to keep listening for
 * further responses. If more than one handler is added for a condition, all of them
 * are invoked, and the request ends if any of them returns true.
 * <p>
 * If no handler is added for a condition, a REFUSE or FAILURE response ends the
 * request, and any other response is ignored.
 * <p>
 * Every response to the request restarts all timeouts.
 */
public interface MultiResponseRequestSender {

  /**
   * Adds a handler to be invoked when an AGREE response is received.
   *
   * @param handler Message handler, returning true to end the request.
   * @return This request sender.
   */
  MultiResponseRequestSender onAgree(Predicate<Message> handler);

  /**
   * Adds a handler to be invoked when a REFUSE response is received.
   *
   * @param handler Message handler, returning true to end the request.
   * @return This request sender.
   */
  MultiResponseRequestSender onRefuse(Predicate<Message> handler);

  /**
   * Adds a handler to be invoked when a FAILURE response is received.
   *
   * @param handler Message handler, returning true to end the request.
   * @return This request sender.
   */
  MultiResponseRequestSender onFailure(Predicate<Message> handler);

  /**
   * Adds a handler to be invoked when an INFORM message is received.
   *
   * @param handler Message handler, returning true to end the request.
   * @return This request sender.
   */
  MultiResponseRequestSender onInform(Predicate<Message> handler);

  /**
   * Adds a handler to be invoked when a message that is not one of AGREE, REFUSE, FAILURE, INFORM is received.
   *
   * @param handler Message handler, returning true to end the request.
   * @return This request sender.
   */
  MultiResponseRequestSender otherwise(Predicate<Message> handler);

  /**
   * Adds a Runnable to be invoked when no response is received for the specified time.
   * The time is counted from when the request is sent, and restarts on every response.
   * There may be more than one timeout Runnable, but only the first one to expire is invoked.
   * The request ends once the Runnable is invoked.
   *
   * @param timeout  Timeout (ms).
   * @param runnable Runnable.
   * @return This request sender.
   */
  MultiResponseRequestSender onTimeout(long timeout, Runnable runnable);

  /**
   * Sends the message asynchronously.
   *
   * @return A Future returning the response message that ended the request, null if the request timed out.
   */
  Future<Message> send();

  /**
   * Sends the message synchronously.
   *
   * @return The response message that ended the request, null if the request timed out.
   */
  Message sendAndWait();
}
