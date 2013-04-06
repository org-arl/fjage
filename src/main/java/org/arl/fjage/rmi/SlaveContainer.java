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
  private RemoteContainerProxy proxy = null;

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
    enableRMI();
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
    enableRMI();
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
    try {
      return master.register(aid, service);
    } catch (RemoteException ex) {
      logRemoteException(ex);
      return false;
    }
  }

  @Override
  public synchronized AgentID agentForService(String service) {
    try {
      return master.agentForService(service);
    } catch (RemoteException ex) {
      logRemoteException(ex);
      return null;
    }
  }

  @Override
  public synchronized AgentID[] agentsForService(String service) {
    try {
      return master.agentsForService(service);
    } catch (RemoteException ex) {
      logRemoteException(ex);
      return null;
    }
  }

  @Override
  public synchronized boolean deregister(AgentID aid, String service) {
    try {
      return master.deregister(aid, service);
    } catch (RemoteException ex) {
      logRemoteException(ex);
      return false;
    }
  }

  @Override
  public synchronized void deregister(AgentID aid) {
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
      master.detachSlave(myurl);
    } catch (RemoteException ex) {
      log.warning("Unable to detach from master during shutdown, perhaps master has already shutdown");
    }
    if (proxy != null) {
      try {
        Naming.unbind(myurl);
        UnicastRemoteObject.unexportObject(proxy, true);
        proxy = null;
      } catch (Exception ex) {
        logRemoteException(ex);
      }
    }
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

  private void enableRMI() throws IOException {
    int port = platform.getPort();
    String hostname = platform.getHostname();
    System.setProperty("java.rmi.server.hostname", hostname);
    myurl = "//"+hostname+":"+port+"/fjage/"+name;
    log.info("Container URL: "+myurl);
    try {
      // test if a registry is already running
      Naming.lookup(myurl);
    } catch (ConnectException ex) {
      // if not, start one...
      log.info("Unable to find RMI registry, so starting one...");
      LocateRegistry.createRegistry(port);
    } catch (NotBoundException e) {
      // do nothing, since this is fine
    }
    proxy = new RemoteContainerProxy(this);
    Naming.rebind(myurl, proxy);
  }

  private void attach(String url) throws IOException, NotBoundException {
    master = (RemoteContainer)Naming.lookup(url);
    if (!master.attachSlave(myurl)) throw new RemoteException("Master cannot bind to us");
    log.info("Attached to "+url);
  }

  private void logRemoteException(Exception ex) {
    log.log(Level.WARNING, "Call to master container failed", ex);
  }

}

