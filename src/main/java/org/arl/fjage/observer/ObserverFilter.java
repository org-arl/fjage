/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.observer;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.arl.fjage.Message;
import org.arl.fjage.MessageFilter;

/**
 * Filter used by {@link Observer} to decide which messages to publish to
 * the web interface.
 * <p>
 * The filter is applied in the container, before serialization, and so a
 * message rejected by it is never seen by any web client. It is deliberately
 * an exclusion-oriented filter: it is meant to silence traffic that is known
 * to be uninteresting, so that the cost of serializing it is never paid.
 * Deciding which of the remaining messages to <i>display</i> is left to the
 * web interface, which can do so retrospectively.
 * <p>
 * Regular expressions are matched using {@code find()} semantics, i.e., a
 * substring match is sufficient. Anchor with {@code ^} and {@code $} for an
 * exact match. A null or empty expression is ignored. An invalid expression is
 * logged and ignored, so that a typo in the web interface cannot break
 * observation.
 * <p>
 * The public fields are deliberately simple, so that this class can be
 * populated directly from the JSON sent by the web interface.
 *
 * @author  Mandar Chitre
 */
public class ObserverFilter implements MessageFilter {

  /** Regex that the message class name must match, null to allow all. */
  public String clazz = null;

  /** Regex that the message class name must not match, null to allow all. */
  public String excludeClazz = null;

  /**
   * Endpoint names whose messages are to be dropped. A message is dropped if
   * either its sender or its recipient is named here. Topics are named with
   * their {@code #} prefix, as they are encoded on the wire.
   */
  public Set<String> excludeEndpoints = null;

  private transient Pattern clazzRe = null;
  private transient Pattern excludeClazzRe = null;
  private transient Set<String> excluded = null;
  private transient boolean compiled = false;

  /**
   * Creates a filter that publishes all messages.
   */
  public ObserverFilter() {
    // nothing to do
  }

  /**
   * Compiles the regular expressions. Must be called after the fields are
   * populated, and before the filter is used.
   *
   * @return this filter, for chaining.
   */
  public ObserverFilter compile() {
    clazzRe = compile(clazz);
    excludeClazzRe = compile(excludeClazz);
    excluded = excludeEndpoints == null || excludeEndpoints.isEmpty() ? null
                                                                     : new HashSet<String>(excludeEndpoints);
    compiled = true;
    return this;
  }

  private Pattern compile(String re) {
    if (re == null || re.trim().isEmpty()) return null;
    try {
      return Pattern.compile(re);
    } catch (PatternSyntaxException ex) {
      Logger.getLogger(getClass().getName()).warning("Bad filter regex, ignored: "+re);
      return null;
    }
  }

  /**
   * Sets the endpoints whose messages are to be dropped.
   *
   * @param endpoints endpoint names, null or empty to drop nothing.
   * @return this filter, for chaining.
   */
  public ObserverFilter exclude(String... endpoints) {
    return exclude(endpoints == null ? null : Arrays.asList(endpoints));
  }

  /**
   * Sets the endpoints whose messages are to be dropped.
   *
   * @param endpoints endpoint names, null or empty to drop nothing.
   * @return this filter, for chaining.
   */
  public ObserverFilter exclude(Collection<String> endpoints) {
    excludeEndpoints = endpoints == null ? null : new HashSet<String>(endpoints);
    compiled = false;
    return this;
  }

  @Override
  public boolean matches(Message m) {
    if (!compiled) compile();
    if (clazzRe != null || excludeClazzRe != null) {
      String cls = m.getClass().getName();
      if (clazzRe != null && !clazzRe.matcher(cls).find()) return false;
      if (excludeClazzRe != null && excludeClazzRe.matcher(cls).find()) return false;
    }
    if (excluded != null) {
      if (excluded.contains(Observer.endpoint(m.getSender()))) return false;
      if (excluded.contains(Observer.endpoint(m.getRecipient()))) return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()+"[clazz:"+clazz+", excludeClazz:"+excludeClazz
                                     +", excludeEndpoints:"+excludeEndpoints+"]";
  }

}
