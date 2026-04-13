package org.arl.fjage.connectors;

public interface FrameListener {

  /**
   * Called when a frame is received over the connection. The type of the frame depends on the
   * connector implementation. For example, a WebSocketConnector will typically receive String
   * or byte[] frames.
   *
   * @param frame
   */
  public void onReceive(Object frame);
}
