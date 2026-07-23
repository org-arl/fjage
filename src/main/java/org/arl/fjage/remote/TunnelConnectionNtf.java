package org.arl.fjage.remote;

import org.arl.fjage.*;

/**
 * Notification message sent by `Tunnel` when a connector is opened or closed.
 * <p>
 * This message carries a short `event` string (e.g., "connected", "closed"),
 * the numeric `connID` assigned by the tunnel, the connector `name`.
 */
public class TunnelConnectionNtf extends Message {

  private static final long serialVersionUID = 1L;

  private String event;
  private int connID;
  private String name;

  public TunnelConnectionNtf() {
    super();
    setPerformative(Performative.INFORM);
  }

  public TunnelConnectionNtf(String event, int connID, String name) {
    super();
    setPerformative(Performative.INFORM);
    this.event = event;
    this.connID = connID;
    this.name = name;
  }

  public TunnelConnectionNtf(AgentID recipient, String event, int connID, String name) {
    super(recipient);
    setPerformative(Performative.INFORM);
    this.event = event;
    this.connID = connID;
    this.name = name;
  }

  public String getEvent() {
    return event;
  }

  public void setEvent(String event) {
    this.event = event;
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
