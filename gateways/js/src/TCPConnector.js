const SOCKET_OPEN = 'open';
const SOCKET_OPENING = 'opening';
const DEFAULT_RECONNECT_TIME = 5000;       // ms, delay between retries to connect to the server.

var createConnection;

/**
 * @class
 * @ignore
 */
export default class TCPconnector {

  /**
    * Create an TCPConnector to connect to a fjage master over TCP
   * @param {Object} opts
   * @param {string} opts.hostname - hostname/ip address of the master container to connect to
   * @param {string} opts.port - port number of the master container to connect to
   * @param {boolean} opts.keepAlive - try to reconnect if the connection is lost
   * @param {number} [opts.reconnectTime=5000] - time before reconnection is attempted after an error
    */
  constructor(opts = {}) {
    let host = opts.hostname;
    let port = opts.port;
    this._keepAlive = opts.keepAlive;
    this._reconnectTime = opts.reconnectTime || DEFAULT_RECONNECT_TIME;
    this.url = new URL('tcp://localhost');
    this.url.hostname = host;
    this.url.port = port;
    this._buf = '';
    this._firstConn = true;               // if the Gateway has managed to connect to a server before
    this._firstReConn = true;             // if the Gateway has attempted to reconnect to a server before
    this.pendingOnOpen = [];              // list of callbacks make as soon as gateway is open
    this.connListeners = [];              // external listeners wanting to listen connection events
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
   * Set a callback for receiving incoming strings from the connector
   * @param {TCPConnector~ReadCallback} cb - callback that is called when the connector gets a string
   */
  setReadCallback(cb){
    if (cb && {}.toString.call(cb) === '[object Function]') this._onSockRx = cb;
  }

  /**
   * @callback TCPConnector~ReadCallback
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
