/* global global */

import { isBrowser, isNode, isJsDom, isWebWorker } from 'browser-or-node';
import { AgentID } from './agentid.js';
import { Message} from './message.js';
import { _guid, UUID7 } from './utils.js';

import TCPConnector from './tcpconnector.js';
import WSConnector from './wsconnector.js';
import { JSONMessage, Actions } from './jsonmessage.js';

const DEFAULT_QUEUE_SIZE = 128;        // max number of old unreceived messages to store
const DEFAULT_TIMEOUT = 10000;         // default timeout for requests in milliseconds

const GATEWAY_DEFAULTS = {
  'timeout': DEFAULT_TIMEOUT,
  'keepAlive' : true,
  'queueSize': DEFAULT_QUEUE_SIZE,
  'returnNullOnFailedResponse': true
};

let DEFAULT_URL;
let gObj = {};

/**
*
* @private
*
* Initializes the Gateway module. This function should be called before using the Gateway class.
* It sets up the default values for the Gateway and initializes the global object.
* It also sets up the default URL for the Gateway based on the environment (browser, Node.js, etc.).
* @returns {void}
*/
export function init(){
  if (isBrowser || isWebWorker){
    gObj = window;
    Object.assign(GATEWAY_DEFAULTS, {
      'hostname': gObj.location.hostname,
      'port': gObj.location.port,
      'pathname' : '/ws/'
    });
    DEFAULT_URL = new URL('ws://localhost');
    // Enable caching of Gateways in browser
    if (typeof gObj.fjage === 'undefined') gObj.fjage = {};
    if (typeof gObj.fjage.gateways == 'undefined') gObj.fjage.gateways = [];
  } else if (isJsDom || isNode){
    gObj = global;
    Object.assign(GATEWAY_DEFAULTS, {
      'hostname': 'localhost',
      'port': '1100',
      'pathname': ''
    });
    DEFAULT_URL = new URL('tcp://localhost');
  }
}

