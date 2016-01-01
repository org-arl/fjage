/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.rmi;

import java.rmi.*;
import org.arl.fjage.*;

/**
 * RMI interface for remote containers.
 *
 * @deprecated As of release 1.4, this interface is no longer valid.
 *
 * @author Mandar Chitre
 */
@Deprecated
public interface RemoteContainer extends Remote {

  /**
   * Gets the remote access URL of the container.
   *
   * @return the URL of the container.
   */
  public String getURL() throws RemoteException;

  /**
   * Attaches a slave container to the master container. Implemented only by
   * master containers.
   *
   * @param url URL of the slave container.
   * @return true on success, false otherwise.
   */
  public boolean attachSlave(String url) throws RemoteException;

  /**
   * Detaches a slave container from the master container. Implemented only by
   * master containers.
   *
   * @param url URL of the slave container.
   * @return true on success, false otherwise.
   */
  public boolean detachSlave(String url) throws RemoteException;

  /**
   * Checks if an agent exists in the container.
   *
   * @param aid agent id to check.
   * @return true if the agent exists, false otherwise.
   */
  public boolean containsAgent(AgentID aid) throws RemoteException;

  /**
   * Registers an agent in the directory service as a provider of a named service.
   *
   * @param aid id of agent providing the service.
   * @param service name of the service.
   * @return true on success, false on failure.
   */
  public boolean register(AgentID aid, String service) throws RemoteException;

  /**
   * Deregisters an agent as a provider of a specific service.
   *
   * @param aid id of agent to deregister.
   * @param service name of the service to deregister.
   * @return true on success, false on failure.
   */
  public boolean deregister(AgentID aid, String service) throws RemoteException;

  /**
   * Deregisters an agent as a provider of all services.
   *
   * @param aid id of agent to deregister.
   */
  public void deregister(AgentID aid) throws RemoteException;

  /**
   * Finds an agent providing a named service.
   *
   * @param service name of the service.
   * @return agent id for service provider, null if none found.
   */
  public AgentID agentForService(String service) throws RemoteException;

  /**
   * Finds all agents providing a named service.
   *
   * @param service name of the service.
   * @return an array of agent ids for service providers, null if none found.
   */
  public AgentID[] agentsForService(String service) throws RemoteException;

  /**
   * Sends a message. The message is sent to the recipient specified in the
   * message.
   *
   * @param m message to deliver
   * @param relay enable relaying to associated remote containers.
   * @return true if delivered, false otherwise.
   */
  public boolean send(Message m, boolean relay) throws RemoteException;

  /**
   * Terminates the container and all agents in it.
   */
  public void shutdown() throws RemoteException;

}

