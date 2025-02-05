/**
 * @module fjage
 *
 * This module provides a JavaScript implementation of the Fjage messaging
 * protocol. It provides classes for representing messages, agents, gateways.
 */

import { MessageClass, Message, GenericMessage, ParameterReq } from './message.js';
import { Gateway, init} from './gateway.js';
import { AgentID } from './agentID.js';
import { Services } from './services.js';

init();

export { Gateway, AgentID, Message, MessageClass, GenericMessage, Services, ParameterReq};