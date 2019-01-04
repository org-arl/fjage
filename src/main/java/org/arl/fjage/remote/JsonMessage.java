/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.arl.fjage.AgentID;
import org.arl.fjage.Message;

/**
 * Class representing a JSON request/response message.
 */
public class JsonMessage {

  String id;
  Action action;
  Action inResponseTo;
  AgentID agentID;
  AgentID[] agentIDs;
  String service;
  String[] services;
  Boolean answer;
  Message message;
  Boolean relay;

  private static GsonBuilder gsonBuilder = new GsonBuilder()
                                               .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                                               .serializeSpecialFloatingPointValues()
                                               .registerTypeHierarchyAdapter(AgentID.class, new AgentIDAdapter())
                                               .registerTypeAdapterFactory(new MessageAdapterFactory())
                                               .registerTypeAdapterFactory(new ArrayAdapterFactory())
                                               .registerTypeAdapterFactory(new GenericValueAdapterFactory())
                                               .enableComplexMapKeySerialization();
  private static Gson gson = gsonBuilder.create();

  public static void addTypeHierarchyAdapter(Class<?> cls, Object adapter) {
    gsonBuilder.registerTypeHierarchyAdapter(cls, adapter);
    gson = gsonBuilder.create();
  }

  static JsonMessage fromJson(String s) {
    return gson.fromJson(s, JsonMessage.class);
  }

  String toJson() {
    return gson.toJson(this);
  }

}
