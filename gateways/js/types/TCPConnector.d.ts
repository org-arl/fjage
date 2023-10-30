/**
 * @class
 * @ignore
 */
export default class TCPconnector {
    /**
      * Create an TCPConnector to connect to a fjage master over TCP
      * @param {Object} opts
      * @param {String} [opts.hostname='localhost'] - ip address/hostname of the master container to connect to
      * @param {number} opts.port - port number of the master container to connect to
      */
    constructor(opts?: {
        hostname?: string;
        port: number;
    });
    url: URL;
    _buf: string;
    pendingOnOpen: any[];
    connListeners: any[];
    _sendConnEvent(val: any): void;
    _sockInit(host: any, port: any): void;
    _sockSetup(host: any, port: any): void;
    sock: any;
    _sockReconnect(): void;
    _firstReConn: boolean;
    _onSockOpen(): void;
    _processSockData(s: any): void;
    toString(): string;
    /**
     * Write a string to the connector
     * @param {string} s - string to be written out of the connector to the master
     * @return {boolean} - true if connect was able to write or queue the string to the underlying socket
     */
    write(s: string): boolean;
    /**
     * Set a callback for receiving incoming strings from the connector
     * @param {TCPConnector~ReadCallback} cb - callback that is called when the connector gets a string
     */
    setReadCallback(cb: any): void;
    _onSockRx: any;
    /**
     * @callback TCPConnector~ReadCallback
     * @ignore
     * @param {string} s - incoming message string
     */
    /**
     * Add listener for connection events
     * @param {function} listener - a listener callback that is called when the connection is opened/closed
     */
    addConnectionListener(listener: Function): void;
    /**
     * Remove listener for connection events
     * @param {function} listener - remove the listener for connection
     * @return {boolean} - true if the listner was removed successfully
     */
    removeConnectionListener(listener: Function): boolean;
    /**
     * Close the connector
     */
    close(): void;
}
