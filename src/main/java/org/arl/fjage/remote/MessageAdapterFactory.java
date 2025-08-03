/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.arl.fjage.*;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Message adapter for custom JSON representation.
 *
 * This adapter adds a "clazz" property to the JSON representation of the message,
 * representing the fully qualified message class name. This enables the message to
 * be unmarshalled into the appropriate message class at the destination.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class MessageAdapterFactory implements TypeAdapterFactory {

  private static ClassLoader classloader = null;
  private static final Logger log = Logger.getLogger(MessageAdapterFactory.class.getName());

  static {
    try {
      Class<?> cls = Class.forName("groovy.lang.GroovyClassLoader");
      classloader = (ClassLoader)cls.getDeclaredConstructor().newInstance();
      Logger log = Logger.getLogger(MessageAdapterFactory.class.getName());
      log.info("Groovy detected, using GroovyClassLoader");
    } catch (Exception ex) {
      // do nothing
    }
  }

  @SuppressWarnings("unchecked")
  public <T> TypeAdapter<T> create(final Gson gson, TypeToken<T> type) {
    final Class<T> rawType = (Class<T>)type.getRawType();
    if (!Message.class.isAssignableFrom(rawType)) return null;
    final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
    final TypeAdapter<Performative> perfDelegate = gson.getAdapter(TypeToken.get(Performative.class));
    final TypeAdapter<AgentID> aidDelegate = gson.getAdapter(TypeToken.get(AgentID.class));
    final TypeAdapter<GenericValue> gvDelegate = gson.getAdapter(TypeToken.get(GenericValue.class));
    final MessageAdapterFactory parent = this;
    return new TypeAdapter<T>() {

      @Override
      public void write(JsonWriter out, T value) throws IOException {
        try {
          out.setSerializeNulls(true);
          if (value == null) out.nullValue();
          else {
            out.beginObject();
            out.name("clazz").value(value.getClass().getName());
            out.name("data");
            if (value instanceof GenericMessage) {
              GenericMessage msg = (GenericMessage)value;
              out.beginObject();
              out.name("msgID").value(msg.getMessageID());
              out.name("inReplyTo").value(msg.getInReplyTo());
              out.name("perf");
              perfDelegate.write(out, msg.getPerformative());
              out.name("recipient");
              aidDelegate.write(out, msg.getRecipient());
              out.name("sender");
              aidDelegate.write(out, msg.getSender());
              for (Object k: msg.keySet()) {
                out.name(k.toString());
                Object v = msg.get(k);
                TypeAdapter delegate = gson.getAdapter(TypeToken.get(v.getClass()));
                delegate.write(out, v);
              }
              out.endObject();
            }
            else delegate.write(out, value);
            out.endObject();
          }
        } finally {
          out.setSerializeNulls(false);
        }
      }

      @Override
      public T read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        T rv = null;
        Class<?> cls = null;
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          if (name.equals("clazz")) {
            String className = in.nextString();
            try {
              cls = classloader != null ? Class.forName(className, true, classloader) : Class.forName(className);
            } catch (Exception ex) {
              // do nothing
            }
          } else if (name.equals("data")) {
            if (cls == null) rv = delegate.read(in);
            else if (cls.equals(GenericMessage.class)) {
              GenericMessage msg = new GenericMessage();
              in.beginObject();
              while (in.hasNext()) {
                String fname = in.nextName();
                if (in.peek() == JsonToken.NULL) {
                  in.nextNull();
                  continue;
                }
                switch (fname) {
                  case "msgID":
                    msg.setMessageID(in.nextString());
                    break;
                  case "inReplyTo":
                    msg.setInReplyTo(in.nextString());
                    break;
                  case "perf":
                    msg.setPerformative(perfDelegate.read(in));
                    break;
                  case "recipient":
                    msg.setRecipient(aidDelegate.read(in));
                    break;
                  case "sender":
                    msg.setSender(aidDelegate.read(in));
                    break;
                  default:
                    GenericValue v = gvDelegate.read(in);
                    msg.put(fname, v.getValue());
                    break;
                }
              }
              in.endObject();
              rv = (T) msg;
            } else {
              TypeAdapter<?> delegate1 = gson.getDelegateAdapter(parent, TypeToken.get(cls));
              log.info("Inflating JSONMessage with adapter" + delegate1.getClass().getName() + " for class " + cls.getName());
              rv = (T)delegate1.read(in);
            }
          } else in.skipValue();
        }
        in.endObject();
        return rv;
      }

    };
  }

}
