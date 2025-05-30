/* fjage.js v2.1.1 */

(function (global, factory) {
  typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports) :
  typeof define === 'function' && define.amd ? define(['exports'], factory) :
  (global = typeof globalThis !== 'undefined' ? globalThis : global || self, factory(global.fjage = {}));
})(this, (function (exports) { 'use strict';

  /**
  * An action represented by a message. The performative actions are a subset of the
  * FIPA ACL recommendations for interagent communication.
  * @enum {string}
  */
  const Performative = {
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

  ////// common utilities

  // generate random ID with length 4*len characters
  /**
   *
   * @private
   * @param {number} len
   */
  function _guid(len) {
    const s4 = () => Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
    return Array.from({ length: len }, s4).join('');
  }

  // src/index.ts
  var isBrowser = typeof window !== "undefined" && typeof window.document !== "undefined";
  var isNode = (
    // @ts-expect-error
    typeof process !== "undefined" && // @ts-expect-error
    process.versions != null && // @ts-expect-error
    process.versions.node != null
  );
  var isWebWorker = typeof self === "object" && self.constructor && self.constructor.name === "DedicatedWorkerGlobalScope";
  var isJsDom = typeof window !== "undefined" && window.name === "nodejs" || typeof navigator !== "undefined" && "userAgent" in navigator && typeof navigator.userAgent === "string" && (navigator.userAgent.includes("Node.js") || navigator.userAgent.includes("jsdom"));
  (
    // @ts-expect-error
    typeof Deno !== "undefined" && // @ts-expect-error
    typeof Deno.version !== "undefined" && // @ts-expect-error
    typeof Deno.version.deno !== "undefined"
  );
  typeof process !== "undefined" && process.versions != null && process.versions.bun != null;

  const SOCKET_OPEN = 'open';
  const SOCKET_OPENING = 'opening';
  const DEFAULT_RECONNECT_TIME$1 = 5000;       // ms, delay between retries to connect to the server.

  var createConnection;

  /**
  * @class
  * @ignore
  */
  class TCPConnector {

    /**
    * Create an TCPConnector to connect to a fjage master over TCP
    * @param {Object} opts
    * @param {string} [opts.hostname='localhost'] - hostname/ip address of the master container to connect to
    * @param {number} [opts.port=1100] - port number of the master container to connect to
    * @param {boolean} [opts.keepAlive=true] - try to reconnect if the connection is lost
    * @param {boolean} [opts.debug=false] - debug info to be logged to console?
    * @param {number} [opts.reconnectTime=5000] - time before reconnection is attempted after an error
    */
    constructor(opts = {}) {
      let host = opts.hostname || 'localhost';
      let port = opts.port || 1100;
      this._keepAlive = opts.keepAlive;
      this._reconnectTime = opts.reconnectTime || DEFAULT_RECONNECT_TIME$1;
      this.url = new URL('tcp://localhost');
      this.url.hostname = host;
      this.url.port = port.toString();
      this._buf = '';
      this._firstConn = true;               // if the Gateway has managed to connect to a server before
      this._firstReConn = true;             // if the Gateway has attempted to reconnect to a server before
      this.pendingOnOpen = [];              // list of callbacks make as soon as gateway is open
      this.connListeners = [];              // external listeners wanting to listen connection events
      this.debug = false;
      this._sockInit(host, port);
    }


    _sendConnEvent(val) {
      this.connListeners.forEach(l => {
        l && {}.toString.call(l) === '[object Function]' && l(val);
      });
    }

    _sockInit(host, port){
      if (!createConnection){
        try {
          // @ts-ignore
          import('net').then(module => {
            createConnection = module.createConnection;
            this._sockSetup(host, port);
          });
        }catch(error){
          if(this.debug) console.log('Unable to import net module');
        }
      }else {
        this._sockSetup(host, port);
      }
    }

    _sockSetup(host, port){
      if(!createConnection) return;
      try{
        this.sock = createConnection({ 'host': host, 'port': port });
        this.sock.setEncoding('utf8');
        this.sock.on('connect', this._onSockOpen.bind(this));
        this.sock.on('error', this._sockReconnect.bind(this));
        this.sock.on('close', () => {this._sendConnEvent(false);});
        this.sock.send = data => {this.sock.write(data);};
      } catch (error) {
        if(this.debug) console.log('Connection failed to ', this.sock.host + ':' + this.sock.port);
        return;
      }
    }

    _sockReconnect(){
      if (this._firstConn || !this._keepAlive || this.sock.readyState == SOCKET_OPENING || this.sock.readyState == SOCKET_OPEN) return;
      if (this._firstReConn) this._sendConnEvent(false);
      this._firstReConn = false;
      setTimeout(() => {
        this.pendingOnOpen = [];
        this._sockSetup(this.url.hostname, this.url.port);
      }, this._reconnectTime);
    }

    _onSockOpen() {
      this._sendConnEvent(true);
      this._firstConn = false;
      this.sock.on('close', this._sockReconnect.bind(this));
      this.sock.on('data', this._processSockData.bind(this));
      this.pendingOnOpen.forEach(cb => cb());
      this.pendingOnOpen.length = 0;
      this._buf = '';
    }

    _processSockData(s){
      this._buf += s;
      var lines = this._buf.split('\n');
      lines.forEach((l, idx) => {
        if (idx < lines.length-1){
          if (l && this._onSockRx) this._onSockRx.call(this,l);
        } else {
          this._buf = l;
        }
      });
    }

    toString(){
      let s = '';
      s += 'TCPConnector [' + this.sock ? this.sock.remoteAddress.toString() + ':' + this.sock.remotePort.toString() : '' + ']';
      return s;
    }

    /**
    * Write a string to the connector
    * @param {string} s - string to be written out of the connector to the master
    * @return {boolean} - true if connect was able to write or queue the string to the underlying socket
    */
    write(s){
      if (!this.sock || this.sock.readyState == SOCKET_OPENING){
        this.pendingOnOpen.push(() => {
          this.sock.send(s+'\n');
        });
        return true;
      } else if (this.sock.readyState == SOCKET_OPEN) {
        this.sock.send(s+'\n');
        return true;
      }
      return false;
    }

    /**
    * @callback TCPConnectorReadCallback
    * @ignore
    * @param {string} s - incoming message string
    */

    /**
    * Set a callback for receiving incoming strings from the connector
    * @param {TCPConnectorReadCallback} cb - callback that is called when the connector gets a string
    */
    setReadCallback(cb){
      if (cb && {}.toString.call(cb) === '[object Function]') this._onSockRx = cb;
    }

    /**
    * Add listener for connection events
    * @param {function} listener - a listener callback that is called when the connection is opened/closed
    */
    addConnectionListener(listener){
      this.connListeners.push(listener);
    }

    /**
    * Remove listener for connection events
    * @param {function} listener - remove the listener for connection
    * @return {boolean} - true if the listner was removed successfully
    */
    removeConnectionListener(listener) {
      let ndx = this.connListeners.indexOf(listener);
      if (ndx >= 0) {
        this.connListeners.splice(ndx, 1);
        return true;
      }
      return false;
    }

    /**
    * Close the connector
    */
    close(){
      if (!this.sock) return;
      if (this.sock.readyState == SOCKET_OPENING) {
        this.pendingOnOpen.push(() => {
          this.sock.send('{"alive": false}\n');
          this.sock.removeAllListeners('connect');
          this.sock.removeAllListeners('error');
          this.sock.removeAllListeners('close');
          this.sock.destroy();
        });
      } else if (this.sock.readyState == SOCKET_OPEN) {
        this.sock.send('{"alive": false}\n');
        this.sock.removeAllListeners('connect');
        this.sock.removeAllListeners('error');
        this.sock.removeAllListeners('close');
        this.sock.destroy();
      }
    }
  }

  const DEFAULT_RECONNECT_TIME = 5000;       // ms, delay between retries to connect to the server.

  /**
  * @class
  * @ignore
  */
  class WSConnector {

    /**
    * Create an WSConnector to connect to a fjage master over WebSockets
    * @param {Object} opts
    * @param {string} [opts.hostname='localhost'] - hostname/ip address of the master container to connect to
    * @param {number} [opts.port=80] - port number of the master container to connect to
    * @param {string} [opts.pathname="/"] - path of the master container to connect to
    * @param {boolean} [opts.keepAlive=true] - try to reconnect if the connection is lost
    * @param {boolean} [opts.debug=false] - debug info to be logged to console?
    * @param {number} [opts.reconnectTime=5000] - time before reconnection is attempted after an error
    */
    constructor(opts = {}) {
      let host = opts.hostname || 'localhost';
      let port = opts.port || 80;
      this.url = new URL('ws://localhost');
      this.url.hostname = host;
      this.url.port = port.toString();
      this.url.pathname = opts.pathname || '/';
      this._keepAlive = opts.keepAlive;
      this._reconnectTime = opts.reconnectTime || DEFAULT_RECONNECT_TIME;
      this.debug = opts.debug || false;      // debug info to be logged to console?
      this._firstConn = true;               // if the Gateway has managed to connect to a server before
      this._firstReConn = true;             // if the Gateway has attempted to reconnect to a server before
      this.pendingOnOpen = [];              // list of callbacks make as soon as gateway is open
      this.connListeners = [];              // external listeners wanting to listen connection events
      this._websockSetup(this.url);
    }

    _sendConnEvent(val) {
      this.connListeners.forEach(l => {
        l && {}.toString.call(l) === '[object Function]' && l(val);
      });
    }

    _websockSetup(url){
      try {
        this.sock = new WebSocket(url);
        this.sock.onerror = this._websockReconnect.bind(this);
        this.sock.onopen = this._onWebsockOpen.bind(this);
        this.sock.onclose = () => {this._sendConnEvent(false);};
      } catch (error) {
        if(this.debug) console.log('Connection failed to ', url);
        return;
      }
    }

    _websockReconnect(){
      if (this._firstConn || !this._keepAlive || this.sock.readyState == this.sock.CONNECTING || this.sock.readyState == this.sock.OPEN) return;
      if (this._firstReConn) this._sendConnEvent(false);
      this._firstReConn = false;
      if(this.debug) console.log('Reconnecting to ', this.sock.url);
      setTimeout(() => {
        this.pendingOnOpen = [];
        this._websockSetup(this.sock.url);
      }, this._reconnectTime);
    }

    _onWebsockOpen() {
      if(this.debug) console.log('Connected to ', this.sock.url);
      this._sendConnEvent(true);
      this.sock.onclose = this._websockReconnect.bind(this);
      this.sock.onmessage = event => { if (this._onWebsockRx) this._onWebsockRx.call(this,event.data); };
      this._firstConn = false;
      this._firstReConn = true;
      this.pendingOnOpen.forEach(cb => cb());
      this.pendingOnOpen.length = 0;
    }

    toString(){
      let s = '';
      s += 'WSConnector [' + this.sock ? this.sock.url.toString() : '' + ']';
      return s;
    }

    /**
    * Write a string to the connector
    * @param {string} s - string to be written out of the connector to the master
    */
    write(s){
      if (!this.sock || this.sock.readyState == this.sock.CONNECTING){
        this.pendingOnOpen.push(() => {
          this.sock.send(s+'\n');
        });
        return true;
      } else if (this.sock.readyState == this.sock.OPEN) {
        this.sock.send(s+'\n');
        return true;
      }
      return false;
    }

    /**
    * @callback WSConnectorReadCallback
    * @ignore
    * @param {string} s - incoming message string
    */

    /**
    * Set a callback for receiving incoming strings from the connector
    * @param {WSConnectorReadCallback} cb - callback that is called when the connector gets a string
    * @ignore
    */
    setReadCallback(cb){
      if (cb && {}.toString.call(cb) === '[object Function]') this._onWebsockRx = cb;
    }

    /**
    * Add listener for connection events
    * @param {function} listener - a listener callback that is called when the connection is opened/closed
    */
    addConnectionListener(listener){
      this.connListeners.push(listener);
    }

    /**
    * Remove listener for connection events
    * @param {function} listener - remove the listener for connection
    * @return {boolean} - true if the listner was removed successfully
    */
    removeConnectionListener(listener) {
      let ndx = this.connListeners.indexOf(listener);
      if (ndx >= 0) {
        this.connListeners.splice(ndx, 1);
        return true;
      }
      return false;
    }

    /**
    * Close the connector
    */
    close(){
      if (!this.sock) return;
      if (this.sock.readyState == this.sock.CONNECTING) {
        this.pendingOnOpen.push(() => {
          this.sock.send('{"alive": false}\n');
          this.sock.onclose = null;
          this.sock.close();
        });
      } else if (this.sock.readyState == this.sock.OPEN) {
        this.sock.send('{"alive": false}\n');
        this.sock.onclose = null;
        this.sock.close();
      }
    }
  }

  /* global Buffer */

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
  class JSONMessage {

    /**
     * @param {String} [jsonString] - JSON string to be parsed into a JSONMessage object.
     */
    constructor(jsonString) {
      this.id =  _guid(8); // unique JSON message ID
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
          if (parsed.agentID) parsed.agentID = AgentID.fromJSON(parsed.agentID);
          if (parsed.agentIDs) parsed.agentIDs = parsed.agentIDs.map(id => AgentID.fromJSON(id));
          Object.assign(this, parsed);
        } catch (e) {
          throw new Error('Invalid JSON string: ' + e.message);
        }
      }  }

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
      jsonMsg.id = _guid(8); // unique JSON message ID
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
      jsonMsg.id = _guid(8); // unique JSON message ID
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
      jsonMsg.id = _guid(8); // unique JSON message ID
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
      jsonMsg.id = _guid(8); // unique JSON message ID
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
      if (this.answer) jsonObj.answer = this.answer;
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
  const Actions = {
    AGENTS : 'agents',
    CONTAINS_AGENT : 'containsAgent',
    AGENT_FOR_SERVICE : 'agentForService',
    AGENTS_FOR_SERVICE : 'agentsForService',
    SEND : 'send',
    WANTS_MESSAGES_FOR : 'wantsMessagesFor'};

  ////// private utilities


  // base64 JSON decoder
  /**
  * @private
  *
  * @param {string} k - key
  * @param {any} d - data
  * @returns {Array} - decoded data in array format
  * */
  function _decodeBase64(k, d) {
    if (d === null) return null;
    if (typeof d == 'object' && 'clazz' in d) {
      if (d.clazz.startsWith('[') && d.clazz.length == 2 && 'data' in d) {
        let x = _b64toArray(d.data, d.clazz);
        if (x) d = x;
      }
    }
    return d;
  }

  // convert from base 64 to numeric array
  /**
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
  function _atob(a){
    if (isBrowser || isWebWorker) return window.atob(a);
    else if (isJsDom || isNode) return Buffer.from(a, 'base64').toString('binary');
  }

  /* global global */


  const DEFAULT_QUEUE_SIZE = 128;        // max number of old unreceived messages to store
  const GATEWAY_DEFAULTS = {
    'timeout': 1000,
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
  function init(){
    /** @type {Window & globalThis & Object} */
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
  * @param {number} [opts.timeout=1000]       - timeout for fjage level messages in ms
  * @param {boolean} [opts.returnNullOnFailedResponse=true] - return null instead of throwing an error when a parameter is not found
  * @param {boolean} [opts.cancelPendingOnDisconnect=false] - cancel pending requests on disconnects
  */
  class Gateway {

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
      this.subscriptions = {};       // map for all topics that are subscribed
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
        jsonMsg = new JSONMessage(data);
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
    * Sends a message out to the master container.
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
    * @private
    * @param {JSONMessage} rq - JSONMessage to be sent to the master container
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
        } else {
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
    * @returns {Promise<AgentID[]>} - a promise which returns an array of all agent ids when resolved
    */
    async agents() {
      let jsonMsg = JSONMessage.createAgents();
      let rsp = await this._msgTxRx(jsonMsg);
      if (!rsp || !Array.isArray(rsp.agentIDs)) throw new Error('Unable to get agents');
      return rsp.agentIDs;
    }

    /**
    * Check if an agent with a given name exists in the container.
    *
    * @param {AgentID|string} agentID - the agent id to check
    * @returns {Promise<boolean>} - a promise which returns true if the agent exists when resolved
    */
    async containsAgent(agentID) {
      let jsonMsg = JSONMessage.createContainsAgent(agentID instanceof AgentID ? agentID : new AgentID(agentID));
      let rsp = await this._msgTxRx(jsonMsg);
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
    * @returns {Promise<?AgentID>} - a promise which returns an agent id for an agent that provides the service when resolved
    */
    async agentForService(service) {
      let jsonMsg = JSONMessage.createAgentForService(service);
      let rsp = await this._msgTxRx(jsonMsg);
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
    * @returns {Promise<?AgentID[]>} - a promise which returns an array of all agent ids that provides the service when resolved
    */
    async agentsForService(service) {
      let jsonMsg = JSONMessage.createAgentsForService(service);
      let rsp = await this._msgTxRx(jsonMsg);
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
  * An identifier for an agent or a topic. This can be to send, receive messages, and set or get parameters
  * on an agent or topic on the fjåge master container.
  *
  * @class
  * @param {string} name - name of the agent
  * @param {boolean} [topic=false] - name of topic
  * @param {Gateway} [owner] - Gateway owner for this AgentID
  */
  class AgentID {

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
      msg.recipient = this;
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
     * Inflate the AgentID from a JSON string or object.
     *
     * @param {string} json - JSON string or object to be converted to an AgentID
     * @returns {AgentID} - AgentID created from the JSON string or object
     */
    static fromJSON(json) {
      if (typeof json !== 'string') {
        throw new Error('Invalid JSON for AgentID');
      }
      json = json.trim();
      if (json.startsWith('#')) {
        return new AgentID(json.substring(1), true);
      } else {
        return new AgentID(json, false);
      }
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

  /**
  * Base class for messages transmitted by one agent to another. Creates an empty message.
  * @class
  *
  * @property {string} msgID - unique message ID
  * @property {Performative} perf - performative of the message
  * @property {AgentID} [sender] - AgentID of the sender of the message
  * @property {AgentID} [recipient] - AgentID of the recipient of the message
  * @property {string} [inReplyTo] - ID of the message to which this message is a response
  * @property {number} [sentAt] - timestamp when the message was sent
  */
  class Message {

    /**
    * @param {Message} [inReplyToMsg] - message to which this message is a response
    * @param {Performative} [perf=Performative.INFORM] - performative of the message
    */
    constructor(inReplyToMsg, perf=Performative.INFORM) {
      this.__clazz__ = 'org.arl.fjage.Message';
      this.msgID = _guid(8);
      this.perf = perf;
      this.sender = null;
      this.recipient = inReplyToMsg ? inReplyToMsg.sender : null;
      this.inReplyTo = inReplyToMsg ? inReplyToMsg.msgID : null;
    }

    /**
    * Gets a string representation of the message.
    *
    * @returns {string} - string representation
    */
    toString() {
      let p = this.perf ? this.perf.toString() : 'MESSAGE';
      if (this.__clazz__ == 'org.arl.fjage.Message') return p;
      return p + ': ' + this.__clazz__.replace(/^.*\./, '');
    }

    /** Convert a message into a object for JSON serialization.
    *
    * NOTE: we don't do any base64 encoding for TX as
    *       we don't know what data type is intended
    *
    * @return {Object} - JSON string representation of the message
    */
    toJSON() {
      let props = {};
      for (let key in this) {
        if (key.startsWith('_')) continue; // skip private properties
        // @ts-ignore
        props[key] = this[key];
      }
      return { 'clazz': this.__clazz__, 'data': props };
    }


    /**
    * Create a message fron a object parsed from the JSON representation of the message.
    *
    * @param {Object} jsonObj - Object containing all the properties of the message
    * @returns {Message} - A message created from the Object
    *
    */
    static fromJSON(jsonObj) {
      if (!( 'clazz' in jsonObj) || !( 'data' in jsonObj)) {
        throw new Error(`Invalid Object for Message : ${jsonObj}`);
      }
      let qclazz = jsonObj.clazz;
      let clazz = qclazz.replace(/^.*\./, '');
      let rv = MessageClass[clazz] ? new MessageClass[clazz] : new Message();
      rv.__clazz__ = qclazz;
      // copy all properties from the data object
      for (var key in jsonObj.data){
        if (key === 'sender' || key === 'recipient') {
          if (jsonObj.data[key] && typeof jsonObj.data[key] === 'string') {
            rv[key] = AgentID.fromJSON(jsonObj.data[key]);
          }
        } else rv[key] = jsonObj.data[key];
      }
      return rv;
    }
  }

  /**
  * A message class that can convey generic messages represented by key-value pairs.
  * @class
  * @extends Message
  */
  class GenericMessage extends Message {
    /**
    * Creates an empty generic message.
    */
    constructor() {
      super();
      this.__clazz__ = 'org.arl.fjage.GenericMessage';
    }
  }

  /**
  * Creates a unqualified message class based on a fully qualified name.
  * @param {string} name - fully qualified name of the message class to be created
  * @param {typeof Message} [parent] - class of the parent MessageClass to inherit from
  * @constructs Message
  * @example
  * const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');
  * let pReq = new ParameterReq()
  */
  function MessageClass(name, parent=Message) {
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

  /**
  * @typedef {Object} ParameterReq.Entry
  * @property {string} param - parameter name
  * @property {Object} value - parameter value
  * @exports ParameterReq.Entry
  */

  /**
  * A message that requests one or more parameters of an agent.
  *
  * @example <caption>Setting a parameter myAgent.x to 42</caption>
  * let req = new ParameterReq({
  *  recipient: myAgentId,
  *  param: 'x',
  *  value: 42
  * });
  *
  * @example <caption>Getting the value of myAgent.x</caption>
  * let req = new ParameterReq({
  * recipient: myAgentId,
  * param: 'x'
  * });
  *
  * @typedef {Message} ParameterReq
  * @property {string} param - parameters name to be get/set if only a single parameter is to be get/set
  * @property {Object} value - parameters value to be set if only a single parameter is to be set
  * @property {Array<ParameterReq.Entry>} requests - a list of multiple parameters to be get/set
  * @property {number} [index=-1] - index of parameter(s) to be set*
  * @exports ParameterReq
  */
  const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');

  /**
  * A message that is a response to a {@link ParameterReq} message.
  *
  * @example <caption>Receiving a parameter from myAgent</caption>
  * let rsp = gw.receive(ParameterRsp)
  * rsp.sender // = myAgentId; sender of the message
  * rsp.param  // = 'x'; parameter name that was get/set
  * rsp.value  // = 42;  value of the parameter that was set
  * rsp.readonly // = [false]; indicates if the parameter is read-only
  *
  *
  * @typedef {Message} ParameterRsp
  * @property {string} param - parameters name if only a single parameter value was requested
  * @property {Object} value - parameters value if only a single parameter was requested
  * @property {Map<string, Object>} values - a map of multiple parameter names and their values if multiple parameters were requested
  * @property {Array<boolean>} readonly - a list of booleans indicating if the parameters are read-only
  * @property {number} [index=-1] - index of parameter(s) being returned
  * @exports ParameterReq
  */
  const ParameterRsp = MessageClass('org.arl.fjage.param.ParameterRsp');

  /**
  * Services supported by fjage agents.
  */
  const Services = {
    SHELL : 'org.arl.fjage.shell.Services.SHELL'
  };

  init();

  exports.AgentID = AgentID;
  exports.Gateway = Gateway;
  exports.GenericMessage = GenericMessage;
  exports.JSONMessage = JSONMessage;
  exports.Message = Message;
  exports.MessageClass = MessageClass;
  exports.ParameterReq = ParameterReq;
  exports.ParameterRsp = ParameterRsp;
  exports.Performative = Performative;
  exports.Services = Services;

}));
//# sourceMappingURL=fjage.js.map
