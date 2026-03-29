package org.arl.fjage.remote;

import org.arl.fjage.param.Parameter;

public enum TunnelParam implements Parameter {

  /**
   * IP address for the tunnel. In case of a client tunnel, this is the IP
   * address of the server to connect to. In case of a server tunnel, this is
   * set to null.
   */
  ip,

  /**
   * TCP port number for the tunnel. In case of a client tunnel, this is the
   * TCP port number of the server to connect to. In case of a server tunnel,
   * this is the TCP port number to listen on for incoming client connections.
   */
  port,

  /**
   * List of remote agents/topics visible through the tunnel.
   */
  agents

}
