/******************************************************************************

Copyright (c) 2021, Nick Ng

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.auth;

import org.apache.commons.lang3.StringUtils;
import org.arl.fjage.AgentID;
import org.arl.fjage.remote.Action;
import org.arl.fjage.remote.JsonMessage;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Simple firewall supplier.
 *
 * This firewall is restrictive and applies access policies on a user-by-user basis.
 *
 * Example usage:
 * <pre>
 * {@code
 *   final SimpleFirewallSupplier simpleFirewallSupplier = new SimpleFirewallSupplier()
 *       .addPolicy("policy1", policy -> policy
 *           .allowedServiceNames("server")
 *           .allowedAgentNames("server1")
 *           .allowedTopicNames("server1__ntf")
 *   )
 *   .addUser("user", "somecreds", "policy1")
 *   .addUser("app", "someappcreds", SimpleFirewallSupplier.POLICY_ID_ALLOW_ALL);
 * }
 * </pre>
 */
public class SimpleFirewallSupplier
    implements Supplier<Firewall> {

  /**
   * Allow all policy ID.
   */
  public static final String POLICY_ID_ALLOW_ALL = "AllowAll";

  private final Map<String, User> userMap = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Policy> policyMap = Collections.synchronizedMap(new HashMap<>());

  /**
   * Add/update a user.
   *
   * @param username User name.
   * @param credentials User credentials.
   * @param policyId ID of policy to be applied to this user.
   * @return This <code>SimpleFirewallSupplier</code> instance.
   */
  public SimpleFirewallSupplier addUser(String username, String credentials, String policyId) {
    final User user = new User(username);
    user.setCredentials(credentials);
    user.setPolicyId(policyId);
    userMap.put(username, user);
    return this;
  }

  /**
   * Add/update a policy.
   *
   * @param policyId Policy ID.
   * @param policyConsumer Policy consumer. Used to configure the policy.
   * @return This <code>SimpleFirewallSupplier</code> instance.
   */
  public SimpleFirewallSupplier addPolicy(String policyId, Consumer<Policy> policyConsumer) {
    if (StringUtils.equals(policyId, POLICY_ID_ALLOW_ALL)) {
      throw new IllegalArgumentException(String.format("%s is a reserved policy ID", policyId));
    }

    final Policy policy = new Policy();
    policyConsumer.accept(policy);
    policyMap.put(policyId, policy);
    return this;
  }

  /**
   * Remove a user.
   *
   * @param username User name.
   */
  public void removeUser(String username) {
    userMap.remove(username);
  }

  /**
   * Remove a policy.
   *
   * @param policyId Policy ID.
   */
  public void removePolicy(String policyId) {
    if (StringUtils.equals(policyId, POLICY_ID_ALLOW_ALL)) {
      throw new IllegalArgumentException(String.format("%s is a reserved policy ID", policyId));
    }
    policyMap.remove(policyId);
  }

  /**
   * Find a user by credentials.
   *
   * @param credentials Credentials.
   * @return <code>User</code> if foumd, <code>null</code> otherwise.
   */
  public User findUserByCredentials(String credentials) {
    if (credentials == null) {
      return null;
    }
    for (final User user : userMap.values()) {
      if (StringUtils.equals(user.getCredentials(), credentials)) {
        return user;
      }
    }
    return null;
  }

  /**
   * Find a policy.
   *
   * @param policyId Policy ID.
   * @return <code>Policy</code> if found, <code>null</code> otherwise.
   */
  public Policy findPolicy(String policyId) {
    if (policyId == null) {
      return null;
    }
    return policyMap.get(policyId);
  }

  @Override
  public Firewall get() {
    return new SimpleFirewall();
  }

  private class SimpleFirewall
      implements Firewall {

    private User user = null;
    private Policy policy = null;
    private boolean allowAll = false;

    @Override
    public boolean authenticate(String creds) {
      user = findUserByCredentials(creds);
      if (user == null) {
        policy = null;
        allowAll = false;
        return false;
      }
      policy = findPolicy(user.getPolicyId());
      allowAll = StringUtils.equals(user.getPolicyId(), POLICY_ID_ALLOW_ALL);
      return true;
    }

    @Override
    public boolean permit(JsonMessage rq) {
      if (allowAll) {
        return true;
      }
      if (policy == null) {
        return false;
      }
      return policy.isPermitted(rq);
    }

    @Override
    public boolean permit(AgentID aid) {
      if (allowAll) {
        return true;
      }
      if (policy == null) {
        return false;
      }
      return policy.isPermitted(aid);
    }

    @Override
    public void signoff() {
      user = null;
      policy = null;
      allowAll = false;
    }

    @Override
    public String toString() {
      if (user != null) {
        return String.format("@%x/%s", hashCode(), user.getUsername());
      } else {
        return String.format("@%x", hashCode());
      }
    }
  }

  /**
   * User information.
   */
  public static class User {

    private final String username;
    private String credentials;
    private String policyId;

    private User(String username) {
      super();

      this.username = username;
    }

    /**
     * Gets the user name.
     *
     * @return User name.
     */
    public String getUsername() {
      return username;
    }

    /**
     * Gets the user credentials.
     *
     * @return User credentials.
     */
    public String getCredentials() {
      return credentials;
    }

    /**
     * Sets the user credentials.
     *
     * @param credentials User credentials.
     */
    public void setCredentials(String credentials) {
      this.credentials = credentials;
    }

    /**
     * Gets the policy ID.
     *
     * @return Policy ID.
     */
    public String getPolicyId() {
      return policyId;
    }

    /**
     * Sets the policy ID.
     *
     * @param policyId Policy ID.
     */
    public void setPolicyId(String policyId) {
      this.policyId = policyId;
    }
  }

  /**
   * Access policy.
   */
  public static class Policy {

    private final Set<String> allowedServiceNames = new HashSet<>();
    private final Set<String> allowedAgentNames = new HashSet<>();
    private final Set<String> allowedTopicNames = new HashSet<>();

    /**
     * Allow <code>agentForService</code>, <code>agentsForService</code> for the specified service names.
     *
     * @param serviceNames Service names.
     * @return This <code>Policy</code> instance.
     */
    public Policy allowedServiceNames(String... serviceNames) {
      allowedServiceNames.addAll(Arrays.asList(serviceNames));
      return this;
    }

    /**
     * Allow <code>send</code> that have the specified agent names specified as a recipient.
     *
     * @param agentNames Agent names.
     * @return This <code>Policy</code> instance.
     */
    public Policy allowedAgentNames(String... agentNames) {
      allowedAgentNames.addAll(Arrays.asList(agentNames));
      return this;
    }

    /**
     * Allow <code>subscribe</code> to the specified topic names.
     *
     * @param topicNames Topic names.
     * @return This <code>Policy</code> instance.
     */
    public Policy allowedTopicNames(String... topicNames) {
      allowedTopicNames.addAll(Arrays.asList(topicNames));
      return this;
    }

    /**
     * Checks whether a message intended for the specified agent/topic may be sent over this connection.
     *
     * @param agentID Agent ID.
     * @return <code>true</code> to accept, <code>false</code> to reject.
     */
    public boolean isPermitted(AgentID agentID) {
      if (agentID.isTopic()) {
        return allowedTopicNames.contains(agentID.getName());
      } else {
        return true;
      }
    }

    /**
     * Checks whether a JSON message can be accepted over this connection.
     *
     * @param jsonMessage <code>JsonMessage</code>
     * @return <code>true</code> to accept, <code>false</code> to reject.
     */
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
