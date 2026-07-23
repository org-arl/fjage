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
  agents,

  /**
   * List of currently connected connection IDs (read-only).
   * Each element is an integer `connID` assigned to a Connector when the
   * connection was established. Used to disambiguate agents with the same
   * name on different containers connected by a tunnel.
   */
  connIDs

}
