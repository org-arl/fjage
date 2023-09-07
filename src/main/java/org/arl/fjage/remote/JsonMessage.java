/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.util.Date;
import com.google.gson.*;
import org.arl.fjage.AgentID;
import org.arl.fjage.Message;
import org.arl.fjage.param.Parameter;

/**
 * Class representing a JSON request/response message.
 */
public class JsonMessage {

  public String id;
  public Action action;
  public Action inResponseTo;
  public AgentID agentID;
  public AgentID[] agentIDs;
  public String[] agentTypes;
  public String service;
  public String[] services;
  public Boolean answer;
  public Message message;
  public Boolean relay;
  public String creds;
  public Boolean auth;
  public String name;

  private static GsonBuilder gsonBuilder = new GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
    .registerTypeAdapter(Float.class, (JsonSerializer<Float>) (value, type, jsonSerializationContext) -> value.isNaN()?null:new JsonPrimitive(value))
    .registerTypeAdapter(Double.class, (JsonSerializer<Double>) (value, type, jsonSerializationContext) -> value.isNaN()?null:new JsonPrimitive(value))
    .registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (json, typeOfT, context) -> new Date(json.getAsJsonPrimitive().getAsLong()))
    .registerTypeAdapter(Date.class, (JsonSerializer<Date>) (date, type, jsonSerializationContext) -> new JsonPrimitive(date.getTime()))
    .registerTypeHierarchyAdapter(AgentID.class, new AgentIDAdapter())
    .registerTypeHierarchyAdapter(Parameter.class, new EnumTypeAdapter())
    .registerTypeAdapterFactory(new MessageAdapterFactory())
    .registerTypeAdapterFactory(new ArrayAdapterFactory())
    .registerTypeAdapterFactory(new GenericValueAdapterFactory())
    .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
    .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
    .serializeSpecialFloatingPointValues()
    .enableComplexMapKeySerialization();

  private static Gson gson = gsonBuilder.create();

  public static void addTypeHierarchyAdapter(Class<?> cls, Object adapter) {
    gsonBuilder.registerTypeHierarchyAdapter(cls, adapter);
    gson = gsonBuilder.create();
  }

  public static JsonMessage fromJson(String s) {
    return gson.fromJson(s, JsonMessage.class);
  }

  public String toJson() {
    return gson.toJson(this);
  }

}
