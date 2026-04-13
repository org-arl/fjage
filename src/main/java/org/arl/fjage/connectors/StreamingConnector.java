package org.arl.fjage.connectors;

import java.io.InputStream;
import java.io.OutputStream;

public interface StreamingConnector extends Connector{
  /**
   * Get the input stream to read data over.
   */
  public InputStream getInputStream();

  /**
   * Get the output stream to write data to.
   */
  public OutputStream getOutputStream();
}
