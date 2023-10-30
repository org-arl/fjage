/**
 * @class
 * @ignore
 */
export default class WSConnector {
    /**
     * Create an WSConnector to connect to a fjage master over WebSockets
     * @param {Object} opts
     * @param {string} opts.hostname - hostname/ip address of the master container to connect to
     * @param {number} opts.port - port number of the master container to connect to
     * @param {string} opts.pathname - path of the master container to connect to
     * @param {boolean} opts.keepAlive - try to reconnect if the connection is lost
     * @param {number} [opts.reconnectTime=5000] - time before reconnection is attempted after an error
     */
    constructor(opts?: {
        hostname: string;
        port: number;
        pathname: string;
        keepAlive: boolean;
        reconnectTime?: number;
    });
    url: URL;
    _reconnectTime: number;
    _keepAlive: boolean;
    debug: any;
    _firstConn: boolean;
    _firstReConn: boolean;
    pendingOnOpen: any[];
    connListeners: any[];
    _sendConnEvent(val: any): void;
    _websockSetup(url: any): void;
    sock: WebSocket;
    _websockReconnect(): void;
    _onWebsockOpen(): void;
    toString(): string;
    /**
     * Write a string to the connector
     * @param {string} s - string to be written out of the connector to the master
     */
    write(s: string): boolean;
    /**
     * Set a callback for receiving incoming strings from the connector
     * @param {WSConnector~ReadCallback} cb - callback that is called when the connector gets a string
     * @ignore
     */
    setReadCallback(cb: any): void;
    _onWebsockRx: any;
    /**
     * @callback WSConnector~ReadCallback
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
