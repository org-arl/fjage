/* global Buffer */
import { isBrowser, isNode, isJsDom, isWebWorker } from 'browser-or-node';
import { UUID7 } from './utils.js';
import { AgentID } from './agentid.js';
import { Message } from './message.js';

/**
* Class representing a fjage's on-the-wire JSON message. A JSONMessage object
* contains all the fields that can be a part of a fjage JSON message. The class
* provides methods to create JSONMessage objects from raw strings and to
* convert JSONMessage objects to JSON strings in the format of the fjage on-the-wire
* protocol. See {@link https://fjage.readthedocs.io/en/latest/protocol.html#json-message-request-response-attributes fjage documentation}
* for more details on the JSON message format.
*
* Most users will not need to create JSONMessage objects directly, but rather use the Gateway and Message classes
* to send and receive messages. However, this class can be useful for low-level access to the fjage protocol
* or for generating/consuming the fjåge protocol messages without having them be transmitted over a network.
*
* @example
* const jsonMsg = new JSONMessage();
* jsonMsg.action = 'send';
* jsonMsg.message = new Message();
* jsonMsg.message.sender = new AgentID('agent1');
* jsonMsg.message.recipient = new AgentID('agent2');
* jsonMsg.message.perf = Performative.INFORM;
* jsonMsg.toJSON(); // Converts to JSON string in the fjage on-the-wire protocol format
*
* @example
* const jsonString = '{"id":"1234",...}'; // JSON string representation of a JSONMessage
* const jsonMsg = new JSONMessage(jsonString); // Parses the JSON string into a JSONMessage object
* jsonMsg.message; // Access the Message object contained in the JSONMessage
*
* @class
* @property {string} [id] - A UUID assigned to each JSONMessage object.
* @property {string} [action] - Denotes the main action the object is supposed to perform.
* @property {string} [inResponseTo] - This attribute contains the action to which this object is a response to.
* @property {AgentID} [agentID] - An AgentID. This attribute is populated in objects which are responses to objects requesting the ID of an agent providing a specific service.
* @property {Array<AgentID>} [agentIDs] - This attribute is populated in objects which are responses to objects requesting the IDs of agents providing a specific service, or objects which are responses to objects requesting a list of all agents running in a container.
* @property {Array<string>} [agentTypes] - This attribute is optionally populated in objects which are responses to objects requesting a list of all agents running in a container. If populated, it contains a list of agent types running in the container, with a one-to-one mapping to the agent IDs in the "agentIDs" attribute.
* @property {string} [service] - Used in conjunction with "action" : "agentForService" and "action" : "agentsForService" to query for agent(s) providing this specific service.
* @property {Array<string>} [services] - This attribute is populated in objects which are responses to objects requesting the services available with "action" : "services".
* @property {boolean} [answer] - This attribute is populated in objects which are responses to query objects with "action" : "containsAgent".
* @property {Message} [message] - This holds the main payload of the message. The structure and format of this object is discussed in the {@link https://fjage.readthedocs.io/en/latest/protocol.html#json-message-request-response-attributes fjage documentation}.
* @property {boolean} [relay] - This attribute defines if the target container should relay (forward) the message to other containers it is connected to or not.
* @property {Object} [creds] - Credentials to be used for authentication.
* @property {Object} [auth] - Authentication information to be used for the message.
*
*
*/
export class JSONMessage {

  /**
  * @param {String} [jsonString] - JSON string to be parsed into a JSONMessage object.
  * @param {Object} [owner] - The owner of the JSONMessage object, typically the Gateway instance.
  */
  constructor(jsonString, owner) {
    this.id = UUID7.generate().toString(); // unique JSON message ID
    this.action =  null;
    this.inResponseTo =  null;
    this.agentID = null;
    this.agentIDs = null;
    this.agentTypes = null;
    this.service =  null;
    this.services = null;
    this.answer =  null;
    this.message = null;
    this.relay =  null;
    this.creds =  null;
    this.auth =  null;
    this.name =  null;
    if (jsonString && typeof jsonString === 'string') {
      try {
        const parsed = JSON.parse(jsonString, _decodeBase64);
        if (parsed.message) parsed.message = Message.fromJSON(parsed.message);
        if (parsed.agentID) parsed.agentID = AgentID.fromJSON(parsed.agentID, owner);
        if (parsed.agentIDs) parsed.agentIDs = parsed.agentIDs.map(id => AgentID.fromJSON(id, owner));
        Object.assign(this, parsed);
      } catch (e) {
        throw new Error('Invalid JSON string: ' + e.message);
      }
    };
  }

