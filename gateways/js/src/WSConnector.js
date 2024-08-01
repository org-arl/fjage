const DEFAULT_RECONNECT_TIME = 5000;       // ms, delay between retries to connect to the server.

/**
 * @class
 * @ignore
 */
export default class WSConnector {

  /**
   * Create an WSConnector to connect to a fjage master over WebSockets
   * @param {Object} opts
   * @param {string} opts.hostname - hostname/ip address of the master container to connect to
   * @param {string} opts.port - port number of the master container to connect to
   * @param {string} opts.pathname - path of the master container to connect to
   * @param {boolean} opts.keepAlive - try to reconnect if the connection is lost
   * @param {number} [opts.reconnectTime=5000] - time before reconnection is attempted after an error
   */
  constructor(opts = {}) {
    this.url = new URL('ws://localhost');
    this.url.hostname = opts.hostname;
    this.url.port = opts.port;
    this.url.pathname = opts.pathname;
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
    this.pendingOnOpen = this.pendingOnOpen.filter(cb => !cb());
  }

  /**
   * Converts the websocket send API to return a boolean
   *
   * @param {String} msg message to be sent to the server
   * @returns {boolean} true if the message was sent successfully
   */
  _send(msg){
    try {
      this.sock.send(msg+'\n');
      return true;
    } catch (error) {
      return false;
    }
  }

  toString(){
    let s = '';
    s += 'WSConnector [' + this.sock ? this.sock.url.toString() : '' + ']';
    return s;
  }

  /**
   * Write a string to the connector
   * @param {string} s - string to be written out of the connector to the master
   * @returns {boolean} - true if the string was written successfully
   */
  write(s){
    if (!this.sock || this.sock.readyState == this.sock.CONNECTING){
      this.pendingOnOpen.push(() => this._send(s));
      return true;
    } else if (this.sock.readyState == this.sock.OPEN) {
      return this.sock.send(s+'\n');
    }
    return false;
  }

  /**
   * Set a callback for receiving incoming strings from the connector
   * @param {WSConnector~ReadCallback} cb - callback that is called when the connector gets a string
   * @ignore
   */
  setReadCallback(cb){
    if (cb && {}.toString.call(cb) === '[object Function]') this._onWebsockRx = cb;
  }

  /**
   * @callback WSConnector~ReadCallback
   * @ignore
   * @param {string} s - incoming message string
   */

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