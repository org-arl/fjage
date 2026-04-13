package org.arl.fjage.connectors;

public interface FrameConnector extends Connector {
  /**
   * Send a frame over the connection. The type of the frame depends on the connector implementation.
   * For example, a WebSocketConnector will typically send String or byte[] frames.
   *
   * @param frame message to send.
   */
  public void sendFrame(Object frame);

  /**
   * Set a frame listener to receive incoming messages. The type of the frame depends on the connector implementation.
   *
   * @param listener listener to call for incoming messages, or null to disable listener.
   */
  public void setFrameListener(FrameListener listener);
}