/**
* A gateway for connecting to a fjage master container. This class provides methods to
* send and receive messages, subscribe to topics, and manage connections to the master container.
* It can be used to connect to a fjage master container over WebSockets or TCP.
*
* @example <caption>Connects to the localhost:1100</caption>
* const gw = new Gateway({ hostname: 'localhost', port: 1100 });
*
* @example <caption>Connects to the origin</caption>
* const gw = new Gateway();
*
* @class
* @property {AgentID} aid - agent id of the gateway
* @property {boolean} connected - true if the gateway is connected to the master container
* @property {boolean} debug - true if debug messages should be logged to the console
*
* Constructor arguments:
* @param {Object} opts
* @param {string} [opts.hostname="localhost"] - hostname/ip address of the master container to connect to
* @param {number} [opts.port=1100]          - port number of the master container to connect to
* @param {string} [opts.pathname=""]        - path of the master container to connect to (for WebSockets)
* @param {boolean} [opts.keepAlive=true]     - try to reconnect if the connection is lost
* @param {number} [opts.queueSize=128]      - size of the queue of received messages that haven't been consumed yet
* @param {number} [opts.timeout=10000]       - timeout for fjage level messages in ms
* @param {boolean} [opts.returnNullOnFailedResponse=true] - return null instead of throwing an error when a parameter is not found
* @param {boolean} [opts.cancelPendingOnDisconnect=false] - cancel pending requests on disconnects
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
    this.subscriptions = {};              // map for all topics that are subscribed
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
    var jsonMsg;
    if (this.debug) console.log('< '+data);
    this._sendEvent('rx', data);
    try {
      jsonMsg = new JSONMessage(data, this);
    }catch(e){
      return;
    }
    this._sendEvent('rxp', jsonMsg);
    if (jsonMsg.id && jsonMsg.id in this.pending) {
      // response to a pending request to master
      this.pending[jsonMsg.id](jsonMsg);
      delete this.pending[jsonMsg.id];
    } else if (jsonMsg.action == Actions.SEND) {
      // incoming message from master
      const msg = jsonMsg.message;
      if (!msg) return;
      this._sendEvent('rxmsg', msg);
      if ((msg.recipient.toJSON() == this.aid.toJSON())|| this.subscriptions[msg.recipient.toJSON()]) {
        // send to any "message" listeners
        this._sendEvent('message', msg);
        // send message to receivers, if not consumed, add to queue
        if(!this._sendReceivers(msg)) {
          if (this.queue.length >= this._queueSize) this.queue.shift();
          this.queue.push(msg);
        }
      }
    } else {
      // respond to standard requests that every gateway must
      let rsp = new JSONMessage();
      rsp.id = jsonMsg.id;
      rsp.inResponseTo = jsonMsg.action;
      switch (jsonMsg.action) {
        case 'agents':
        rsp.agentIDs = [this.aid];
        break;
        case 'containsAgent':
        rsp.answer = (jsonMsg.agentID.toJSON() == this.aid.toJSON());
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
  * Sends a message out to the master container. This method is used for sending
  * fjage level actions that do not require a response, such as alive, wantMessages, etc.
  * @private
  * @param {JSONMessage} msg - JSONMessage to be sent to the master container
  * @returns {boolean} - true if the message was sent successfully
  */
  _msgTx(msg) {
    const s = msg.toJSON();
    if(this.debug) console.log('> '+s);
    this._sendEvent('tx', s);
    return this.connector.write(s);
  }

  /**
  * Send a message to the master container and wait for a response. This method is used for sending
  * fjage level actions that require a response, such as agentForService, agents, etc.
  * @private
  * @param {JSONMessage} rq - JSONMessage to be sent to the master container
  * @param {number} [timeout=opts.timeout] - timeout in milliseconds for the response
  * @returns {Promise<JSONMessage|null>} - a promise which returns the response from the master container
  */
  _msgTxRx(rq, timeout = this._timeout) {
    rq.id = UUID7.generate().toString();
    return new Promise(resolve => {
      let timer;
      if (timeout >= 0){
        timer = setTimeout(() => {
          delete this.pending[rq.id];
          if (this.debug) console.log('Receive Timeout : ' + JSON.stringify(rq));
          resolve(null);
        }, timeout);
      }
      this.pending[rq.id] = rsp => {
        if (timer) clearTimeout(timer);
        resolve(rsp);
      };
      if (!this._msgTx.call(this,rq)) {
        if(timer) clearTimeout(timer);
        delete this.pending[rq.id];
        if (this.debug) console.log('Transmit Failure : ' +  JSON.stringify(rq));
        resolve(null);
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
    watch.push(this.aid.toJSON());
    const jsonMsg = JSONMessage.createWantsMessagesFor(watch.map(id => AgentID.fromJSON(id)));
    this._msgTx(jsonMsg);
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
  * @param {number} [timeout=opts.timeout] - timeout in milliseconds
  * @returns {Promise<AgentID[]>} - a promise which returns an array of all agent ids when resolved
  */
  async agents(timeout=this._timeout) {
    let jsonMsg = JSONMessage.createAgents();
    let rsp = await this._msgTxRx(jsonMsg, timeout);
    if (!rsp || !Array.isArray(rsp.agentIDs)) throw new Error('Unable to get agents');
    return rsp.agentIDs;
  }

  /**
  * Check if an agent with a given name exists in the container.
  *
  * @param {AgentID|string} agentID - the agent id to check
  * @param {number} [timeout=opts.timeout] - timeout in milliseconds
  * @returns {Promise<boolean>} - a promise which returns true if the agent exists when resolved
  */
  async containsAgent(agentID, timeout=this._timeout) {
    let jsonMsg = JSONMessage.createContainsAgent(agentID instanceof AgentID ? agentID : new AgentID(agentID));
    let rsp = await this._msgTxRx(jsonMsg, timeout);
    if (!rsp) {
      if (this._returnNullOnFailedResponse) return null;
      else throw new Error('Unable to check if agent exists');
    }
    return !!rsp.answer;
  }

  /**
  * Finds an agent that provides a named service. If multiple agents are registered
  * to provide a given service, any of the agents' id may be returned.
  *
  * @param {string} service - the named service of interest
  * @param {number} [timeout=opts.timeout] - timeout in milliseconds
  * @returns {Promise<?AgentID>} - a promise which returns an agent id for an agent that provides the service when resolved
  */
  async agentForService(service, timeout=this._timeout) {
    let jsonMsg = JSONMessage.createAgentForService(service);
    let rsp = await this._msgTxRx(jsonMsg, timeout);
    if (!rsp) {
      if (this._returnNullOnFailedResponse) return null;
      else throw new Error('Unable to get agent for service');
    }
    return rsp.agentID;
  }

  /**
  * Finds all agents that provides a named service.
  *
  * @param {string} service - the named service of interest
  * @param {number} [timeout=opts.timeout] - timeout in milliseconds
  * @returns {Promise<AgentID[]>} - a promise which returns an array of all agent ids that provides the service when resolved
  */
  async agentsForService(service, timeout=this._timeout) {
    let jsonMsg = JSONMessage.createAgentsForService(service);
    let rsp = await this._msgTxRx(jsonMsg, timeout);
    if (!rsp) {
      if (this._returnNullOnFailedResponse) return null;
      else throw new Error('Unable to get agents for service');
    }
    return rsp.agentIDs || [];
  }

  /**
  * Sends a message to the recipient indicated in the message. The recipient
  * may be an agent or a topic.
  *
  * @param {Message} msg - message to be sent
  * @returns {boolean} - if sending was successful
  */
  send(msg) {
    msg.sender = this.aid;
    this._sendEvent('txmsg', msg);
    const jsonMsg = JSONMessage.createSend(msg, true);
    return !!this._msgTx(jsonMsg);
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
  * @param {number} [timeout=opts.timeout] - timeout in milliseconds
  * @returns {Promise<Message|void>} - a promise which resolves with the received response message, null on timeout
  */
  async request(msg, timeout=this._timeout) {
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
      let lid = UUID7.generate().toString();
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