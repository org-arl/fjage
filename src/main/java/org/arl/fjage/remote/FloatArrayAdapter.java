/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Base64;
import java.lang.reflect.Type;
import com.google.gson.*;

/**
 * Float array adapter for custom JSON representation.
 *
 * Float arrays are compressed into a base 64 notation for quick transmission
 * over a network.
 */
class FloatArrayAdapter implements JsonSerializer<float[]>, JsonDeserializer<float[]> {

  @Override public JsonElement serialize(float[] src, Type typeOfSrc, JsonSerializationContext context) {
    ByteBuffer buf = ByteBuffer.allocate(Float.SIZE/Byte.SIZE*src.length);
    buf.asFloatBuffer().put(src);
    String s = Base64.getEncoder().encodeToString(buf.array());
    return new JsonPrimitive(s);
  }

  @Override public float[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    String s = json.getAsString();
    FloatBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(s)).asFloatBuffer();
    float[] array = new float[buf.limit()];
    buf.get(array);
    return array;
  }

}
