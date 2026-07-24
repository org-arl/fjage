package org.arl.fjage.remote;

import org.arl.fjage.*;

/**
 * Notification message sent by {@link Tunnel} when a connector connects or disconnects.
 * <p>
 * This message carries a {@link TunnelStatus} event, a connection ID ({@code connID}), and
 * the name of the connector.
 */
public class TunnelConnectionNtf extends Message {

  private static final long serialVersionUID = 1L;

  private TunnelStatus event;
  private int connID;
  private String name;

  public TunnelConnectionNtf() {
    super();
    setPerformative(Performative.INFORM);
  }

  public TunnelConnectionNtf(TunnelStatus status, int connID, String name) {
    super();
    setPerformative(Performative.INFORM);
    this.event = status;
    this.connID = connID;
    this.name = name;
  }

  public TunnelConnectionNtf(AgentID recipient, TunnelStatus status, int connID, String name) {
    super(recipient);
    setPerformative(Performative.INFORM);
    this.event = status;
    this.connID = connID;
    this.name = name;
  }

  public TunnelStatus getEvent() {
    return event;
  }

  public void setEvent(TunnelStatus status) {
    this.event = status;
  }

  public int getConnID() {
    return connID;
  }

  public void setConnID(int connID) {
    this.connID = connID;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "TunnelConnectionNtf[event="+event+", connID="+connID+", name="+name+"]";
  }

}
