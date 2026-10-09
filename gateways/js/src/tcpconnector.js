const SOCKET_OPEN = 'open';
const SOCKET_OPENING = 'opening';
const DEFAULT_RECONNECT_TIME = 5000;       // ms, delay between retries to connect to the server.

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
    this._reconnectTime = opts.reconnectTime || DEFAULT_RECONNECT_TIME;
    this.url = new URL('tcp://localhost');
    this.url.hostname = host;
    this.url.port = port.toString();
    this._buf = '';
    this._firstConn = true;               // if the Gateway has managed to connect to a server before
    this._firstReConn = true;             // if the Gateway has attempted to reconnect to a server before
    this._closed = false;
    this._reconnectTimer = null;
    this.pendingOnOpen = [];              // list of callbacks make as soon as gateway is open
    this.connListeners = [];              // external listeners wanting to listen connection events
    this.debug = false;
    this._sockInit(host, port);
  }


  _sendConnEvent(val) {
    this.connListeners.forEach(l => {
      if (val && this._closed) return;
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
    }else{
      this._sockSetup(host, port);
    }
  }

  _sockSetup(host, port){
    if(this._closed || !createConnection) return;
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
    if (this._closed || this._reconnectTimer !== null || this._firstConn || !this._keepAlive || this.sock.readyState == SOCKET_OPENING || this.sock.readyState == SOCKET_OPEN) return;
    this._reconnectTimer = setTimeout(() => {
      this._reconnectTimer = null;
      this.pendingOnOpen = [];
      this._sockSetup(this.url.hostname, this.url.port);
    }, this._reconnectTime);
    if (this._firstReConn) {
      this._firstReConn = false;
      this._sendConnEvent(false);
    }
  }

  _onSockOpen() {
    if (this._closed) return;
    this._firstConn = false;
    this.sock.on('close', this._sockReconnect.bind(this));
    this.sock.on('data', this._processSockData.bind(this));
    this._buf = '';
    this._sendConnEvent(true);
    this.pendingOnOpen.forEach(cb => cb());
    this.pendingOnOpen.length = 0;
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
    if (this._closed) return false;
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
    if (this._closed) return;
    this._closed = true;
    clearTimeout(this._reconnectTimer);
    this._reconnectTimer = null;
    this.pendingOnOpen.length = 0;
    if (this.sock) {
      this.sock.removeAllListeners('connect');
      this.sock.removeAllListeners('close');
      this.sock.removeAllListeners('data');
      // Keep the error listener to handle errors from an aborted connection.
      if (this.sock.readyState == SOCKET_OPEN) this.sock.end('{"alive": false}\n');
      else this.sock.destroy();
    }
    this._sendConnEvent(false);
  }
}

export default TCPConnector;
