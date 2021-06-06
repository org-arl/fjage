import { isBrowser, isNode, isJsDom, isWebWorker } from "../node_modules/browser-or-node/src/index.js";

const DEFAULT_QUEUE_SIZE = 128;        // max number of old unreceived messages to store

/**
 * An action represented by a message. The performative actions are a subset of the
 * FIPA ACL recommendations for interagent communication.
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
 */
export class AgentID {

  /**
    * Create an AgentID
    * @param {string} name - name of the agent.
    * @param {boolean} topic - name of topic.
    * @param {Gateway} owner - Gateway owner for this AgentID.
    */
  constructor(name, topic, owner) {
    this.name = name;
    this.topic = topic;
    this.owner = owner;
  }

  /**
   * Gets the name of the agent or topic.
   *
   * @return {string} name of agent or topic.
   */
  getName() {
    return this.name;
  }

  /**
   * Returns true if the agent id represents a topic.
   *
   * @return {boolean} true if the agent id represents a topic,
   *         false if it represents an agent.
   */
  isTopic() {
    return this.topic;
  }

  /**
   * Sends a message to the agent represented by this id.
   *
   * @param {string} msg - message to send.
   * @returns {void}
   */
  send(msg) {
    msg.recipient = this.toJSON();
    this.owner.send(msg);
  }

  /**
   * Sends a request to the agent represented by this id and waits for
   * a return message for 1 second.
   *
   * @param {Message} msg - request to send.
   * @param {number} [timeout=1000] - timeout in milliseconds.
   * @return {Message} response.
   */
  request(msg, timeout=1000) {
    msg.recipient = this.toJSON();
    return this.owner.request(msg, timeout);
  }

  /**
   * Gets a string representation of the agent id.
   *
   * @return {string} string representation of the agent id.
   */
  toString() {
    return this.toJSON() + ((this.owner && this.owner.connector) ? ` on ${this.owner.connector.url}` : '');
  }

  /**
   * Gets a JSON string representation of the agent id.
   *
   * @return {string} JSON string representation of the agent id.
   */
  toJSON() {
    return (this.topic ? '#' : '') + this.name;
  }

  /**
   * Sets parameter(s) on the Agent referred to by this AgentID.
   *
   * @param {(string|string[])} params - parameters name(s) to be set.
   * @param {(Object|Object[])} values - parameters value(s) to be set.
   * @param {number} [index=-1] - index of parameter(s) to be set.
   * @param {number} [timeout=5000] - timeout for the response.
   * @return {Promise} - A promise which returns the new value(s) of the parameters
   */
  set (params, values, index=-1, timeout=5000) {
    if (!params) return null;
    let msg = new ParameterReq();
    msg.recipient = this.name;
    if (Array.isArray(params)){
      msg.requests = params.map((p, i) => {
        return {
          'param': p,
          'value': values[i]
        };
      });
    } else {
      msg.param = params;
      msg.value = values;
    }
    msg.index = Number.isInteger(index) ? index : -1;
    return this.owner.request(msg, timeout).then(rsp => {
      return new Promise(resolve => {
        var ret = Array.isArray(params) ? new Array(params.length).fill(null) : null;
        if (!rsp || rsp.perf != Performative.INFORM || !rsp.param){
          resolve(ret);
          return;
        }

        if (Array.isArray(params)){
          if (!rsp.values) rsp.values = {};
          if (rsp.param) rsp.values[rsp.param] = rsp.value;
          const rvals = Object.keys(rsp.values);
          ret = params.map((p, i) => {
            let f = rvals.find(rv => rv.endsWith(p));
            if (f) return rsp.values[f];
            else return null;
          });
        }else{
          ret = rsp.value;
        }
        resolve(ret);
      });
    });
  }


