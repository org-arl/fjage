/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.lang.reflect.Type;
import com.google.gson.*;

/**
 * Byte array adapter for custom JSON representation.
 *
 * Byte arrays are compressed into a base 64 notation for quick transmission
 * over a network.
 */
class ByteArrayAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

  @Override public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
    String s = Base64.getEncoder().encodeToString(src);
    return new JsonPrimitive(s);
  }

  @Override public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    String s = json.getAsString();
    return Base64.getDecoder().decode(s);
  }

}
