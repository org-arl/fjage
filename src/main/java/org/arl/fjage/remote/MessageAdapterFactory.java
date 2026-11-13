/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.arl.fjage.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message adapter for custom JSON representation.
 *
 * This adapter adds a "clazz" property to the JSON representation of the message,
 * representing the fully qualified message class name. This enables the message to
 * be unmarshalled into the appropriate message class at the destination.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class MessageAdapterFactory implements TypeAdapterFactory {

  private static final Set<String> HEADERS = new HashSet<>(Arrays.asList("msgID", "inReplyTo", "perf", "recipient", "sender", "sentAt"));
  private static final JsonClassResolver resolver = new JsonClassResolver(JsonClassResolver.scriptLoader, Message.class);

  @SuppressWarnings("unchecked")
  public <T> TypeAdapter<T> create(final Gson gson, TypeToken<T> type) {
    final Class<T> rawType = (Class<T>)type.getRawType();
    if (!Message.class.isAssignableFrom(rawType)) return null;
    final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
    final TypeAdapter<Performative> perfDelegate = gson.getAdapter(TypeToken.get(Performative.class));
    final TypeAdapter<AgentID> aidDelegate = gson.getAdapter(TypeToken.get(AgentID.class));
    final TypeAdapter<GenericValue> gvDelegate = gson.getAdapter(TypeToken.get(GenericValue.class));
    final TypeAdapter<JsonElement> treeDelegate = gson.getAdapter(JsonElement.class);
    final MessageAdapterFactory parent = this;
    // Gson does not cache delegate adapters
    final Map<Class<?>, TypeAdapter<?>> delegates = new ConcurrentHashMap<>();
    return new TypeAdapter<T>() {

      @Override
      public void write(JsonWriter out, T value) throws IOException {
        // nulls are written inside a message, and the writer's own setting is restored after it
        boolean nulls = out.getSerializeNulls();
        try {
          if (value == null) out.nullValue();
          else {
            out.setSerializeNulls(true);
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
              out.name("sentAt").value(msg.getSentAt());
              for (Object k: msg.keySet()) {
                String name = k.toString();
                if (HEADERS.contains(name)) throw new IllegalArgumentException("GenericMessage key '" + name + "' is a reserved header name");
                out.name(name);
                Object v = msg.get(k);
                if (v == null) {
                  out.nullValue();
                  continue;
                }
                TypeAdapter delegate = gson.getAdapter(TypeToken.get(v.getClass()));
                delegate.write(out, v);
              }
              out.endObject();
            }
            else delegate.write(out, value);
            out.endObject();
          }
        } finally {
          out.setSerializeNulls(nulls);
        }
      }

      @Override
      public T read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        T rv = null;
        String clazz = null;
        Class<?> cls = null;
        JsonElement early = null;
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          if (name.equals("clazz")) {
            clazz = in.nextString();
            cls = resolver.resolve(clazz);
          } else if (name.equals("data")) {
            if (clazz != null) rv = decode(cls, in);
            else early = treeDelegate.read(in);
          } else in.skipValue();
        }
        in.endObject();
        if (early == null) return rv;
        // data arrived before clazz, so decode it again in the usual order
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("clazz", clazz == null ? "" : clazz);
        wrapper.add("data", early);
        return gson.fromJson(wrapper, type);
      }

      // unknown classes fall back to the declared type
      private T decode(Class<?> cls, JsonReader in) throws IOException {
        if (cls == null) return delegate.read(in);
        if (GenericMessage.class.isAssignableFrom(cls)) return (T)readGeneric(cls, in);
        TypeAdapter<?> d = delegates.computeIfAbsent(cls, c -> gson.getDelegateAdapter(parent, TypeToken.get(c)));
        return (T)d.read(in);
      }

      private GenericMessage readGeneric(Class<?> cls, JsonReader in) throws IOException {
        GenericMessage msg = newGeneric(cls);
        in.beginObject();
        while (in.hasNext()) {
          String fname = in.nextName();
          if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            if (!HEADERS.contains(fname)) msg.put(fname, null);
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
            case "sentAt":
              msg.setSentAt(in.nextLong());
              break;
            default:
              msg.put(fname, gvDelegate.read(in).getValue());
              break;
          }
        }
        in.endObject();
        return msg;
      }

    };
  }

  private static GenericMessage newGeneric(Class<?> cls) {
    if (cls == GenericMessage.class) return new GenericMessage();
    try {
      Constructor<?> c = cls.getDeclaredConstructor();
      c.setAccessible(true);
      return (GenericMessage)c.newInstance();
    } catch (ReflectiveOperationException | RuntimeException ex) {
      throw new JsonSyntaxException("Cannot create " + cls.getName() + " without a no-arg constructor", ex);
    }
  }

}
