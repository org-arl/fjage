/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.rmi;

import java.io.IOException;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.logging.Level;

import org.arl.fjage.*;

/**
 * Master container supporting multiple remote slave containers. Agents in linked
 * master and slave containers function as if they were in the same container,
 * i.e., are able to communicate with each other through messaging, topics and
 * directory services.
 *
 * @author Mandar Chitre
 */
public class MasterContainer extends Container implements RemoteContainer {
  
  ////////////// Private attributes

  private Map<String,RemoteContainer> slaves = Collections.synchronizedMap(new HashMap<String,RemoteContainer>());
  private String myurl = null;
  private RemoteContainerProxy proxy = null;
  
  ////////////// Constructors

  /**
   * Creates a master container.
   * 
   * @param platform platform on which the container runs.
   */
  public MasterContainer(Platform platform) throws IOException {
    super(platform);
    enableRMI();
  }

  /**
   * Creates a named master container.
   * 
   * @param platform platform on which the container runs.
   * @param name name of the container.
   */
  public MasterContainer(Platform platform, String name) throws IOException {
    super(platform, name);
    enableRMI();
  }
  
  /////////////// Container interface methods to override
  
  @Override
  public String getURL() {
    return myurl;
  }

  @Override
  protected boolean isDuplicate(AgentID aid) {
    if (super.isDuplicate(aid)) return true;
    if (slaves.size() > 0) {
      Iterator<RemoteContainer> it = slaves.values().iterator();
      while (it.hasNext()) {
        RemoteContainer c = it.next();
        try {
          if (c.containsAgent(aid)) return true;
        } catch (RemoteException ex) {
          logRemoteException(ex);
          it.remove();
        }
      }
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
      super.send(m, false);
      if (!relay) return true;
      if (slaves.size() > 0) {
        Iterator<RemoteContainer> it = slaves.values().iterator();
        while (it.hasNext()) {
          RemoteContainer c = it.next();
          try {
            c.send(m, false);
          } catch (RemoteException ex) {
            logRemoteException(ex);
            it.remove();
          }
        }
      }
      return true;
    } else {
      if (super.send(m, false)) return true;
      if (!relay) return false;
      if (slaves.size() > 0) {
        Iterator<RemoteContainer> it = slaves.values().iterator();
        while (it.hasNext()) {
          RemoteContainer c = it.next();
          try {
            if (c.send(m, false)) return true;
          } catch (RemoteException ex) {
            logRemoteException(ex);
            it.remove();
          }
        }
      }
      return false;
    }
  }

  @Override
  public void shutdown() {
    if (!running) return;
    super.shutdown();
    if (slaves.size() > 0) {
      for (RemoteContainer c: slaves.values()) {
        try {
          c.shutdown();
        } catch (RemoteException ex) {
          logRemoteException(ex);
        }
      }
      slaves.clear();
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
    try {
      RemoteContainer c = (RemoteContainer)Naming.lookup(url);
      slaves.put(url, c);
      log.info("Slave "+url+" attached");
      return true;
    } catch (Exception ex) {
      logRemoteException(ex);
      return false;
    }
  }

  @Override
  public boolean detachSlave(String url) {
    if (slaves.remove(url) != null) {
      log.info("Slave "+url+" detached");
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    String s = getClass().getName()+"@"+name;
    s += "/master/"+platform;
    return s;
  }
  
  /////////////// Private methods
  
  private void enableRMI() throws IOException {
    int port = platform.getPort();
    String hostname = platform.getHostname();
    System.setProperty("java.rmi.server.hostname", hostname);
    myurl = "//"+hostname+":"+port+"/jaf/"+name;
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

  private void logRemoteException(Exception ex) {
    log.log(Level.WARNING, "Call to slave container failed", ex);
  }

}

