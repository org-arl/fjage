/* global global */

import { isBrowser, isNode, isJsDom, isWebWorker } from '../node_modules/browser-or-node/src/index.js';
import { AgentID } from './AgentID.js';
import { Message, createJSONMessage } from './Message.js';
import { Performative } from './Performative.js';
import { _guid } from './Utils.js';

import TCPConnector from './TCPConnector.js';
import WSConnector from './WSConnector.js';


const DEFAULT_QUEUE_SIZE = 128;        // max number of old unreceived messages to store
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
    'returnNullOnFailedResponse': true
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
    'returnNullOnFailedResponse': true
  });
  DEFAULT_URL = new URL('tcp://localhost');
}

/**
 * A gateway for connecting to a fjage master container. The new version of the constructor
 * uses an options object instead of individual parameters.
 *
 * @example <caption>Connects to the localhost:1100</caption>
 * const gw = new Gateway({ hostname: 'localhost', port: 1100 });
 *
 * @example <caption>Connects to the origin</caption>
 * const gw = new Gateway();
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
 *

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
      this.pending = {};                    // msgid to callback mapping for pending requests to server
      this.subscriptions = {};              // hashset for all topics that are subscribed
      this.listener = {};                   // set of callbacks that want to listen to incoming messages
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
      if (Array.isArray(this.eventListeners[type])) {
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
        jsonMsg = createJSONMessage(data);
      }catch(e){
        return;
      }
      this._sendEvent('rxp', jsonMsg);
      if ('id' in jsonMsg && jsonMsg.id in this.pending) {
        // response to a pending request to master
        this.pending[jsonMsg.id](jsonMsg);
        delete this.pending[jsonMsg.id];
      } else if (jsonMsg.action == 'send') {
        // incoming message from master
        // @ts-ignore
        let msg = Message._deserialize(jsonMsg.message);
        if (!msg) return;
        this._sendEvent('rxmsg', msg);
        if ((msg.recipient == this.aid.toJSON() )|| this.subscriptions[msg.recipient]) {
          var consumed = false;
          if (Array.isArray(this.eventListeners['message'])){
            for (var i = 0; i < this.eventListeners['message'].length; i++) {
              try {
                if (this.eventListeners['message'][i](msg)) {
                  consumed = true;
                  break;
                }
              } catch (error) {
                console.warn('Error in message listener : ' + error);
              }
            }
          }
          // iterate over internal callbacks, until one consumes the message
          for (var key in this.listener){
            // callback returns true if it has consumed the message
            try {
              if (this.listener[key](msg)) {
                consumed = true;
                break;
              }
            } catch (error) {
              console.warn('Error in listener : ' + error);
            }
          }
          if(!consumed) {
            if (this.queue.length >= this._queueSize) this.queue.shift();
            this.queue.push(msg);
          }
        }
      } else {
        // respond to standard requests that every container must
        let rsp = { id: jsonMsg.id, inResponseTo: jsonMsg.action };
        switch (jsonMsg.action) {
          case 'agents':
            rsp.agentIDs = [this.aid.getName()];
            break;
          case 'containsAgent':
            rsp.answer = (jsonMsg.agentID == this.aid.getName());
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
            this.listener[lid] && delete this.listener[lid];
            if (this.debug) console.log('Receive Timeout : ' + filter);
            resolve();
          }, timeout);
        }
        this.listener[lid] = msg => {
          if (!this._matchMessage(filter, msg)) return false;
          if(timer) clearTimeout(timer);
          this.listener[lid] && delete this.listener[lid];
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