  /**
  * Creates a JSONMessage object to send a message.
  *
  * @param {Message} msg
  * @param {boolean} [relay=false] - whether to relay the message
  * @returns {JSONMessage} - JSONMessage object with request to send a message
  */
  static createSend(msg, relay=false){
    if (!(msg instanceof Message)) {
      throw new Error('Invalid message type');
    }
    const jsonMsg = new JSONMessage();
    jsonMsg.action = Actions.SEND;
    jsonMsg.relay = relay;
    jsonMsg.message = msg;
    return jsonMsg;
  }

  /**
  * Creates a JSONMessage object to update WantsMessagesFor list.
  *
  * @param {Array<AgentID>} agentIDs - array of AgentID objects for which the gateway wants messages
  * @returns {JSONMessage} - JSONMessage object with request to update WantsMessagesFor list
  */
  static createWantsMessagesFor(agentIDs) {
    if (!Array.isArray(agentIDs) || agentIDs.length === 0) {
      throw new Error('agentIDNames must be a non-empty array');
    }
    const jsonMsg = new JSONMessage();
    jsonMsg.action = Actions.WANTS_MESSAGES_FOR;
    jsonMsg.agentIDs = agentIDs;
    return jsonMsg;
  }

  /**
  * Creates a JSONMessage object to request the list of agents.
  *
  * @returns {JSONMessage} - JSONMessage object with request for the list of agents
  */
  static createAgents(){
    const jsonMsg = new JSONMessage();
    jsonMsg.action = Actions.AGENTS;
    jsonMsg.id = UUID7.generate().toString(); // unique JSON message ID
    return jsonMsg;
  }

  /**
  * Creates a JSONMessage object to check if an agent is contained
  *
  * @param {AgentID} agentID - AgentID of the agent to check
  * @returns {JSONMessage} - JSONMessage object with request to check if the agent is contained
  */
  static createContainsAgent(agentID) {
    if (!(agentID instanceof AgentID)) {
      throw new Error('agentID must be an instance of AgentID');
    }
    const jsonMsg = new JSONMessage();
    jsonMsg.action = Actions.CONTAINS_AGENT;
    jsonMsg.id = UUID7.generate().toString(); // unique JSON message ID
    jsonMsg.agentID = agentID;
    return jsonMsg;
  }

  /**
  * Creates a JSONMessage object to get an agent for a service.
  *
  * @param {string} service - service which the agent must provide
  * @returns {JSONMessage} - JSONMessage object with request for an agent providing the service
  */
  static createAgentForService(service) {
    if (typeof service !== 'string' || service.length === 0) {
      throw new Error('service must be a non-empty string');
    }
    const jsonMsg = new JSONMessage();
    jsonMsg.action = Actions.AGENT_FOR_SERVICE;
    jsonMsg.id = UUID7.generate().toString(); // unique JSON message ID
    jsonMsg.service = service;
    return jsonMsg;
  }

  /**
  * Creates a JSONMessage object to get all agents for a service.
  *
  * @param {string} service - service which the agents must provide
  * @returns {JSONMessage} - JSONMessage object with request for all agent providing the service
  */
  static createAgentsForService(service) {
    if (typeof service !== 'string' || service.length === 0) {
      throw new Error('service must be a non-empty string');
    }
    const jsonMsg = new JSONMessage();
    jsonMsg.action = Actions.AGENTS_FOR_SERVICE;
    jsonMsg.id = UUID7.generate().toString(); // unique JSON message ID
    jsonMsg.service = service;
    return jsonMsg;
  }

