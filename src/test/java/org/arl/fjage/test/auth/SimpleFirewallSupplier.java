/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test.auth;

import org.arl.fjage.AgentID;
import org.arl.fjage.auth.Firewall;
import org.arl.fjage.remote.Action;
import org.arl.fjage.remote.JsonMessage;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimpleFirewallSupplier
    implements Supplier<Firewall> {

  private final Map<String, UserConfiguration> userConfigurationMap = Collections.synchronizedMap(new HashMap<>());

  public SimpleFirewallSupplier addUserConfiguration(String credentials,
                                                     Consumer<UserConfiguration> userConfigurationConsumer) {
    final UserConfiguration userConfiguration = new UserConfiguration();
    userConfigurationConsumer.accept(userConfiguration);
    userConfigurationMap.put(credentials, userConfiguration);
    return this;
  }

  public UserConfiguration getUserConfiguration(String credentials) {
    return userConfigurationMap.get(credentials);
  }

  @Override
  public Firewall get() {
    return new SimpleFirewall();
  }

  private class SimpleFirewall
      implements Firewall {

    private String creds = null;
    private UserConfiguration userConfiguration = null;

    @Override
    public boolean authenticate(String creds) {
      final UserConfiguration userConfiguration = getUserConfiguration(creds);
      if (userConfiguration != null) {
        this.creds = creds;
        this.userConfiguration = userConfiguration;
        return true;
      } else {
        this.creds = null;
        this.userConfiguration = null;
        return false;
      }
    }

    @Override
    public boolean permit(JsonMessage rq) {
      if (userConfiguration == null) {
        return false;
      }
      return userConfiguration.isPermitted(rq);
    }

    @Override
    public boolean permit(AgentID aid) {
      if (userConfiguration == null) {
        return false;
      }
      return userConfiguration.isPermitted(aid);
    }

    @Override
    public String toString() {
      if (creds != null)  {
        return String.format("@%x/%s", hashCode(), creds);
      } else {
        return String.format("@%x", hashCode());
      }
    }
  }

  public static class UserConfiguration {

    private final Set<String> allowedServiceNames = new HashSet<>();
    private final Set<String> allowedAgentNames = new HashSet<>();
    private final Set<String> allowedTopicNames = new HashSet<>();

    public UserConfiguration allowedServiceNames(String... serviceNames) {
      allowedServiceNames.addAll(Arrays.asList(serviceNames));
      return this;
    }

    public UserConfiguration allowedAgentNames(String... agentNames) {
      allowedAgentNames.addAll(Arrays.asList(agentNames));
      return this;
    }

    public UserConfiguration allowedTopicNames(String... topicNames) {
      allowedTopicNames.addAll(Arrays.asList(topicNames));
      return this;
    }

    public boolean isPermitted(AgentID agentID) {
      if (agentID.isTopic()) {
        return allowedTopicNames.contains(agentID.getName());
      } else {
        return true;
      }
    }

    public boolean isPermitted(JsonMessage jsonMessage) {
      if ((jsonMessage.action == Action.AGENT_FOR_SERVICE) || (jsonMessage.action == Action.AGENTS_FOR_SERVICE)) {
        return allowedServiceNames.contains(jsonMessage.service);
      } else if (jsonMessage.action == Action.SEND) {
        return allowedAgentNames.contains(jsonMessage.message.getRecipient().getName());
      } else {
        return true;
      }
    }
  }
}
