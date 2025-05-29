import { Performative } from './performative.js';
import { ParameterReq  } from './message.js';
import { Gateway } from './gateway.js';  // import Gateway class for type checking. Remove if not needed.
import { Message } from './message.js';  // import Message class for type checking. Remove if not needed.

/**
* An identifier for an agent or a topic.
* @class
* @param {string} name - name of the agent
* @param {boolean} [topic=false] - name of topic
* @param {Gateway} [owner] - Gateway owner for this AgentID
*/
export class AgentID {

  constructor(name, topic=false, owner) {
    this.name = name;
    this.topic = topic;
    this.owner = owner;
  }

  /**
  * Gets the name of the agent or topic.
  *
  * @returns {string} - name of agent or topic
  */
  getName() {
    return this.name;
  }

  /**
  * Returns true if the agent id represents a topic.
  *
  * @returns {boolean} - true if the agent id represents a topic, false if it represents an agent
  */
  isTopic() {
    return this.topic;
  }

  /**
  * Sends a message to the agent represented by this id.
  *
  * @param {Message} msg - message to send
  * @returns {void}
  */
  send(msg) {
    msg.recipient = this.toJSON();
    if (this.owner) this.owner.send(msg);
    else throw new Error('Unowned AgentID cannot send messages');
  }

  /**
  * Sends a request to the agent represented by this id and waits for a reponse.
  *
  * @param {Message} msg - request to send
  * @param {number} [timeout=1000] - timeout in milliseconds
  * @returns {Promise<Message>} - response
  */
  async request(msg, timeout=1000) {
    msg.recipient = this.toJSON();
    if (this.owner) return this.owner.request(msg, timeout);
    else throw new Error('Unowned AgentID cannot send messages');
  }

  /**
  * Gets a string representation of the agent id.
  *
  * @returns {string} - string representation of the agent id
  */
  toString() {
    return this.toJSON() + ((this.owner && this.owner.connector) ? ` on ${this.owner.connector.url}` : '');
  }

  /**
  * Gets a JSON string representation of the agent id.
  *
  * @returns {string} - JSON string representation of the agent id
  */
  toJSON() {
    return (this.topic ? '#' : '') + this.name;
  }

  /**
  * Sets parameter(s) on the Agent referred to by this AgentID.
  *
  * @param {(string|string[])} params - parameters name(s) to be set
  * @param {(Object|Object[])} values - parameters value(s) to be set
  * @param {number} [index=-1] - index of parameter(s) to be set
  * @param {number} [timeout=5000] - timeout for the response
  * @returns {Promise<(Object|Object[])>} - a promise which returns the new value(s) of the parameters
  */
  async set (params, values, index=-1, timeout=5000) {
    if (!params) return null;
    let msg = new ParameterReq();
    msg.recipient = this.toJSON();
    if (Array.isArray(params)){
      if (params.length != values.length) throw new Error(`Parameters and values arrays must have the same length: ${params.length} != ${values.length}`);
      const clonedParams = params.slice(); // Clone the array to avoid side effects
      const clonedValues = values.slice(); // Clone the values array
      msg.param = clonedParams.shift();
      msg.value = clonedValues.shift();
      msg.requests = clonedParams.map((p, i) => {
        return {
          'param': p,
          'value': clonedValues[i]
        };
      });
    } else {
      msg.param = params;
      msg.value = values;
    }
    msg.index = Number.isInteger(index) ? index : -1;
    const rsp = await this.owner.request(msg, timeout);
    var ret = Array.isArray(params) ? new Array(params.length).fill(null) : null;
    if (!rsp || rsp.perf != Performative.INFORM || !rsp.param) {
      if (this.owner._returnNullOnFailedResponse) return ret;
      else throw new Error(`Unable to set ${this.name}.${params} to ${values}`);
    }
    if (Array.isArray(params)) {
      if (!rsp.values) rsp.values = {};
      if (rsp.param) rsp.values[rsp.param] = rsp.value;
      const rkeys = Object.keys(rsp.values);
      return params.map( p => {
        if (p.includes('.')) p = p.split('.').pop();
        let f = rkeys.find(k => (k.includes('.') ? k.split('.').pop() : k) == p);
        return f ? rsp.values[f] : undefined;
      });
    } else {
      return rsp.value;
    }
  }


  /**
  * Gets parameter(s) on the Agent referred to by this AgentID.
  *
  * @param {(?string|?string[])} params - parameters name(s) to be get, null implies get value of all parameters on the Agent
  * @param {number} [index=-1] - index of parameter(s) to be get
  * @param {number} [timeout=5000] - timeout for the response
  * @returns {Promise<(?Object|?Object[])>} - a promise which returns the value(s) of the parameters
  */
  async get(params, index=-1, timeout=5000) {
    let msg = new ParameterReq();
    msg.recipient = this.toJSON();
    if (params){
      if (Array.isArray(params)) {
        const clonedParams = params.slice(); // Clone the array to avoid side effects
        msg.param = clonedParams.shift();
        msg.requests = clonedParams.map(p => {return {'param': p};});
      }
      else msg.param = params;
    }
    msg.index = Number.isInteger(index) ? index : -1;
    const rsp = await this.owner.request(msg, timeout);
    var ret = Array.isArray(params) ? new Array(params.length).fill(null) : null;
    if (!rsp || rsp.perf != Performative.INFORM || !rsp.param) {
      if (this.owner._returnNullOnFailedResponse) return ret;
      else throw new Error(`Unable to get ${this.name}.${params}`);
    }
    // Request for listing of all parameters.
    if (!params) {
      if (!rsp.values) rsp.values = {};
      if (rsp.param) rsp.values[rsp.param] = rsp.value;
      return rsp.values;
    } else if (Array.isArray(params)) {
      if (!rsp.values) rsp.values = {};
      if (rsp.param) rsp.values[rsp.param] = rsp.value;
      const rkeys = Object.keys(rsp.values);
      return params.map( p => {
        if (p.includes('.')) p = p.split('.').pop();
        let f = rkeys.find(k => (k.includes('.') ? k.split('.').pop() : k) == p);
        return f ? rsp.values[f] : undefined;
      });
    } else {
      return rsp.value;
    }
  }
}