  /**
  * Converts the JSONMessage object to a JSON string in the format of the
  * fjage on-the-wire protocol. If the JSONMessage contains a Message or
  * AgentID objects, they will be serialized as per the fjåge protocol.
  *
  * @returns {string} - JSON string representation of the message
  */
  toJSON() {
    if (!this.action && !this.id) {
      throw new Error('Neither action nor id is set. Cannot serialize JSONMessage.');
    }
    const jsonObj = {};
    // Add property if not null or undefined
    if (this.id) jsonObj.id = this.id;
    if (this.action) jsonObj.action = this.action;
    if (this.inResponseTo) jsonObj.inResponseTo = this.inResponseTo;
    if (this.agentID) jsonObj.agentID = this.agentID.toJSON();
    if (this.agentIDs) {
      jsonObj.agentIDs = this.agentIDs.map(id => id.toJSON());
      if (jsonObj.agentIDs.length === 0) delete jsonObj.agentIDs; // remove empty array
    }
    if (this.service) jsonObj.service = this.service;
    if (this.services) {
      jsonObj.services = this.services;
      if (jsonObj.services.length === 0) delete jsonObj.services; // remove empty array
    }
    if (this.answer != undefined) jsonObj.answer = this.answer;
    if (this.message) jsonObj.message = this.message;
    if (this.relay) jsonObj.relay = this.relay;
    if (this.creds) jsonObj.creds = this.creds;
    if (this.auth) jsonObj.auth = this.auth;
    if (this.name) jsonObj.name = this.name;
    return JSON.stringify(jsonObj);
  }

  toString() {
    return this.toJSON();
  }
}


/**
* Actions supported by the fjåge JSON message protocol. See
* {@link https://fjage.readthedocs.io/en/latest/protocol.html#json-message-request-response-attributes fjage documentation} for more details.
*
* @enum {string} Actions
*/
export const Actions = {
  AGENTS : 'agents',
  CONTAINS_AGENT : 'containsAgent',
  SERVICES : 'services',
  AGENT_FOR_SERVICE : 'agentForService',
  AGENTS_FOR_SERVICE : 'agentsForService',
  SEND : 'send',
  WANTS_MESSAGES_FOR : 'wantsMessagesFor',
  SHUTDOWN : 'shutdown'
};

////// private utilities


/**
* Decode large numeric arrays encoded in base64 back to array format.
*
* @private
*
* @param {string} _k - key (unused)
* @param {any} d - data
* @returns {Array} - decoded data in array format
* */
function _decodeBase64(_k, d) {
  if (d === null) return null;
  if (typeof d == 'object' && 'clazz' in d && 'data' in d && d.clazz.startsWith('[') && d.clazz.length == 2) {
    return _b64toArray(d.data, d.clazz) || d;
  }
  return d;
}

/**
* Convert a base64 encoded string to an array of numbers of the specified data type.
*
* @private
*
* @param {string} base64 - base64 encoded string
* @param {string} dtype - data type, e.g. '[B' for byte array, '[S' for short array, etc.
* @param {boolean} [littleEndian=true] - whether to use little-endian byte order
*/
function _b64toArray(base64, dtype, littleEndian=true) {
  let s = _atob(base64);
  let len = s.length;
  let bytes = new Uint8Array(len);
  for (var i = 0; i < len; i++)
    bytes[i] = s.charCodeAt(i);
  let rv = [];
  let view = new DataView(bytes.buffer);
  switch (dtype) {
    case '[B': // byte array
    for (i = 0; i < len; i++)
      rv.push(view.getUint8(i));
    break;
    case '[S': // short array
    for (i = 0; i < len; i+=2)
      rv.push(view.getInt16(i, littleEndian));
    break;
    case '[I': // integer array
    for (i = 0; i < len; i+=4)
      rv.push(view.getInt32(i, littleEndian));
    break;
    case '[J': // long array
    for (i = 0; i < len; i+=8)
      rv.push(view.getBigInt64(i, littleEndian));
    break;
    case '[F': // float array
    for (i = 0; i < len; i+=4)
      rv.push(view.getFloat32(i, littleEndian));
    break;
    case '[D': // double array
    for (i = 0; i < len; i+=8)
      rv.push(view.getFloat64(i, littleEndian));
    break;
    default:
    return;
  }
  return rv;
}

// node.js safe atob function
/**
* @private
* @param {string} a
*/
export function _atob(a){
  if (isBrowser || isWebWorker) return window.atob(a);
  else if (isJsDom || isNode) return Buffer.from(a, 'base64').toString('binary');
}