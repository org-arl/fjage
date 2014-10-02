/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.rmi;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.util.logging.Level;

import org.arl.fjage.*;

/**
 * Slave container attached to a master container. Agents in linked
 * master and slave containers function as if they were in the same container,
 * i.e., are able to communicate with each other through messaging, topics and
 * directory services.
 *
 * @author Mandar Chitre
 */
public class SlaveContainer extends Container implements RemoteContainer {

  ////////////// Private attributes

  private RemoteContainer master;
  private String myurl = null;
  private String masterUrl = null;
  private RemoteContainerProxy proxy = null;
  private int containerPort = 0;

  ////////////// Constructors

  /**
   * Creates a slave container.
   * 
   * @param platform platform on which the container runs.
   * @param url URL of master platform to connect to.
   */
  public SlaveContainer(Platform platform, String url) throws IOException, NotBoundException {
    super(platform);
    if (platform.getNetworkInterface() == null) determineNetworkInterface(url);
    enableRMI(true);
    masterUrl = url;
    attach(url);
  }
  
  
  /**
   * Creates a slave container, runs its container stub on a specified port.
   * 
   * @param platform platform on which the container runs.
   * @param port port on which the container's stub runs.
   * @param url URL of master platform to connect to.
   */
  public SlaveContainer(Platform platform, int port, String url) throws IOException, NotBoundException {
	  super(platform);
	  if (platform.getNetworkInterface() == null) determineNetworkInterface(url);
	  containerPort = port;
	  enableRMI(true);
	  masterUrl = url;
	  attach(url);
  }
  
  /**
   * Creates a named slave container.
   * 
   * @param platform platform on which the container runs.
   * @param name name of the container.
   * @param url URL of master platform to connect to.
   */
  public SlaveContainer(Platform platform, String name, String url) throws IOException, NotBoundException {
    super(platform, name);
    if (platform.getNetworkInterface() == null) determineNetworkInterface(url);
    enableRMI(true);
    masterUrl = url;
    attach(url);
  }
  
  /**
   * Creates a named slave container, runs its container stub on a specified port.
   * 
   * @param platform platform on which the container runs.
   * @param name name of the container.
   * @param port port on which the container's stub runs.
   * @param url URL of master platform to connect to.
   */
  public SlaveContainer(Platform platform, String name, int port, String url) throws IOException, NotBoundException {
    super(platform, name);
    if (platform.getNetworkInterface() == null) determineNetworkInterface(url);
    containerPort = port;
    enableRMI(true);
    masterUrl = url;
    attach(url);
  }

  /////////////// Container interface methods to override
  
  @Override
  public String getURL() {
    return myurl;
  }

  @Override
  protected boolean isDuplicate(AgentID aid) {
    if (super.isDuplicate(aid)) return true;
    try {
      if (master == null && !reattach()) return false;
      if (master.containsAgent(aid)) return true;
    } catch (RemoteException ex) {
      logRemoteException(ex);
    }
    return false;
  }

  @Override
  public boolean send(Message m) {
    return send(m, true);
  }

  @Override
  public boolean send(Message m, boolean relay) {
    if (!running) return false;
    if (master == null && !reattach()) return false;
    AgentID aid = m.getRecipient();
    if (aid == null) return false;
    if (aid.isTopic()) {
      if (relay) {
        try {
          return master.send(m, true);
        } catch (RemoteException ex) {
          logRemoteException(ex);
        }
      }
      super.send(m, false);
      return true;
    } else {
      if (super.send(m, false)) return true;
      if (!relay) return false;
      try {
        return master.send(m, true);
      } catch (RemoteException ex) {
        logRemoteException(ex);
        return false;
      }
    }
  }

  @Override
  public synchronized boolean register(AgentID aid, String service) {
    if (master == null && !reattach()) return false;
    try {
      return master.register(aid, service);
    } catch (RemoteException ex) {
      logRemoteException(ex);
      return false;
    }
  }

  @Override
  public synchronized AgentID agentForService(String service) {
    if (master == null && !reattach()) return null;
    try {
      return master.agentForService(service);
    } catch (RemoteException ex) {
      logRemoteException(ex);
      return null;
    }
  }

