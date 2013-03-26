/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;


/**
 * An action represented by a message. The performative actions are a subset of the
 * FIPA ACL recommendations for interagent communication.
 *
 * @author  Mandar Chitre
 * @version $Revision: 9110 $, $Date: 2012-06-15 22:50:14 +0800 (Fri, 15 Jun 2012) $
 */
public enum Performative {

  /**
   * Request an action to be performed.
   */
  REQUEST,

  /**
   * Agree to performing the requested action.
   */
  AGREE,

  /**
   * Refuse to perform the requested action.
   */
  REFUSE,

  /**
   * Notification of failure to perform a requested or agreed action.
   */
  FAILURE,

  /**
   * Notification of an event.
   */
  INFORM,

  /**
   * Confirm that the answer to a query is true.
   */
  CONFIRM,

  /**
   * Confirm that the answer to a query is false.
   */
  DISCONFIRM,

  /**
   * Query if some statement is true or false.
   */
  QUERY_IF,

  /**
   * Notification that a message was not understood.
   */
  NOT_UNDERSTOOD,

  /**
   * Call for proposal.
   */
  CFP,

  /**
   * Response for CFP.
   */
  PROPOSE,

  /**
   * Cancel pending request.
   */
  CANCEL

}

