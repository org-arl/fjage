/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import org.arl.fjage.*;

/**
 * RMI proxy to avoid having {@link MasterContainer} and {@link SlaveContainer}
 * implement the {@link java.rmi.server.UnicastRemoteObject} interface.
 *
 * @see org.arl.fjage.Container
 *
 * @author Mandar Chitre
 */
public class RemoteContainerProxy extends UnicastRemoteObject implements RemoteContainer {

  private static final long serialVersionUID = 1L;

  /////////// Private attributes

  private RemoteContainer delegate;

  /////////// Constructor

  RemoteContainerProxy(RemoteContainer delegate) throws RemoteException {
    super();
    this.delegate = delegate;
  }

  /////////// Delegated methods

  @Override
  public String getURL() throws RemoteException {
    return delegate.getURL();
  }

  @Override
  public boolean attachSlave(String url) throws RemoteException {
    return delegate.attachSlave(url);
  }

  @Override
  public boolean detachSlave(String url) throws RemoteException {
    return delegate.detachSlave(url);
  }

  @Override
  public boolean containsAgent(AgentID aid) throws RemoteException {
    return delegate.containsAgent(aid);
  }

  @Override
  public boolean register(AgentID aid, String service) throws RemoteException {
    return delegate.register(aid, service);
  }

  @Override
  public boolean deregister(AgentID aid, String service) throws RemoteException {
    return delegate.deregister(aid, service);
  }

  @Override
  public void deregister(AgentID aid) throws RemoteException {
    delegate.deregister(aid);
  }

  @Override
  public AgentID agentForService(String service) throws RemoteException {
    return delegate.agentForService(service);
  }

  @Override
  public AgentID[] agentsForService(String service) throws RemoteException {
    return delegate.agentsForService(service);
  }

  @Override
  public boolean send(Message m, boolean relay) throws RemoteException {
    return delegate.send(m, relay);
  }

  @Override
  public void shutdown() throws RemoteException {
    delegate.shutdown();
  }

}

