const DEFAULT_RECONNECT_TIME = 5000;       // ms, delay between retries to connect to the server.

/**
* @class
* @ignore
*/
class WSConnector {
  /**
   * WebSocket URL of the master container to connect to
   * @type {URL}
   */
  url;

  /**
   * Whether to try to reconnect if the connection is lost
   * @type {boolean}
   */
  keepAlive;

  /**
   * Time in milliseconds before a reconnection is attempted after an error
   * @type {number}
   */
  _reconnectTime;

  /**
   * Whether to log debug info to the console
   * @type {boolean}
   */
  debug;

  /**
   * Whether the Gateway has managed to connect to a server before
   * @type {boolean}
   */
  _firstConn;

  /**
   * Whether the Gateway has attempted to reconnect to a server before
   * @type {boolean}
   */
  _firstReConn;

  /**
   * List of callbacks make as soon as gateway is open
   * @type {(() => void)[]}
   */
  pendingOnOpen;

  /**
   * @callback ConnectionListener
   * @param {boolean} val
   * @returns {void}
   */

  /**
   * External listeners wanting to listen connection events
   * @type {ConnectionListener[]}
   */
  connListeners;

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
    this.debug = opts.debug || false;
    this._firstConn = true;
    this._firstReConn = true;
    this.pendingOnOpen = [];
    this.connListeners = [];
    this._websockSetup(this.url);
  }

  /**
   * @param {boolean} val
   */
  _sendConnEvent(val) {
    this.connListeners.forEach(l => {
      l && {}.toString.call(l) === '[object Function]' && l(val);
    });
  }

  /**
   * @param {string | URL} url
   */
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
  * @param {string} s - incoming message string
  * @returns {void}
  * @ignore
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
  * @param {ConnectionListener} listener - a listener callback that is called when the connection is opened/closed
  */
  addConnectionListener(listener){
    this.connListeners.push(listener);
  }

  /**
  * Remove listener for connection events
  * @param {ConnectionListener} listener - remove the listener for connection
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

export default WSConnector;
