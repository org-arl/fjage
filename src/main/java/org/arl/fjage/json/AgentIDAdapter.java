/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.json;

import java.lang.reflect.Type;
import org.arl.fjage.AgentID;
import com.google.gson.*;

/**
 * AgentID adapter for custom JSON representation.
 *
 * AgentID is represented as a JSON string primitive, with just the name of the agent.
 * For topics, the name is prefixed with a "#". Normal agent names should not begin with
 * a "#" to avoid confusion.
 */
class AgentIDAdapter implements JsonSerializer<AgentID>, JsonDeserializer<AgentID> {

  @Override public JsonElement serialize(AgentID src, Type typeOfSrc, JsonSerializationContext context) {
    if (src.isTopic()) return new JsonPrimitive("#"+src.getName());
    return new JsonPrimitive(src.getName());
  }

  @Override public AgentID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    String s = json.getAsString();
    return s.startsWith("#") ? new AgentID(s.substring(1), true) : new AgentID(s);
  }

}
