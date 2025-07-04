/* global global Buffer */

import { isBrowser, isNode, isJsDom, isWebWorker } from '../node_modules/browser-or-node/src/index.js';
import TCPConnector from './TCPConnector';
import WSConnector from './WSConnector';

const DEFAULT_QUEUE_SIZE = 128;        // max number of old unreceived messages to store

/**
 * An action represented by a message. The performative actions are a subset of the
 * FIPA ACL recommendations for interagent communication.
 * @enum {string}
 */
export const Performative = {
  REQUEST: 'REQUEST',               // Request an action to be performed
  AGREE: 'AGREE',                   // Agree to performing the requested action
  REFUSE: 'REFUSE',                 // Refuse to perform the requested action
  FAILURE: 'FAILURE',               // Notification of failure to perform a requested or agreed action
  INFORM: 'INFORM',                 // Notification of an event
  CONFIRM: 'CONFIRM',               // Confirm that the answer to a query is true
  DISCONFIRM: 'DISCONFIRM',         // Confirm that the answer to a query is false
  QUERY_IF: 'QUERY_IF',             // Query if some statement is true or false
  NOT_UNDERSTOOD: 'NOT_UNDERSTOOD', // Notification that a message was not understood
  CFP: 'CFP',                       // Call for proposal
  PROPOSE: 'PROPOSE',               // Response for CFP
  CANCEL: 'CANCEL'                  // Cancel pending request
};

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
    msg.recipient = this.name;
    if (Array.isArray(params)){
      msg.param = params.shift();
      msg.value = values.shift();
      msg.requests = params.map((p, i) => {
        return {
          'param': p,
          'value': values[i]
        };
      });
      // Add back for generating a response
      params.unshift(msg.param);
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
    msg.recipient = this.name;
    if (params){
      if (Array.isArray(params)) {
        msg.param = params.shift();
        msg.requests = params.map(p => {return {'param': p};});
        // Add back for generating a response
        params.unshift(msg.param);
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

// protected String msgID = UUID.randomUUID().toString();
// protected Performative perf;
// protected AgentID recipient;
// protected AgentID sender = null;
// protected String inReplyTo = null;
// protected Long sentAt = null;

/**
 * Base class for messages transmitted by one agent to another. Creates an empty message.
 * @class
 * @param {string} [msgID] - unique identifier for the message
 * @param {Performative} [perf=Performative.INFORM] - performative
 * @param {AgentID} [recipient] - recipient of the message
 * @param {AgentID} [sender] - sender of the message
 * @param {string} [inReplyTo] - message to which this response corresponds to
 * @param {Message} [inReplyTo] - message to which this response corresponds to
 * @param {number} [sentAt] - time at which the message was sent
 */
export class Message {

  constructor(inReplyTo={msgID:null, sender:null}, perf=Performative.INFORM) {
    this.__clazz__ = 'org.arl.fjage.Message';
    this.msgID = _guid(8);
    this.sender = null;
    this.recipient = inReplyTo.sender;
    this.perf = perf;
    this.inReplyTo = inReplyTo.msgID || null;
  }

  /**
   * Gets a string representation of the message.
   *
   * @returns {string} - string representation
   */
  toString() {
    let s = '';
    let suffix = '';
    if (!this.__clazz__) return'';
    let clazz = this.__clazz__;
    clazz = clazz.replace(/^.*\./, '');
    let perf = this.perf;
    for (var k in this) {
      if (k.startsWith('__')) continue;
      if (k == 'sender') continue;
      if (k == 'recipient') continue;
      if (k == 'msgID') continue;
      if (k == 'perf') continue;
      if (k == 'inReplyTo') continue;
      if (typeof this[k] == 'object') {
        suffix = ' ...';
        continue;
      }
      s += ' ' + k + ':' + this[k];
    }
    s += suffix;
    return clazz+':'+perf+'['+s.replace(/^ /, '')+']';
  }

  // convert a message into a JSON string
  // NOTE: we don't do any base64 encoding for TX as
  //       we don't know what data type is intended
  /**
   * @private
   *
   * @return {string} - JSON string representation of the message
   */
  _serialize() {
    let clazz = this.__clazz__ || 'org.arl.fjage.Message';
    let data = JSON.stringify(this, (k,v) => {
      if (k.startsWith('__')) return;
      return v;
    });
    return '{ "clazz": "'+clazz+'", "data": '+data+' }';
  }

  // inflate a data dictionary into the message
  /** @private */
  _inflate(data) {
    for (var key in data)
      this[key] = data[key];
  }

  // convert a dictionary (usually from decoding JSON) into a message
  /**
   * @private
   *
   * @param {(string|Object)} json - JSON string or object to be converted to a message
   * @returns {Message} - message created from the JSON string or object
   * */
  static _deserialize(json) {
    let obj = null;
    if (typeof json == 'string') {
      try {
        obj = JSON.parse(json);
      }catch(e){
        return null;
      }
    } else obj = json;
    let qclazz = obj.clazz;
    let clazz = qclazz.replace(/^.*\./, '');
    let rv = MessageClass[clazz] ? new MessageClass[clazz] : new Message();
    rv.__clazz__ = qclazz;
    rv._inflate(obj.data);
    return rv;
  }
}

/**
 * A message class that can convey generic messages represented by key-value pairs.
 * @class
 * @extends Message
 */
export class GenericMessage extends Message {
  /**
   * Creates an empty generic message.
   */
  constructor() {
    super();
    this.__clazz__ = 'org.arl.fjage.GenericMessage';
  }
}

/**
 * A gateway for connecting to a fjage master container. The new version of the constructor
 * uses an options object instead of individual parameters. The old version with
 *
 *
 * @class
 * @param {Object} opts
 * @param {string} [opts.hostname="localhost"] - hostname/ip address of the master container to connect to
 * @param {number} [opts.port=1100]          - port number of the master container to connect to
 * @param {string} [opts.pathname=""]        - path of the master container to connect to (for WebSockets)
 * @param {string} [opts.keepAlive=true]     - try to reconnect if the connection is lost
 * @param {number} [opts.queueSize=128]      - size of the queue of received messages that haven't been consumed yet
 * @param {number} [opts.timeout=1000]       - timeout for fjage level messages in ms
 * @param {boolean} [opts.returnNullOnFailedResponse=true] - return null instead of throwing an error when a parameter is not found
 * @param {boolean} [opts.cancelPendingOnDisconnect=false] - cancel pending requests on disconnect
 */
export class Gateway {

  constructor(opts = {}) {
    // Similar to Object.assign but also overwrites `undefined` and empty strings with defaults
    for (var key in GATEWAY_DEFAULTS){
      if (opts[key] == undefined || opts[key] === '') opts[key] = GATEWAY_DEFAULTS[key];
    }
    var url = DEFAULT_URL;
    url.hostname = opts.hostname;
    url.port = opts.port;
    url.pathname = opts.pathname;
    let existing = this._getGWCache(url);
    if (existing) return existing;
    this._timeout = opts.timeout;         // timeout for fjage level messages (agentForService etc)
    this._keepAlive = opts.keepAlive;     // reconnect if connection gets closed/errored
    this._queueSize = opts.queueSize;     // size of queue
    this._returnNullOnFailedResponse = opts.returnNullOnFailedResponse; // null or error
    this._cancelPendingOnDisconnect = opts.cancelPendingOnDisconnect; // cancel pending requests on disconnect
    this.pending = {};                    // msgid to callback mapping for pending requests to server
    this.subscriptions = {};              // hashset for all topics that are subscribed
    this.listeners = {};                  // list of callbacks that want to listen to incoming messages
    this.eventListeners = {};             // external listeners wanting to listen internal events
    this.queue = [];                      // incoming message queue
    this.connected = false;               // connection status
    this.debug = false;                   // debug info to be logged to console?
    this.aid = new AgentID('gateway-'+_guid(4));         // gateway agent name
    this.connector = this._createConnector(url);
    this._addGWCache(this);
  }

  /**
   * Sends an event to all registered listeners of the given type.
   * @private
   * @param {string} type - type of event
   * @param {Object|Message|string} val - value to be sent to the listeners
   */
  _sendEvent(type, val) {
    if (!Array.isArray(this.eventListeners[type])) return;
    this.eventListeners[type].forEach(l => {
      if (l && {}.toString.call(l) === '[object Function]'){
        try {
          l(val);
        } catch (error) {
          console.warn('Error in event listener : ' + error);
        }
      }
    });
  }

  /**
   * Sends the message to all registered receivers.
   *
   * @private
   * @param {Message} msg
   * @returns {boolean} - true if the message was consumed by any listener
   */
  _sendReceivers(msg) {
    for (var lid in this.listeners){
      try {
        if (this.listeners[lid] && this.listeners[lid](msg)) return true;
      } catch (error) {
        console.warn('Error in listener : ' + error);
      }
    }
    return false;
  }


  /**
   * @private
   * @param {string} data - stringfied JSON data received from the master container to be processed
   * @returns {void}
   */
  _onMsgRx(data) {
    var obj;
    if (this.debug) console.log('< '+data);
    this._sendEvent('rx', data);
    try {
      obj = JSON.parse(data, _decodeBase64);
    }catch(e){
      return;
    }
    this._sendEvent('rxp', obj);
    if ('id' in obj && obj.id in this.pending) {
      // response to a pending request to master
      this.pending[obj.id](obj);
      delete this.pending[obj.id];
    } else if (obj.action == 'send') {
      // incoming message from master
      // @ts-ignore
      let msg = Message._deserialize(obj.message);
      if (!msg) return;
      this._sendEvent('rxmsg', msg);
      if ((msg.recipient == this.aid.toJSON() )|| this.subscriptions[msg.recipient]) {
        // send to any "message" listeners
        this._sendEvent('message', msg);
        // send message to receivers, if not consumed, add to queue
        if(!this._sendReceivers(msg)) {
          if (this.queue.length >= this._queueSize) this.queue.shift();
          this.queue.push(msg);
        }
      }
    } else {
      // respond to standard requests that every container must
      let rsp = { id: obj.id, inResponseTo: obj.action };
      switch (obj.action) {
      case 'agents':
        rsp.agentIDs = [this.aid.getName()];
        break;
      case 'containsAgent':
        rsp.answer = (obj.agentID == this.aid.getName());
        break;
      case 'services':
        rsp.services = [];
        break;
      case 'agentForService':
        rsp.agentID = '';
        break;
      case 'agentsForService':
        rsp.agentIDs = [];
        break;
      default:
        rsp = undefined;
      }
      if (rsp) this._msgTx(rsp);
    }
  }

  /**
   * Sends a message out to the master container.
   * @private
   * @param {string|Object} s - JSON object (either stringified or not) to be sent to the master container
   * @returns {boolean} - true if the message was sent successfully
   */
  _msgTx(s) {
    if (typeof s != 'string' && !(s instanceof String)) s = JSON.stringify(s);
    if(this.debug) console.log('> '+s);
    this._sendEvent('tx', s);
    return this.connector.write(s);
  }

  /**
   * @private
   * @param {Object} rq - request to be sent to the master container as a JSON object
   * @returns {Promise<Object>} - a promise which returns the response from the master container
   */
  _msgTxRx(rq) {
    rq.id = _guid(8);
    return new Promise(resolve => {
      let timer = setTimeout(() => {
        delete this.pending[rq.id];
        if (this.debug) console.log('Receive Timeout : ' + JSON.stringify(rq));
        resolve();
      }, 8*this._timeout);
      this.pending[rq.id] = rsp => {
        clearTimeout(timer);
        resolve(rsp);
      };
      if (!this._msgTx.call(this,rq)) {
        clearTimeout(timer);
        delete this.pending[rq.id];
        if (this.debug) console.log('Transmit Timeout : ' +  JSON.stringify(rq));
        resolve();
      }
    });
  }

  /**
   * @private
   * @param {URL} url - URL object of the master container to connect to
   * @returns {TCPConnector|WSConnector} - connector object to connect to the master container
   */
  _createConnector(url){
    let conn;
    if (url.protocol.startsWith('ws')){
      conn =  new WSConnector({
        'hostname':url.hostname,
        'port':parseInt(url.port),
        'pathname':url.pathname,
        'keepAlive': this._keepAlive,
        'debug': this.debug
      });
    }else if (url.protocol.startsWith('tcp')){
      conn = new TCPConnector({
        'hostname':url.hostname,
        'port':parseInt(url.port),
        'keepAlive': this._keepAlive,
        'debug': this.debug
      });
    } else return null;
    conn.setReadCallback(this._onMsgRx.bind(this));
    conn.addConnectionListener(state => {
      this.connected = !!state;
      if (state == true){
        this.flush();
        this.connector.write('{"alive": true}');
        this._update_watch();
      } else{
        if (this._cancelPendingOnDisconnect) {
          this._sendReceivers(null);
          this.flush();
        }
      }
      this._sendEvent('conn', state);
    });
    return conn;
  }

  /**
   * Checks if the object is a constructor.
   *
   * @private
   * @param {Object} value - an object to be checked if it is a constructor
   * @returns {boolean} - if the object is a constructor
   */
  _isConstructor(value) {
    try {
      new new Proxy(value, {construct() { return {}; }});
      return true;
    } catch (err) {
      return false;
    }
  }

  /**
   * Matches a message with a filter.
   * @private
   * @param {string|Object|function} filter - filter to be matched
   * @param {Message} msg - message to be matched to the filter
   * @returns {boolean} - true if the message matches the filter
   */
  _matchMessage(filter, msg){
    if (typeof filter == 'string' || filter instanceof String) {
      return 'inReplyTo' in msg && msg.inReplyTo == filter;
    } else if (Object.prototype.hasOwnProperty.call(filter, 'msgID')) {
      return 'inReplyTo' in msg && msg.inReplyTo == filter.msgID;
    } else if (filter.__proto__.name == 'Message' || filter.__proto__.__proto__.name == 'Message') {
      return filter.__clazz__ == msg.__clazz__;
    } else if (typeof filter == 'function' && !this._isConstructor(filter)) {
      try {
        return filter(msg);
      }catch(e){
        console.warn('Error in filter : ' + e);
        return false;
      }
    } else {
      return msg instanceof filter;
    }
  }

  /**
   * Gets the next message from the queue that matches the filter.
   * @private
   * @param {string|Object|function} filter - filter to be matched
   */
  _getMessageFromQueue(filter) {
    if (!this.queue.length) return;
    if (!filter) return this.queue.shift();
    let matchedMsg = this.queue.find( msg => this._matchMessage(filter, msg));
    if (matchedMsg) this.queue.splice(this.queue.indexOf(matchedMsg), 1);
    return matchedMsg;
  }

  /**
   * Gets a cached gateway object for the given URL (if it exists).
   * @private
   * @param {URL} url - URL object of the master container to connect to
   * @returns {Gateway|void} - gateway object for the given URL
   */
  _getGWCache(url){
    if (!gObj.fjage || !gObj.fjage.gateways) return null;
    var f = gObj.fjage.gateways.filter(g => g.connector.url.toString() == url.toString());
    if (f.length ) return f[0];
    return null;
  }

  /**
   * Adds a gateway object to the cache if it doesn't already exist.
   * @private
   * @param {Gateway} gw - gateway object to be added to the cache
   */
  _addGWCache(gw){
    if (!gObj.fjage || !gObj.fjage.gateways) return;
    gObj.fjage.gateways.push(gw);
  }

  /**
   * Removes a gateway object from the cache if it exists.
   * @private
   * @param {Gateway} gw - gateway object to be removed from the cache
   */
  _removeGWCache(gw){
    if (!gObj.fjage || !gObj.fjage.gateways) return;
    var index = gObj.fjage.gateways.indexOf(gw);
    if (index != null) gObj.fjage.gateways.splice(index,1);
  }

  /** @private */
  _update_watch() {
    let watch = Object.keys(this.subscriptions);
    watch.push(this.aid.getName());
    let rq = { action: 'wantsMessagesFor', agentIDs: watch };
    this._msgTx(rq);
  }

  /**
   * Add an event listener to listen to various events happening on this Gateway
   *
   * @param {string} type - type of event to be listened to
   * @param {function} listener - new callback/function to be called when the event happens
   * @returns {void}
   */
  addEventListener(type, listener) {
    if (!Array.isArray(this.eventListeners[type])){
      this.eventListeners[type] = [];
    }
    this.eventListeners[type].push(listener);
  }

  /**
   * Remove an event listener.
   *
   * @param {string} type - type of event the listener was for
   * @param {function} listener - callback/function which was to be called when the event happens
   * @returns {void}
   */
  removeEventListener(type, listener) {
    if (!this.eventListeners[type]) return;
    let ndx = this.eventListeners[type].indexOf(listener);
    if (ndx >= 0) this.eventListeners[type].splice(ndx, 1);
  }

  /**
   * Add a new listener to listen to all {Message}s sent to this Gateway
   *
   * @param {function} listener - new callback/function to be called when a {Message} is received
   * @returns {void}
   */
  addMessageListener(listener) {
    this.addEventListener('message',listener);
  }

  /**
   * Remove a message listener.
   *
   * @param {function} listener - removes a previously registered listener/callback
   * @returns {void}
   */
  removeMessageListener(listener) {
    this.removeEventListener('message', listener);
  }

  /**
   * Add a new listener to get notified when the connection to master is created and terminated.
   *
   * @param {function} listener - new callback/function to be called connection to master is created and terminated
   * @returns {void}
   */
  addConnListener(listener) {
    this.addEventListener('conn', listener);
  }

  /**
   * Remove a connection listener.
   *
   * @param {function} listener - removes a previously registered listener/callback
   * @returns {void}
   */
  removeConnListener(listener) {
    this.removeEventListener('conn', listener);
  }

  /**
   * Gets the agent ID associated with the gateway.
   *
   * @returns {AgentID} - agent ID
   */
  getAgentID() {
    return this.aid;
  }

  /**
   * Get an AgentID for a given agent name.
   *
   * @param {string} name - name of agent
   * @returns {AgentID} - AgentID for the given name
   */
  agent(name) {
    return new AgentID(name, false, this);
  }

  /**
   * Returns an object representing the named topic.
   *
   * @param {string|AgentID} topic - name of the topic or AgentID
   * @param {string} [topic2] - name of the topic if the topic param is an AgentID
   * @returns {AgentID} - object representing the topic
   */
  topic(topic, topic2) {
    if (typeof topic == 'string' || topic instanceof String) return new AgentID(topic, true, this);
    if (topic instanceof AgentID) {
      if (topic.isTopic()) return topic;
      return new AgentID(topic.getName()+(topic2 ? '__' + topic2 : '')+'__ntf', true, this);
    }
  }

  /**
   * Subscribes the gateway to receive all messages sent to the given topic.
   *
   * @param {AgentID} topic - the topic to subscribe to
   * @returns {boolean} - true if the subscription is successful, false otherwise
   */
  subscribe(topic) {
    if (!topic.isTopic()) topic = new AgentID(topic.getName() + '__ntf', true, this);
    this.subscriptions[topic.toJSON()] = true;
    this._update_watch();
    return true;
  }

  /**
   * Unsubscribes the gateway from a given topic.
   *
   * @param {AgentID} topic - the topic to unsubscribe
   * @returns {void}
   */
  unsubscribe(topic) {
    if (!topic.isTopic()) topic = new AgentID(topic.getName() + '__ntf', true, this);
    delete this.subscriptions[topic.toJSON()];
    this._update_watch();
  }

  /**
   * Gets a list of all agents in the container.
   * @returns {Promise<AgentID[]>} - a promise which returns an array of all agent ids when resolved
   */
  async agents() {
    let rq = { action: 'agents' };
    let rsp = await this._msgTxRx(rq);
    if (!rsp || !Array.isArray(rsp.agentIDs)) throw new Error('Unable to get agents');
    return rsp.agentIDs.map(aid => new AgentID(aid, false, this));
  }

  /**
   * Check if an agent with a given name exists in the container.
   *
   * @param {AgentID|String} agentID - the agent id to check
   * @returns {Promise<boolean>} - a promise which returns true if the agent exists when resolved
   */
  async containsAgent(agentID) {
    let rq = { action: 'containsAgent', agentID: agentID instanceof AgentID ? agentID.getName() : agentID };
    let rsp = await this._msgTxRx(rq);
    if (!rsp) throw new Error('Unable to check if agent exists');
    return !!rsp.answer;
  }

  /**
   * Finds an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param {string} service - the named service of interest
   * @returns {Promise<?AgentID>} - a promise which returns an agent id for an agent that provides the service when resolved
   */
  async agentForService(service) {
    let rq = { action: 'agentForService', service: service };
    let rsp = await this._msgTxRx(rq);
    if (!rsp) {
      if (this._returnNullOnFailedResponse) return null;
      else throw new Error('Unable to get agent for service');
    }
    if (!rsp.agentID) return null;
    return new AgentID(rsp.agentID, false, this);
  }

  /**
   * Finds all agents that provides a named service.
   *
   * @param {string} service - the named service of interest
   * @returns {Promise<?AgentID[]>} - a promise which returns an array of all agent ids that provides the service when resolved
   */
  async agentsForService(service) {
    let rq = { action: 'agentsForService', service: service };
    let rsp = await this._msgTxRx(rq);
    let aids = [];
    if (!rsp) {
      if (this._returnNullOnFailedResponse) return aids;
      else throw new Error('Unable to get agents for service');
    }
    if (!Array.isArray(rsp.agentIDs)) return aids;
    for (var i = 0; i < rsp.agentIDs.length; i++)
      aids.push(new AgentID(rsp.agentIDs[i], false, this));
    return aids;
  }

  /**
   * Sends a message to the recipient indicated in the message. The recipient
   * may be an agent or a topic.
   *
   * @param {Message} msg - message to be sent
   * @returns {boolean} - if sending was successful
   */
  send(msg) {
    msg.sender = this.aid.toJSON();
    if (msg.perf == '') {
      if (msg.__clazz__.endsWith('Req')) msg.perf = Performative.REQUEST;
      else msg.perf = Performative.INFORM;
    }
    this._sendEvent('txmsg', msg);
    let rq = JSON.stringify({ action: 'send', relay: true, message: '###MSG###' });
    // @ts-ignore
    rq = rq.replace('"###MSG###"', msg._serialize());
    return !!this._msgTx(rq);
  }

  /**
   * Flush the Gateway queue for all pending messages. This drops all the pending messages.
   * @returns {void}
   *
   */
  flush() {
    this.queue.length = 0;
  }

  /**
   * Sends a request and waits for a response. This method returns a {Promise} which resolves when a response
   * is received or if no response is received after the timeout.
   *
   * @param {Message} msg - message to send
   * @param {number} [timeout=1000] - timeout in milliseconds
   * @returns {Promise<Message|void>} - a promise which resolves with the received response message, null on timeout
   */
  async request(msg, timeout=1000) {
    this.send(msg);
    return this.receive(msg, timeout);
  }

  /**
   * Returns a response message received by the gateway. This method returns a {Promise} which resolves when
   * a response is received or if no response is received after the timeout.
   *
   * @param {function|Message|typeof Message} filter - original message to which a response is expected, or a MessageClass of the type
   * of message to match, or a closure to use to match against the message
   * @param {number} [timeout=0] - timeout in milliseconds
   * @returns {Promise<Message|void>} - received response message, null on timeout
   */
  async receive(filter, timeout=0) {
    return new Promise(resolve => {
      let msg = this._getMessageFromQueue.call(this,filter);
      if (msg) {
        resolve(msg);
        return;
      }
      if (timeout == 0) {
        if (this.debug) console.log('Receive Timeout : ' + filter);
        resolve();
        return;
      }
      let lid = _guid(8);
      let timer;
      if (timeout > 0){
        timer = setTimeout(() => {
          this.listeners[lid] && delete this.listeners[lid];
          if (this.debug) console.log('Receive Timeout : ' + filter);
          resolve();
        }, timeout);
      }
      // listener for each pending receive
      this.listeners[lid] = msg => {
        // skip if the message does not match the filter
        if (msg && !this._matchMessage(filter, msg)) return false;
        if(timer) clearTimeout(timer);
        // if the message matches the filter or is null, delete listener clear timer and resolve
        this.listeners[lid] && delete this.listeners[lid];
        resolve(msg);
        return true;
      };
    });
  }

  /**
   * Closes the gateway. The gateway functionality may not longer be accessed after
   * this method is called.
   * @returns {void}
   */
  close() {
    this.connector.close();
    this._removeGWCache(this);
  }

}

/**
 * Services supported by fjage agents.
 */
export const Services = {
  SHELL : 'org.arl.fjage.shell.Services.SHELL'
};

/**
 * Creates a unqualified message class based on a fully qualified name.
 * @param {string} name - fully qualified name of the message class to be created
 * @param {typeof Message} [parent] - class of the parent MessageClass to inherit from
 * @constructs Message
 * @example
 * const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');
 * let pReq = new ParameterReq()
 */
export function MessageClass(name, parent=Message) {
  let sname = name.replace(/^.*\./, '');
  if (MessageClass[sname]) return MessageClass[sname];
  let cls = class extends parent {
    /**
     * @param {{ [x: string]: any; }} params
     */
    constructor(params) {
      super();
      this.__clazz__ = name;
      if (params){
        const keys = Object.keys(params);
        for (let k of keys) {
          this[k] = params[k];
        }
      }
      if (name.endsWith('Req')) this.perf = Performative.REQUEST;
    }
  };
  cls.__clazz__ = name;
  MessageClass[sname] = cls;
  return cls;
}

////// private utilities

// generate random ID with length 4*len characters
/** @private */
function _guid(len) {
  function s4() {
    return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
  }
  let s = s4();
  for (var i = 0; i < len-1; i++)
    s += s4();
  return s;
}

// convert from base 64 to array
/** @private */
function _b64toArray(base64, dtype, littleEndian=true) {
  let s = gObj.atob(base64);
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

// base 64 JSON decoder
/** @private */
function _decodeBase64(k, d) {
  if (d === null) {
    return null;
  }
  if (typeof d == 'object' && 'clazz' in d) {
    let clazz = d.clazz;
    if (clazz.startsWith('[') && clazz.length == 2 && 'data' in d) {
      let x = _b64toArray(d.data, d.clazz);
      if (x) d = x;
    }
  }
  return d;
}

////// global

const GATEWAY_DEFAULTS = {};

/** @type {Window & globalThis & Object} */
let gObj = {};
let DEFAULT_URL;
if (isBrowser || isWebWorker){
  gObj = window;
  Object.assign(GATEWAY_DEFAULTS, {
    'hostname': gObj.location.hostname,
    'port': gObj.location.port,
    'pathname' : '/ws/',
    'timeout': 1000,
    'keepAlive' : true,
    'queueSize': DEFAULT_QUEUE_SIZE,
    'returnNullOnFailedResponse': true,
    'cancelPendingOnDisconnect': false
  });
  DEFAULT_URL = new URL('ws://localhost');
  // Enable caching of Gateways
  if (typeof gObj.fjage === 'undefined') gObj.fjage = {};
  if (typeof gObj.fjage.gateways == 'undefined') gObj.fjage.gateways = [];
} else if (isJsDom || isNode){
  gObj = global;
  Object.assign(GATEWAY_DEFAULTS, {
    'hostname': 'localhost',
    'port': '1100',
    'pathname': '',
    'timeout': 1000,
    'keepAlive' : true,
    'queueSize': DEFAULT_QUEUE_SIZE,
    'returnNullOnFailedResponse': true,
    'cancelPendingOnDisconnect': false
  });
  DEFAULT_URL = new URL('tcp://localhost');
  gObj.atob = a => Buffer.from(a, 'base64').toString('binary');
}

/**
 * @typedef {Object} ParameterReq.Entry
 * @property {string} param - parameter name
 * @property {Object} value - parameter value
 * @exports ParameterReq.Entry
 */


/**
 * A message that requests one or more parameters of an agent.
 * @typedef {Message} ParameterReq
 * @property {string} param - parameters name to be get/set if only a single parameter is to be get/set
 * @property {Object} value - parameters value to be set if only a single parameter is to be set
 * @property {Array<ParameterReq.Entry>} requests - a list of multiple parameters to be get/set
 * @property {number} [index=-1] - index of parameter(s) to be set
 * @exports ParameterReq
 */
export const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');
