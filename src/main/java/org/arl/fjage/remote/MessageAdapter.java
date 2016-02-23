/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.logging.Logger;
import org.arl.fjage.*;
import com.google.gson.*;

/**
 * Message adapter for custom JSON representation.
 *
 * This adapter adds a "msgType" property to the JSON representation of the message,
 * representing the fully qualified message class name. This enables the message to
 * be unmarshalled into the appropriate message class at the destination.
 *
 * The adapter also adds special support for {@link org.arl.fjage.GenericMessage} messages
 * so that the map key-value pairs are correctly retained in JSON.
 */
class MessageAdapter implements JsonSerializer<Message>, JsonDeserializer<Message> {

  private static ClassLoader classloader = null;
  private static Gson gson = new GsonBuilder()
                                  .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                                  .serializeSpecialFloatingPointValues()
                                  .registerTypeHierarchyAdapter(AgentID.class, new AgentIDAdapter())
                                  .create();

  static {
    try {
      Class<?> cls = Class.forName("groovy.lang.GroovyClassLoader");
      classloader = (ClassLoader)cls.newInstance();
      Logger log = Logger.getLogger(MessageAdapter.class.getName());
      log.info("Groovy detected, using GroovyClassLoader");
    } catch (Exception ex) {
      // do nothing
    }
  }

  @Override public JsonElement serialize(Message src, Type typeOfSrc, JsonSerializationContext context) {
    JsonElement rv = gson.toJsonTree(src);
    if (rv.isJsonObject()) {
      JsonObject rvObj = rv.getAsJsonObject();
      Class<?> cls = src.getClass();
      if (cls == GenericMessage.class) {
        JsonElement map = rv;
        rv = gson.toJsonTree(src, Message.class);
        rvObj = rv.getAsJsonObject();
        rvObj.add("map", map);
      }
      rvObj.addProperty("msgType", cls.getName());
    }
    return rv;
  }

  @Override public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    try {
      JsonObject jsonObj =  json.getAsJsonObject();
      JsonPrimitive prim = (JsonPrimitive)jsonObj.get("msgType");
      String className = prim.getAsString();
      Class<?> cls = classloader != null ? Class.forName(className, true, classloader) : Class.forName(className);
      if (cls != GenericMessage.class) return (Message)gson.fromJson(jsonObj, cls);
      GenericMessage rv = new GenericMessage();
      Message m = (Message)gson.fromJson(jsonObj, Message.class);
      rv.setMessageID(m.getMessageID());
      rv.setInReplyTo(m.getInReplyTo());
      rv.setPerformative(m.getPerformative());
      rv.setRecipient(m.getRecipient());
      rv.setSender(m.getSender());
      rv.putAll((Map<?,?>)gson.fromJson(jsonObj.get("map"), Map.class));
      return rv;
    } catch (Exception e) {
      return gson.fromJson(json, typeOfT);
    }
  }

}
