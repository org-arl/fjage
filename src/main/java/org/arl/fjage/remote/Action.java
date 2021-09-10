/******************************************************************************

Copyright (c) 2015-2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import com.google.gson.annotations.SerializedName;

/**
 * JSON message actions.
 */
public enum Action {
  @SerializedName("auth")             AUTH,
  @SerializedName("agents")           AGENTS,
  @SerializedName("containsAgent")    CONTAINS_AGENT,
  @SerializedName("services")         SERVICES,
  @SerializedName("agentForService")  AGENT_FOR_SERVICE,
  @SerializedName("agentsForService") AGENTS_FOR_SERVICE,
  @SerializedName("send")             SEND,
  @SerializedName("wantsMessagesFor") WANTS_MESSAGES_FOR,
  @SerializedName("shutdown")         SHUTDOWN
}
