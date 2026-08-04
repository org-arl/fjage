/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.observer;

import org.arl.fjage.param.Parameter;

/**
 * Parameters supported by {@link ObserverAgent}, so that an observer can be
 * inspected and controlled from a shell or over a gateway.
 *
 * @author  Mandar Chitre
 */
public enum ObserverParam implements Parameter {

  /** Whether messages are being observed. */
  enabled,

  /** Number of messages published so far. */
  count,

  /** Number of messages dropped by the rate limiter so far. */
  dropped,

  /** Maximum number of messages published per second, 0 for no limit. */
  maxRate,

  /** URL of the web interface. */
  url,

  /** Number of web clients currently connected. */
  connections,

  /** Endpoints (agents and topics) seen so far. */
  endpoints

}
