/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Base64;
import java.lang.reflect.Type;
import com.google.gson.*;

/**
 * Integer array adapter for custom JSON representation.
 *
 * Integer arrays are compressed into a base 64 notation for quick transmission
 * over a network.
 */
class IntegerArrayAdapter implements JsonSerializer<int[]>, JsonDeserializer<int[]> {

  @Override public JsonElement serialize(int[] src, Type typeOfSrc, JsonSerializationContext context) {
    ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE/Byte.SIZE*src.length);
    buf.asIntBuffer().put(src);
    String s = Base64.getEncoder().encodeToString(buf.array());
    return new JsonPrimitive(s);
  }

  @Override public int[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    String s = json.getAsString();
    IntBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(s)).asIntBuffer();
    int[] array = new int[buf.limit()];
    buf.get(array);
    return array;
  }

}