  /**
   * Gets parameter(s) on the Agent referred to by this AgentID.
   *
   * @param {(null|string|string[])} params - parameters name(s) to be get. null implies get value of all parameters on the Agent.
   * @param {number} [index=-1] - index of parameter(s) to be get.
   * @param {number} [timeout=5000] - timeout for the response.
   * @return {Promise} - A promise which returns the value(s) of the parameters
   */
  get(params, index=-1, timeout=5000) {
    let msg = new ParameterReq();
    msg.recipient = this.name;
    if (params){
      if (Array.isArray(params)){
        msg.requests = params.map(p => {return {'param': p};});
      }else{
        msg.param = params;
      }
    }
    msg.index = Number.isInteger(index) ? index : -1;
    return this.owner.request(msg, timeout).then(rsp => {
      return new Promise(resolve => {
        var ret = Array.isArray(params) ? new Array(params.length).fill(null) : null;
        if (!rsp || rsp.perf != Performative.INFORM || (params && (!rsp.param))){
          resolve(ret);
          return;
        }
        // Request for listing of all parameters.
        if (!params){
          if (!rsp.values) rsp.values = {};
          if (rsp.param) rsp.values[rsp.param] = rsp.value;
          ret = rsp.values;
        } else if (Array.isArray(params)) {
          if (!rsp.values) rsp.values = {};
          if (rsp.param) rsp.values[rsp.param] = rsp.value;
          const rvals = Object.keys(rsp.values);
          ret = params.map(p => {
            let f = rvals.find(rv => rv.endsWith(p));
            return f ? rsp.values[f] : null ;
          });
        } else{
          ret = rsp.value;
        }

        resolve(ret);
      });
    });
  }
}

/**
 * Base class for messages transmitted by one agent to another. This class provides
 * the basic attributes of messages and is typically extended by application-specific
 * message classes. To ensure that messages can be sent between agents running
 * on remote containers, all attributes of a message must be serializable.
 */
export class Message {

  /**
   * Creates an empty message.
   * @param {Message} inReplyTo - message to which this response corresponds to.
   * @param {Performative} perf  - performative
   */
  constructor(inReplyTo={msgID:null, sender:null}, perf='') {
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
   * @return {string} string representation.
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
  _serialize() {
    let clazz = this.__clazz__;
    let data = JSON.stringify(this, (k,v) => {
      if (k.startsWith('__')) return;
      return v;
    });
    return '{ "clazz": "'+clazz+'", "data": '+data+' }';
  }

  // inflate a data dictionary into the message
  _inflate(data) {
    for (var key in data)
      this[key] = data[key];
  }

  // convert a dictionary (usually from decoding JSON) into a message
  static _deserialize(obj) {
    if (typeof obj == 'string' || obj instanceof String) {
      try {
        obj = JSON.parse(obj);
      }catch(e){
        return null;
      }
    }
    let qclazz = obj.clazz;
    let clazz = qclazz.replace(/^.*\./, '');
    let rv = this.isPrototypeOf(gObj[clazz]) ? new gObj[clazz] : new Message();
    // let rv = eval('try { new '+clazz+'() } catch(ex) { new Message() }');
    rv.__clazz__ = qclazz;
    rv._inflate(obj.data);
    return rv;
  }
}

/**
 * A message class that can convey generic messages represented by key-value pairs.
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
 * Gateway to communicate with agents from Java classes. Only agents in a master
 * or slave container can be accessed using this gateway.
 *
 */
 export class Gateway {

