const SOCKET_OPEN = 'open';
const SOCKET_OPENING = 'opening';
const DEFAULT_RECONNECT_TIME = 5000;       // ms, delay between retries to connect to the server.

/** @typedef {import('node:net').Socket & {send: (data: string) => void}} TCPConnectorSocket */

/**
 * @callback TCPConnectorReadCallback
 * @param {string} data - incoming message string
 * @returns {void}
 */

/** @type {typeof import('node:net').createConnection|undefined} */
var createConnection;

/**
* @class
* @ignore
*/
class TCPConnector {

  /** @type {URL} */
  url;

  /** @type {boolean|undefined} */
  _keepAlive;

  /** @type {number} */
  _reconnectTime;

  /** @type {string} */
  _buf;

  /** @type {boolean} */
  _firstConn;

  /** @type {boolean} */
  _firstReConn;

  /** @type {Array<() => void>} */
  pendingOnOpen;

  /** @type {Array<(connected: boolean) => void>} */
  connListeners;

  /** @type {boolean} */
  debug;

  /** @type {TCPConnectorSocket|undefined} */
  sock;

  /** @type {TCPConnectorReadCallback|undefined} */
  _onSockRx;

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
    this._reconnectTime = opts.reconnectTime || DEFAULT_RECONNECT_TIME;
    this.url = new URL('tcp://localhost');
    this.url.hostname = host;
    this.url.port = port.toString();
    this._buf = '';
    this._firstConn = true;               // if the Gateway has managed to connect to a server before
    this._firstReConn = true;             // if the Gateway has attempted to reconnect to a server before
    this.pendingOnOpen = [];              // list of callbacks invoked as soon as the gateway is open
    this.connListeners = [];              // external listeners wanting to listen connection events
    this.debug = false;
    this._sockInit(host, port);
  }

  /** @param {boolean} val */
  _sendConnEvent(val) {
    this.connListeners.forEach(l => {
      l && {}.toString.call(l) === '[object Function]' && l(val);
    });
  }

  /**
   * @param {string} host
   * @param {number} port
   */
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
    }else{
      this._sockSetup(host, port);
    }
  }

  /**
   * @param {string} host
   * @param {number} port
   */
  _sockSetup(host, port){
    if(!createConnection) return;
    try{
      this.sock = /** @type {TCPConnectorSocket} */ (createConnection({ 'host': host, 'port': port }));
      this.sock.setEncoding('utf8');
      this.sock.on('connect', this._onSockOpen.bind(this));
      this.sock.on('error', this._sockReconnect.bind(this));
      this.sock.on('close', () => {this._sendConnEvent(false);});
      this.sock.send = data => {this.sock.write(data);};
    } catch (error) {
      if(this.debug) console.log('Connection failed to ', host + ':' + port);
      return;
    }
  }

  _sockReconnect(){
    if (this._firstConn || !this._keepAlive || this.sock.readyState == SOCKET_OPENING || this.sock.readyState == SOCKET_OPEN) return;
    if (this._firstReConn) this._sendConnEvent(false);
    this._firstReConn = false;
    setTimeout(() => {
      this.pendingOnOpen = [];
      this._sockSetup(this.url.hostname, parseInt(this.url.port));
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

  /** @param {string} s */
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

  /** @returns {string} */
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
  * Set a callback for receiving incoming strings from the connector
  * @param {TCPConnectorReadCallback} cb - callback that is called when the connector gets a string
  * @returns {void}
  */
  setReadCallback(cb){
    if (cb && {}.toString.call(cb) === '[object Function]') this._onSockRx = cb;
  }

  /**
  * Add listener for connection events
  * @param {(connected: boolean) => void} listener - a listener callback that is called when the connection is opened/closed
  * @returns {void}
  */
  addConnectionListener(listener){
    this.connListeners.push(listener);
  }

  /**
  * Remove listener for connection events
  * @param {(connected: boolean) => void} listener - remove the listener for connection
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
  * @returns {void}
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

export default TCPConnector;
