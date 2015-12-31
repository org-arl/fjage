/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.json;

import java.lang.reflect.Type;
import java.util.logging.Logger;
import org.arl.fjage.*;
import com.google.gson.*;

/**
 * Message adapter for custom JSON representation.
 *
 * This adapted adds a "msgType" property to the JSON representation of the message,
 * representing the fully qualified message class name. This enables the message to
 * be unmarshalled into the appropriate message class at the destination.
 */
class MessageAdapter implements JsonSerializer<Message>, JsonDeserializer<Message> {

  // TODO add support for GenericMessage

  private static ClassLoader classloader = null;
  private static Gson gson = new GsonBuilder().registerTypeHierarchyAdapter(AgentID.class, new AgentIDAdapter()).create();

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
      rvObj.addProperty("msgType", src.getClass().getName());
    }
    return rv;
  }

  @Override public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    try {
      JsonObject jsonObj =  json.getAsJsonObject();
      JsonPrimitive prim = (JsonPrimitive)jsonObj.get("msgType");
      String className = prim.getAsString();
      Class<?> cls = classloader != null ? Class.forName(className, true, classloader) : Class.forName(className);
      return (Message)gson.fromJson(jsonObj, cls);
    } catch (Exception e) {
      return gson.fromJson(json, typeOfT);
    }
  }

}
