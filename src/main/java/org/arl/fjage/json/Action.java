/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.json;

import com.google.gson.annotations.SerializedName;

/**
 * JSON message actions.
 */
enum Action {
  @SerializedName("containsAgent")    CONTAINS_AGENT,
  @SerializedName("register")         REGISTER,
  @SerializedName("deregister")       DEREGISTER,
  @SerializedName("agentForService")  AGENT_FOR_SERVICE,
  @SerializedName("agentsForService") AGENTS_FOR_SERVICE,
  @SerializedName("send")             SEND,
  @SerializedName("shutdown")         SHUTDOWN;
}
