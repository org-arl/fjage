/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Base64;
import java.lang.reflect.Type;
import com.google.gson.*;

/**
 * Double array adapter for custom JSON representation.
 *
 * Double arrays are compressed into a base 64 notation for quick transmission
 * over a network.
 */
class DoubleArrayAdapter implements JsonSerializer<double[]>, JsonDeserializer<double[]> {

  @Override public JsonElement serialize(double[] src, Type typeOfSrc, JsonSerializationContext context) {
    ByteBuffer buf = ByteBuffer.allocate(Double.SIZE/Byte.SIZE*src.length);
    buf.asDoubleBuffer().put(src);
    String s = Base64.getEncoder().encodeToString(buf.array());
    return new JsonPrimitive(s);
  }

  @Override public double[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    String s = json.getAsString();
    DoubleBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(s)).asDoubleBuffer();
    double[] array = new double[buf.limit()];
    buf.get(array);
    return array;
  }

}
