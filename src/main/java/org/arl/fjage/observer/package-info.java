/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

/**
 * A message observer for debugging fjåge applications.
 * <p>
 * fjåge offers no direct way to watch agents talk to each other. Logs show
 * what each agent chose to log, and a shell sees only the messages addressed to
 * it. {@link org.arl.fjage.observer.ObserverAgent} fills that gap: added to a
 * container, it observes every message sent in that container and publishes it
 * to a web interface that draws the traffic as a sequence diagram.
 * <pre>
 * container.add("observer", new ObserverAgent());
 * </pre>
 * The web interface is served by the application's own web server if it has
 * one, and on {@link org.arl.fjage.observer.ObserverAgent#DEFAULT_PORT}
 * otherwise. The URL is logged at startup, and readable as the agent's
 * {@code url} parameter.
 * <p>
 * <b>An observer sees only its own container.</b> Messages exchanged between
 * two agents inside a slave container never reach the master, so an observer on
 * the master cannot see them. In a distributed setup, add one observer per
 * container.
 * <p>
 * Observation is transparent: the listener never consumes a message and never
 * throws, so agents behave exactly as they would without it. What the observer
 * reports is messages <i>sent</i> — it runs before delivery is attempted, and
 * so cannot say whether a message was delivered. A message published to a topic
 * appears once, addressed to that topic, not once per subscriber.
 * <p>
 * In the web interface, choosing which endpoints to <i>show</i> is local to the
 * browser and retrospective, revealing messages already buffered there.
 * Choosing to <i>drop</i> an endpoint, or excluding message classes by regular
 * expression, is applied in the container, before serialization: those messages
 * are never sent to any browser, and undoing the rule does not bring them back.
 *
 * @author  Mandar Chitre
 */
package org.arl.fjage.observer;