  /**
   * Creates a gateway connecting to a specified master container over Websockets.
   *
   * @param {Object} opts
   * @param {String} opts.hostname - hostname/ip address of the master container to connect to
   * @param {Number} opts.port - port number of the master container to connect to
   * @param {String} opts.pathname - path of the master container to connect to
   * @param {String} opts.keepAlive - try to reconnect if the connection is lost
   * @param {int} opts.queueSize     - size of the queue of received messages that haven't been consumed yet
   * @param {int} opts.timeout     - timeout for fjage level messages
   */
  constructor(opts = {}) {
    let defaults;
    var url;
    if (isBrowser){
      defaults = {
        "hostname": window.location.hostname,
        "port": window.location.port,
        "pathname" : "/ws/",
        "timeout": 1000,
        "keepAlive" : true,
        "queueSize": DEFAULT_QUEUE_SIZE
      };
      url = new URL('ws://localhost')
    } else {
      defaults = {
        "hostname": "localhost",
        "port": "1100",
        "pathname": "",
        "timeout": 1000,
        "keepAlive" : true,
        "queueSize": DEFAULT_QUEUE_SIZE
      };
      url = new URL('tcp://localhost')
    }
    opts = Object.assign({}, defaults, opts);
    url.hostname = opts.hostname;
    url.port = opts.port || 80;
    url.pathname = opts.pathname;
    let existing = gObj.fjage.getGateway(url);
    if (existing) return existing;
    this._timeout = opts.timeout;         // timeout for fjage level messages (agentForService etc)
    this._keepAlive = opts.keepAlive;     // reconnect if connection gets closed/errored
    this._queueSize = opts.queueSize      // size of queue
    this.pending = {};                    // msgid to callback mapping for pending requests to server
    this.subscriptions = {};              // hashset for all topics that are subscribed
    this.listener = {};                   // set of callbacks that want to listen to incoming messages
    this.eventListeners = {};             // external listeners wanting to listen internal events
    this.queue = [];                      // incoming message queue
    this.debug = false;                   // debug info to be logged to console?
    this.aid = new AgentID(isBrowser ? 'WebGW-' : 'NodeGW-'+_guid(4));         // gateway agent name
    this.connector = this._createConnector(url);
    gObj.fjage.gateways.push(this);
  }

