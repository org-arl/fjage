/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import com.google.gson.JsonSyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Looks up classes named on the wire, and caches the ones found. A class is
 * initialized only after it is checked to be a subtype of the expected type.
 * Misses are not cached, since Groovy classes can appear later.
 */
final class JsonClassResolver {

  /** GroovyClassLoader when Groovy is available, else the default class loader. */
  static final ClassLoader scriptLoader = createScriptLoader();

  private final ClassLoader loader;
  private final Class<?> type;
  private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();

  JsonClassResolver(ClassLoader loader, Class<?> type) {
    this.loader = loader;
    this.type = type;
  }

  /**
   * Gets the named class, or null if it is unknown.
   *
   * @throws JsonSyntaxException if the class is not a subtype of the expected type.
   */
  Class<?> resolve(String name) {
    Class<?> cls = classes.get(name);
    if (cls != null) return cls;
    return accept(name, find(name));
  }

  /**
   * Gets the named class like {@link #resolve(String)}, also accepting nested classes
   * written with dots, such as {@code pkg.Outer.Inner} for {@code pkg.Outer$Inner}.
   * Beyond one level of nesting, only capitalized names are tried as outer classes.
   */
  Class<?> resolveNested(String name) {
    Class<?> cls = classes.get(name);
    if (cls != null) return cls;
    cls = find(name);
    int last = name.lastIndexOf('.');
    // the outer class is loaded first, since Groovy only defines inner classes when it compiles the outer one
    for (int i = last; cls == null && i > 0; i = name.lastIndexOf('.', i-1)) {
      String outer = name.substring(0, i);
      if (i < last && !Character.isUpperCase(outer.charAt(outer.lastIndexOf('.')+1))) break;
      if (find(outer) == null) continue;
      cls = find(outer + '$' + name.substring(i+1).replace('.', '$'));
      break;
    }
    return accept(name, cls);
  }

  private Class<?> find(String name) {
    try {
      return Class.forName(name, false, loader);
    } catch (Exception | LinkageError ex) {
      return null;
    }
  }

  private Class<?> accept(String name, Class<?> cls) {
    if (cls == null) return null;
    if (!type.isAssignableFrom(cls)) throw new JsonSyntaxException("Class " + cls.getName() + " is not a " + type.getName());
    try {
      Class.forName(cls.getName(), true, loader);
    } catch (Exception | LinkageError ex) {
      return null;
    }
    classes.put(name, cls);
    return cls;
  }

  private static ClassLoader createScriptLoader() {
    try {
      ClassLoader groovy = (ClassLoader)Class.forName("groovy.lang.GroovyClassLoader").getDeclaredConstructor().newInstance();
      Logger.getLogger(JsonClassResolver.class.getName()).info("Groovy detected, using GroovyClassLoader");
      return groovy;
    } catch (Exception ex) {
      return JsonClassResolver.class.getClassLoader();
    }
  }

}