  @Override
  public synchronized AgentID[] agentsForService(String service) {
    if (master == null && !reattach()) return null;
    try {
      return master.agentsForService(service);
    } catch (RemoteException ex) {
      logRemoteException(ex);
      return null;
    }
  }

  @Override
  public synchronized boolean deregister(AgentID aid, String service) {
    if (master == null && !reattach()) return false;
    try {
      return master.deregister(aid, service);
    } catch (RemoteException ex) {
      logRemoteException(ex);
      return false;
    }
  }

  @Override
  public synchronized void deregister(AgentID aid) {
    if (master == null && !reattach()) return;
    try {
      master.deregister(aid);
    } catch (RemoteException ex) {
      logRemoteException(ex);
    }
  }

  @Override
  public void shutdown() {
    super.shutdown();
    try {
      if (master != null) master.detachSlave(myurl);
    } catch (RemoteException ex) {
      log.warning("Unable to detach from master during shutdown, perhaps master has already shutdown");
    }
    disableRMI();
  }

  @Override
  public boolean attachSlave(String url) {
    throw new UnsupportedOperationException("Cannot attach slave to slave");
  }

  @Override
  public boolean detachSlave(String url) {
    throw new UnsupportedOperationException("Cannot detach slave from slave");
  }
  
  @Override
  public String getState() {
    if (!running) return "Not running";
    if (master == null) return "Running, connecting to "+masterUrl+"...";
    return "Running, connected to "+masterUrl;
  }

  @Override
  public String toString() {
    String s = getClass().getName()+"@"+name;
    s += "/slave/"+platform;
    return s;
  }

  /////////////// Private methods
  
  private void determineNetworkInterface(String url) {
    try {
      URL u = new URL("http:"+url);
      String server = u.getHost();
      int port = u.getPort();
      Socket s = new Socket(server, port);
      InetAddress a = s.getLocalAddress();
      s.close();
      NetworkInterface nif = NetworkInterface.getByInetAddress(a);
      log.info("Binding to network interface "+nif.getDisplayName());
      if (nif != null) platform.setNetworkInterface(nif);
    } catch (Exception ex) {
      log.warning("Could not determine network interface to bind to: "+ex.getMessage());
    }
  }

  private void enableRMI(boolean localNamingOK) throws IOException {
    int port = platform.getPort();
    String hostname = platform.getHostname();
    System.setProperty("java.rmi.server.hostname", hostname);
    myurl = "//"+hostname+":"+port+"/fjage/"+name;
    log.info("Container URL: "+myurl);
    try {
      // test if a registry is already running
      Naming.lookup(myurl);
    } catch (ConnectException ex) {
      // if not, perhaps start one...
      log.info("Unable to find RMI registry...");
      if (localNamingOK) {
        log.info("Starting local RMI registry!");
        LocateRegistry.createRegistry(port);
      }
    } catch (NotBoundException e) {
      // do nothing, since this is fine
    }
    if (containerPort != 0) {
      proxy = new RemoteContainerProxy(this, containerPort);
    }
    else {
      proxy = new RemoteContainerProxy(this);	
    }
    Naming.rebind(myurl, proxy);
  }

  private void disableRMI() {
    try {
      if (proxy != null) UnicastRemoteObject.unexportObject(proxy, true);
      Naming.unbind(myurl);
    } catch (Exception ex) {
      // ignore
    }
    master = null;
    proxy = null;
  }

  private void attach(String url) throws IOException, NotBoundException {
    master = (RemoteContainer)Naming.lookup(url);
    if (!master.attachSlave(myurl)) {
      master = null;
      throw new RemoteException("Master cannot bind to us");
    }
    log.info("Attached to "+url);
  }

  private void logRemoteException(Exception ex) {
    //log.log(Level.WARNING, "Call to master container failed", ex);
    log.warning("Lost connection to master: "+ex.toString());
    disableRMI();
  }

  private boolean reattach() {
    log.info("Trying to reconnect...");
    try {
      enableRMI(false);
      attach(masterUrl);
    } catch (Exception ex1) {
      log.info("Connection failed: "+ex1.toString());
      disableRMI();
      return false;
    }
    return true;
  }

}