  _sendEvent(type, val) {
    if (Array.isArray(this.eventListeners[type])) {
      this.eventListeners[type].forEach(l => {
        l && {}.toString.call(l) === '[object Function]' && l(val);
      });
    }
  }

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
      let msg = Message._deserialize(obj.message);
      if (!msg) return;
      this._sendEvent('rxmsg', msg);
      if ((msg.recipient == this.aid.toJSON() )|| this.subscriptions[msg.recipient]) {
        var consumed = false;
        if (Array.isArray(this.eventListeners['message'])){
          for (var i = 0; i < this.eventListeners['message'].length; i++) {
            if (this.eventListeners['message'][i](msg)) {
              consumed = true;
              break;
            }
          }
        }
        // iterate over internal callbacks, until one consumes the message
        for (var key in this.listener){
          // callback returns true if it has consumed the message
          if (this.listener[key](msg)) {
            consumed = true;
            break;
          }
        }
        if(!consumed) {
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

  _msgTx(s) {
    if(this.debug) console.log('> '+s);
    this._sendEvent('tx', s);
    return this.connector.write(s+'\n');
  }

  // returns a Promise
  _msgTxRx(rq) {
    rq.id = _guid(8);
    return new Promise(resolve => {
      let timer = setTimeout(() => {
        delete this.pending[rq.id];
        if (this.debug) console.log('Receive Timeout : ' + rq);
        resolve();
      }, 8*this._timeout);
      this.pending[rq.id] = rsp => {
        clearTimeout(timer);
        resolve(rsp);
      };
      if (!this._msgTx.call(this,rq)) {
        clearTimeout(timer);
        delete this.pending[rq.id];
        if (this.debug) console.log('Transmit Timeout : ' + rq);
        resolve();
      }
    });
  }



  _createConnector(url){
    let conn;
    if (isBrowser || isWebWorker){
      conn =  new Connector({
        "hostname":url.hostname,
        "port":url.port,
        "pathname":url.pathname,
        "keepAlive": this._keepAlive
      });
    }else if (isNode || isJsDom){
      conn = new Connector({
        "hostname":url.hostname,
        "port":url.port,
        "keepAlive": this._keepAlive
      });
    }
    conn.setReadCallback(this._onMsgRx.bind(this))
    conn.addConnectionListener(state => {
      if (state == true){
        this.flush()
        this.connector.write('{"alive": true}\n');
        this._update_watch();
      }
    })
    return conn;
  }

  _matchMessage(filter, msg){
    if (typeof filter == 'string' || filter instanceof String) {
      return 'inReplyTo' in msg && msg.inReplyTo == filter;
    } else if (Object.prototype.hasOwnProperty.call(filter, 'msgID')) {
      return 'inReplyTo' in msg && msg.inReplyTo == filter.msgID;
    } else if (filter.__proto__.name == 'Message') {
      return filter.__clazz__ == msg.__clazz__;
    } else if (typeof filter == 'function') {
      return filter(msg);
    } else {
      return msg instanceof filter;
    }
  }

  _getMessageFromQueue(filter) {
    if (!this.queue.length) return;
    if (!filter) return this.queue.shift();

    let matchedMsg = this.queue.find( msg => this._matchMessage(filter, msg));
    if (matchedMsg) this.queue.splice(this.queue.indexOf(matchedMsg), 1);

    return matchedMsg;
  }

  _update_watch() {
    // FIXME : Turning off wantsMessagesFor in fjagejs for now as it breaks multiple browser
    // windows connecting to the same master container.
    //
    // let watch = Object.keys(this.subscriptions);
    // watch.push(this.aid.getName());
    // let rq = { action: 'wantsMessagesFor', agentIDs: watch };
    // this._msgTx(rq);
  }

  /**
   * Add an event listener to listen to various events happening on this Gateway
   *
   * @param {string} type - type of event to be listened to.
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
   * @param {function} listener - new callback/function to be called when a {Message} is received.
   * @returns {void}
   */
  addMessageListener(listener) {
    this.addEventListener('message',listener);
  }

  /**
   * Remove a message listener.
   *
   * @param {function} listener - removes a previously registered listener/callback.
   * @returns {void}
   */
  removeMessageListener(listener) {
    this.removeEventListener('message', listener);
  }

  /**
   * Add a new listener to get notified when the connection to master is created and terminated.
   *
   * @param {function} listener - new callback/function to be called connection to master is created and terminated.
   * @returns {void}
   */
  addConnListener(listener) {
    this.addEventListener('conn', listener);
  }

  /**
   * Remove a connection listener.
   *
   * @param {function} listener - removes a previously registered listener/callback.
   * @returns {void}
   */
  removeConnListener(listener) {
    this.removeEventListener('conn', listener);
  }

  /**
   * Gets the agent ID associated with the gateway.
   *
   * @return {string} agent ID
   */
  getAgentID() {
    return this.aid;
  }

  /**
   * Get an AgentID for a given agent name.
   *
   * @param {string} name - name of agent
   * @return {AgentID} AgentID for the given name.
   */
  agent(name) {
    return new AgentID(name, false, this);
  }

  /**
   * Returns an object representing the named topic.
   *
   * @param {string|AgentID} topic - name of the topic or AgentID.
   * @param {string} topic2 - name of the topic if the topic param is an AgentID.
   * @returns {AgentID} object representing the topic.
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
   * @param {AgentID} topic - the topic to subscribe to.
   * @return {boolean} true if the subscription is successful, false otherwise.
   */
  subscribe(topic) {
    if (!topic.isTopic()) topic = new AgentID(topic.getName() + '__ntf', true, this);
    this.subscriptions[topic.toJSON()] = true;
    this._update_watch();
  }

  /**
   * Unsubscribes the gateway from a given topic.
   *
   * @param {AgentID} topic - the topic to unsubscribe.
   * @returns {void}
   */
  unsubscribe(topic) {
    if (!topic.isTopic()) topic = new AgentID(topic.getName() + '__ntf', true, this);
    delete this.subscriptions[topic.toJSON()];
    this._update_watch();
  }

  /**
   * Finds an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param {string} service - service the named service of interest.
   * @return {Promise} - A promise which returns an agent id for an agent that provides the service when resolved.
   */
  async agentForService(service) {
    let rq = { action: 'agentForService', service: service };
    let rsp = await this._msgTxRx(rq);
    if (!rsp || !rsp.agentID) return;
    return new AgentID(rsp.agentID, false, this);
  }

  /**
   * Finds all agents that provides a named service.
   *
   * @param {string} service - service the named service of interest.
   * @return {Promise} - A promise which returns an array of all agent ids that provides the service when resolved.
   */
  async agentsForService(service) {
    let rq = { action: 'agentsForService', service: service };
    let rsp = await this._msgTxRx(rq);
    let aids = [];
    if (!rsp || !Array.isArray(rsp.agentIDs)) return aids;
    for (var i = 0; i < rsp.agentIDs.length; i++)
      aids.push(new AgentID(rsp.agentIDs[i], false, this));
    return aids;
  }

  /**
   * Sends a message to the recipient indicated in the message. The recipient
   * may be an agent or a topic.
   *
   * @param {Message} msg - message to be sent.
   * @returns {boolean} status - if sending was successful.
   */
  send(msg) {
    msg.sender = this.aid.toJSON();
    if (msg.perf == '') {
      if (msg.__clazz__.endsWith('Req')) msg.perf = Performative.REQUEST;
      else msg.perf = Performative.INFORM;
    }
    this._sendEvent('txmsg', msg);
    let rq = JSON.stringify({ action: 'send', relay: true, message: '###MSG###' });
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
   * Sends a request and waits for a response. This method returns a {Promise} which resolves when a response is received or if no response is received after the timeout.
   *
   * @param {string} msg - message to send.
   * @param {number} [timeout=1000] - timeout in milliseconds.
   * @return {Promise} a promise which resolves with the received response message, null on timeout.
   */
  async request(msg, timeout=1000) {
    this.send(msg);
    return this.receive(msg, timeout);
  }

  /**
   * Returns a response message received by the gateway. This method returns a {Promise} which resolves when a response is received or if no response is received after the timeout.
   *
   * @param {function} [filter=undefined] - original message to which a response is expected, or a MessageClass of the type of message to match, or a closure to use to match against the message.
   * @param {number} [timeout=0] - timeout in milliseconds.
   * @return {Message} received response message, null on timeout.
   */
  receive(filter=undefined, timeout=0) {
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
          delete this.listener[lid];
          if (this.debug) console.log('Receive Timeout : ' + filter);
          resolve();
        }, timeout);
      }
      this.listener[lid] = msg => {
        if (!this._matchMessage(filter, msg)) return false;
        if(timer) clearTimeout(timer);
        delete this.listener[lid];
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
    this.connector.close()
    var index = gObj.fjage.gateways.indexOf(this);
    gObj.fjage.gateways.splice(index,1);
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
 * @param {string} name - fully qualified name of the message class to be created.
 * @param {string} [parent] - Class of the parent MessageClass to inherit from.
 * @returns {function} constructor for the unqualified message class.
 */
export function MessageClass(name, parent=Message) {
  let sname = name.replace(/^.*\./, '');
  let pname = parent.__clazz__.replace(/^.*\./, '');
  gObj[sname] = class extends gObj[pname] {
    constructor(params) {
      super();
      this.__clazz__ = name;
      if (params){
        const keys = Object.keys(params);
        for (let k of keys) {
          this[k] = params[k];
        }
      }
    }
  };
  gObj[sname].__clazz__ = name;
  return gObj[sname];
}

////// private utilities

// generate random ID with length 4*len characters
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
      rv.push(view.getInt64(i, littleEndian));
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


var gObj = {};
var Connector;
if (isBrowser){
  gObj = window
  import("./WSConnector.js").then(module => {
    Connector = module.default;
  })
} else if (isJsDom || isNode){
  gObj = global
  gObj.atob = a => Buffer.from(a, 'base64').toString('binary')
  import("./TCPConnector.js").then(module => {
    Connector = module.default;
  })
}

if (typeof gObj.fjage === 'undefined') {
  gObj.fjage = {};
  gObj.fjage.gateways = [];
  gObj.fjage.MessageClass = MessageClass;
  gObj.fjage.getGateway = function (url){
    var f = gObj.fjage.gateways.filter(g => g.connector.url.toString() == url.toString());
    if (f.length ) return f[0];
  };
  Message.__clazz__ = 'org.arl.fjage.Message';
  gObj['Message'] = Message;
}
  
const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');
