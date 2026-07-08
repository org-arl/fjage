import { Performative } from './performative.js';
import { ParameterReq  } from './message.js';
import { Gateway } from './gateway.js';  // import Gateway class for type checking. Remove if not needed.
import { Message } from './message.js';  // import Message class for type checking. Remove if not needed.

const DEFAULT_TIMEOUT = 10000; // Default timeout for non-owned AgentIDs


/**
* An identifier for an agent or a topic. This can be to send, receive messages, and set or get parameters
* on an agent or topic on the fjåge master container.
*/
export class AgentID {
  /**
   * Name of the agent
   * @type {string}
   */
  name;

  /**
   * Whether the identifer is for a topic
   * @type {boolean}
   */
  topic;

  /**
   * Gateway owner for this AgentID
   * @type {Gateway}
   */
  owner;

  /**
   * Construct an AgentID.
   *
   * @param {string} name
   * @param {boolean} [topic=false]
   * @param {Gateway} [owner]
   */
  constructor(name, topic=false, owner) {
    this.name = name;
    this.topic = topic;
    this.owner = owner;
    this._timeout = owner ? owner._timeout : DEFAULT_TIMEOUT; // Default timeout if owner is not provided
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
    msg.recipient = this;
    if (this.owner) this.owner.send(msg);
    else throw new Error('Unowned AgentID cannot send messages');
  }

  /**
  * Sends a request to the agent represented by this id and waits for a reponse.
  *
  * @param {Message} msg - request to send
  * @param {number} [timeout=owner.timeout] - timeout in milliseconds
  * @returns {Promise<Message>} - response
  */
  async request(msg, timeout=this._timeout) {
    msg.recipient = this;
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
   * Inflate the AgentID from a JSON string.
   *
   * @param {string} json - JSON string to be converted to an AgentID
   * @param {Gateway} [owner] - Gateway owner for this AgentID
   * @returns {AgentID} - AgentID created from the JSON string or object
   */
  static fromJSON(json, owner) {
    if (typeof json !== 'string') {
      throw new Error('Invalid JSON for AgentID');
    }
    json = json.trim();
    if (json.startsWith('#')) {
      return new AgentID(json.substring(1), true, owner);
    } else {
      return new AgentID(json, false, owner);
    }
  }

  /**
   * @overload
   * @param {string} param - parameter name to set
   * @param {unknown} value - parameter value to set
   * @param {number} [index=-1] - index of parameter to be set
   * @param {number} [timeout=owner.timeout] - timeout for the response
   * @returns {Promise<unknown>} - a promise which returns the new value of the parameter
   */

  /**
   * @overload
   * @param {string[]} params - parameter names to set
   * @param {unknown[]} values - parameter values to set
   * @param {number} [index=-1] - index of parameters to be set
   * @param {number} [timeout=owner.timeout] - timeout for the response
   * @returns {Promise<unknown[]>} - a promise which returns the new value of the parameters
   */

  /**
  * Sets parameter(s) on the Agent referred to by this AgentID.
  *
  * @param {(string|string[])} params - parameters name(s) to be set
  * @param {(unknown|unknown[])} values - parameters value(s) to be set
  * @param {number} [index=-1] - index of parameter(s) to be set
  * @param {number} [timeout=owner.timeout] - timeout for the response
  * @returns {Promise<(unknown | unknown[])>} - a promise which returns the new value(s) of the parameters
  */
  async set (params, values, index=-1, timeout=this._timeout) {
    if (!params) return null;
    let msg = new ParameterReq();
    msg.recipient = this;

    if (Array.isArray(params)) {
      if (!Array.isArray(values)) {
        throw new Error('Values must be an array because an array was passed for parameters');
      }

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
    if (!rsp || rsp.perf != Performative.INFORM || !('param' in rsp) || !rsp.param || typeof rsp.param !== 'string') {
      if (this.owner._returnNullOnFailedResponse) return ret;
      else throw new Error(`Unable to set ${this.name}.${params} to ${values}`);
    }
    if (Array.isArray(params)) {
      const values = 'values' in rsp && rsp.values ? rsp.values : {};
      values[rsp.param] = 'value' in rsp ? rsp.value : undefined;
      const rkeys = Object.keys(values);
      return params.map( p => {
        if (p.includes('.')) p = p.split('.').pop();
        let f = rkeys.find(k => (k.includes('.') ? k.split('.').pop() : k) == p);
        return f ? values[f] : undefined;
      });
    } else {
      return 'value' in rsp ? rsp.value : undefined;
    }
  }


  /**
   * @overload
   * @param {string} param - name of the parameter to get
   * @param {number} [index=-1] - index of the parameter to get
   * @param {number} [timeout=owner.timeout] - timeout for the response
   * @returns {Promise<unknown>} - a promise which returns the value of the parameter
   */

  /**
   * @overload
   * @param {string[] | null} param - names of the parameters to get, or null to get the value of all parameters on the Agent
   * @param {number} [index=-1] - index of parameters to get
   * @param {number} [timeout=owner.timeout] - timeout for the response
   * @returns {Promise<unknown[]>} - a promise which returns the values of the parameters
   */

  /**
  * Gets parameter(s) on the Agent referred to by this AgentID.
  *
  * @param {string | string[] | null} params - parameters name(s) to be get, null implies get value of all parameters on the Agent
  * @param {number} [index=-1] - index of parameter(s) to be get
  * @param {number} [timeout=owner.timeout] - timeout for the response
  * @returns {Promise<unknown | unknown[]>} - a promise which returns the value(s) of the parameters
  */
  async get(params, index=-1, timeout=this._timeout) {
    let msg = new ParameterReq();
    msg.recipient = this;
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
    if (!rsp || rsp.perf != Performative.INFORM || !('param' in rsp) || !rsp.param || typeof rsp.param !== 'string') {
      if (this.owner._returnNullOnFailedResponse) return ret;
      else throw new Error(`Unable to get ${this.name}.${params}`);
    }
    // Request for listing of all parameters.
    if (!params) {
      const values = 'values' in rsp && rsp.values ? rsp.values : {};
      values[rsp.param] = 'value' in rsp ? rsp.value : undefined;
      return values;
    } else if (Array.isArray(params)) {
      const values = 'values' in rsp && rsp.values ? rsp.values : {};
      values[rsp.param] = 'value' in rsp ? rsp.value : undefined;
      const rkeys = Object.keys(values);
      return params.map( p => {
        if (p.includes('.')) p = p.split('.').pop();
        let f = rkeys.find(k => (k.includes('.') ? k.split('.').pop() : k) == p);
        return f ? values[f] : undefined;
      });
    } else {
      return 'value' in rsp ? rsp.value : undefined;
    }
  }
}
