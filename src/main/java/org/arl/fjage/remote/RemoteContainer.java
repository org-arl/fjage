/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import org.arl.fjage.*;

/**
 * Represents a remote container, either master or slave. This class adds interface
 * methods that every remote container should implement.
 *
 * @author Mandar Chitre
 */
abstract class RemoteContainer extends Container {

  //////// Constructors (pass-through)

  RemoteContainer(Platform platform) {
    super(platform);
  }

  RemoteContainer(Platform platform, String name) {
    super(platform, name);
  }

  //////// New interface methods for remote containers

  /**
   * Callback for closure of connection to remote container.
   *
   * @param handler indicates the connection that is closed.
   */
  abstract void connectionClosed(ConnectionHandler handler);

  /**
   * Lists all agents, with subtly different behaviors on master and slave containers.
   * On the master container, this method should be the same as getAgents(). On the
   * slave container, however, this method should only list agents residing in that slave.
   *
   * @return agent ids for all agents.
   */
  abstract AgentID[] getLocalAgents();

  /**
   * Lists all services, with subtly different behaviors on master and slave containers.
   * On the master container, this method should be the same as getServices(). On the
   * slave container, however, this method should only list services residing in that slave.
   *
   * @return list of all services.
   */
  abstract String[] getLocalServices();

  /**
   * Finds an agent providing a named service, with subtly different behaviors on
   * master and slave containers. On the master container, this method should
   * be the same as agentForService(). On the slave container, however, this method
   * should only search agents residing in that slave.
   *
   * @param service name of the service.
   * @return agent id for service provider, null if none found.
   */
  abstract AgentID localAgentForService(String service);        // called to find a local agent that provides a service

  /**
   * Finds a list of agents providing a named service, with subtly different behaviors on
   * master and slave containers. On the master container, this method should
   * be the same as agentForService(). On the slave container, however, this method
   * should only search agents residing in that slave.
   *
   * @param service name of the service.
   * @return agent id for service provider, null if none found.
   */
  abstract AgentID[] localAgentsForService(String service);     // called to find a list of local agents providing a service

}
