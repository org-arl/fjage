/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;
/**
 * Services supported by fjage agents.
 * <br>
 * Agents can be searched based on the services they provide.
 * <br>
 * Example usage:
 * <pre> 
 *  // Agent can register for services it provides
 *  register(Services.SHELL);
 *  
 *  // Agents can be looked up for the provides for services
 *  AgentID stack = agent.agentForService(Services.SHELL); 
 *  
 *  </pre>
 */
public enum Services {
  SHELL
}

