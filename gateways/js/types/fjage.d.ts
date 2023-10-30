/**
 * Creates a unqualified message class based on a fully qualified name.
 * @param {string} name - fully qualified name of the message class to be created
 * @param {class} [parent=Message] - class of the parent MessageClass to inherit from
 * @returns {function} - constructor for the unqualified message class
 * @example
 * const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');
 * let pReq = new ParameterReq()
 */
export function MessageClass(name: string, parent?: class): Function;
/**
 * An action represented by a message. The performative actions are a subset of the
 * FIPA ACL recommendations for interagent communication.
 */
export type Performative = any;
export namespace Performative {
    let REQUEST: string;
    let AGREE: string;
    let REFUSE: string;
    let FAILURE: string;
    let INFORM: string;
    let CONFIRM: string;
    let DISCONFIRM: string;
    let QUERY_IF: string;
    let NOT_UNDERSTOOD: string;
    let CFP: string;
    let PROPOSE: string;
    let CANCEL: string;
}
/**
 * An identifier for an agent or a topic.
 * @class
 * @param {string} name - name of the agent
 * @param {boolean} topic - name of topic
 * @param {Gateway} owner - Gateway owner for this AgentID
 */
export class AgentID {
    constructor(name: any, topic: any, owner: any);
    name: any;
    topic: any;
    owner: any;
    /**
     * Gets the name of the agent or topic.
     *
     * @returns {string} - name of agent or topic
     */
    getName(): string;
    /**
     * Returns true if the agent id represents a topic.
     *
     * @returns {boolean} - true if the agent id represents a topic, false if it represents an agent
     */
    isTopic(): boolean;
    /**
     * Sends a message to the agent represented by this id.
     *
     * @param {string} msg - message to send
     * @returns {void}
     */
    send(msg: string): void;
    /**
     * Sends a request to the agent represented by this id and waits for a reponse.
     *
     * @param {Message} msg - request to send
     * @param {number} [timeout=1000] - timeout in milliseconds
     * @returns {Promise<Message>} - response
     */
    request(msg: Message, timeout?: number): Promise<Message>;
    /**
     * Gets a string representation of the agent id.
     *
     * @returns {string} - string representation of the agent id
     */
    toString(): string;
    /**
     * Gets a JSON string representation of the agent id.
     *
     * @returns {string} - JSON string representation of the agent id
     */
    toJSON(): string;
    /**
     * Sets parameter(s) on the Agent referred to by this AgentID.
     *
     * @param {(string|string[])} params - parameters name(s) to be set
     * @param {(Object|Object[])} values - parameters value(s) to be set
     * @param {number} [index=-1] - index of parameter(s) to be set
     * @param {number} [timeout=5000] - timeout for the response
     * @returns {Promise<(Object|Object[])>} - a promise which returns the new value(s) of the parameters
     */
    set(params: (string | string[]), values: (any | any[]), index?: number, timeout?: number): Promise<(any | any[])>;
    /**
     * Gets parameter(s) on the Agent referred to by this AgentID.
     *
     * @param {(?string|?string[])} params - parameters name(s) to be get, null implies get value of all parameters on the Agent
     * @param {number} [index=-1] - index of parameter(s) to be get
     * @param {number} [timeout=5000] - timeout for the response
     * @returns {Promise<(?Object|?Object[])>} - a promise which returns the value(s) of the parameters
     */
    get(params: ((string | (string[] | null)) | null), index?: number, timeout?: number): Promise<((any | (any[] | null)) | null)>;
}
/**
 * Base class for messages transmitted by one agent to another. Creates an empty message.
 * @class
 * @param {Message} inReplyTo - message to which this response corresponds to
 * @param {Performative} - performative
 */
export class Message {
    /** @private */
    private static _deserialize;
    constructor(inReplyTo?: {
        msgID: any;
        sender: any;
    }, perf?: string);
    __clazz__: string;
    msgID: string;
    sender: any;
    recipient: any;
    perf: string;
    inReplyTo: any;
    /**
     * Gets a string representation of the message.
     *
     * @returns {string} - string representation
     */
    toString(): string;
    /** @private */
    private _serialize;
    /** @private */
    private _inflate;
}
/**
 * A message class that can convey generic messages represented by key-value pairs.
 * @class
 * @extends Message
 */
export class GenericMessage extends Message {
    /**
     * Creates an empty generic message.
     */
    constructor();
}
/**
 * A gateway for connecting to a fjage master container. The new version of the constructor
 * uses an options object instead of individual parameters. The old version with
 *
 *
 * @class
 * @param {Object} opts
 * @param {string} [opts.hostname="localhost"] - hostname/ip address of the master container to connect to
 * @param {number} [opts.port=1100]          - port number of the master container to connect to
 * @param {string} [opts.pathname=""]        - path of the master container to connect to (for WebSockets)
 * @param {string} [opts.keepAlive=true]     - try to reconnect if the connection is lost
 * @param {number} [opts.queueSize=128]      - size of the queue of received messages that haven't been consumed yet
 * @param {number} [opts.timeout=1000]       - timeout for fjage level messages in ms
 * @param {string} [hostname="localhost"]    - <strike>Deprecated : hostname/ip address of the master container to connect to</strike>
 * @param {number} [port=]                   - <strike>Deprecated : port number of the master container to connect to</strike>
 * @param {string} [pathname=="/ws/"]        - <strike>Deprecated : path of the master container to connect to (for WebSockets)</strike>
 * @param {number} [timeout=1000]            - <strike>Deprecated : timeout for fjage level messages in ms</strike>
 */
export class Gateway {
    constructor(opts: {}, port: any, pathname?: string, timeout?: number);
    _timeout: any;
    _keepAlive: any;
    _queueSize: any;
    pending: {};
    subscriptions: {};
    listener: {};
    eventListeners: {};
    queue: any[];
    debug: boolean;
    aid: AgentID;
    connector: TCPConnector | WSConnector;
    /** @private */
    private _sendEvent;
    /** @private */
    private _onMsgRx;
    /** @private */
    private _msgTx;
    /** @private */
    private _msgTxRx;
    /** @private */
    private _createConnector;
    /** @private */
    private _matchMessage;
    /** @private */
    private _getMessageFromQueue;
    /** @private */
    private _getGWCache;
    /** @private */
    private _addGWCache;
    /** @private */
    private _removeGWCache;
    /** @private */
    private _update_watch;
    /**
     * Add an event listener to listen to various events happening on this Gateway
     *
     * @param {string} type - type of event to be listened to
     * @param {function} listener - new callback/function to be called when the event happens
     * @returns {void}
     */
    addEventListener(type: string, listener: Function): void;
    /**
     * Remove an event listener.
     *
     * @param {string} type - type of event the listener was for
     * @param {function} listener - callback/function which was to be called when the event happens
     * @returns {void}
     */
    removeEventListener(type: string, listener: Function): void;
    /**
     * Add a new listener to listen to all {Message}s sent to this Gateway
     *
     * @param {function} listener - new callback/function to be called when a {Message} is received
     * @returns {void}
     */
    addMessageListener(listener: Function): void;
    /**
     * Remove a message listener.
     *
     * @param {function} listener - removes a previously registered listener/callback
     * @returns {void}
     */
    removeMessageListener(listener: Function): void;
    /**
     * Add a new listener to get notified when the connection to master is created and terminated.
     *
     * @param {function} listener - new callback/function to be called connection to master is created and terminated
     * @returns {void}
     */
    addConnListener(listener: Function): void;
    /**
     * Remove a connection listener.
     *
     * @param {function} listener - removes a previously registered listener/callback
     * @returns {void}
     */
    removeConnListener(listener: Function): void;
    /**
     * Gets the agent ID associated with the gateway.
     *
     * @returns {string} - agent ID
     */
    getAgentID(): string;
    /**
     * Get an AgentID for a given agent name.
     *
     * @param {string} name - name of agent
     * @returns {AgentID} - AgentID for the given name
     */
    agent(name: string): AgentID;
    /**
     * Returns an object representing the named topic.
     *
     * @param {string|AgentID} topic - name of the topic or AgentID
     * @param {string} topic2 - name of the topic if the topic param is an AgentID
     * @returns {AgentID} - object representing the topic
     */
    topic(topic: string | AgentID, topic2: string): AgentID;
    /**
     * Subscribes the gateway to receive all messages sent to the given topic.
     *
     * @param {AgentID} topic - the topic to subscribe to
     * @returns {boolean} - true if the subscription is successful, false otherwise
     */
    subscribe(topic: AgentID): boolean;
    /**
     * Unsubscribes the gateway from a given topic.
     *
     * @param {AgentID} topic - the topic to unsubscribe
     * @returns {void}
     */
    unsubscribe(topic: AgentID): void;
    /**
     * Finds an agent that provides a named service. If multiple agents are registered
     * to provide a given service, any of the agents' id may be returned.
     *
     * @param {string} service - the named service of interest
     * @returns {Promise<?AgentID>} - a promise which returns an agent id for an agent that provides the service when resolved
     */
    agentForService(service: string): Promise<AgentID | null>;
    /**
     * Finds all agents that provides a named service.
     *
     * @param {string} service - the named service of interest
     * @returns {Promise<?AgentID[]>} - a promise which returns an array of all agent ids that provides the service when resolved
     */
    agentsForService(service: string): Promise<AgentID[] | null>;
    /**
     * Sends a message to the recipient indicated in the message. The recipient
     * may be an agent or a topic.
     *
     * @param {Message} msg - message to be sent
     * @returns {boolean} - if sending was successful
     */
    send(msg: Message): boolean;
    /**
     * Flush the Gateway queue for all pending messages. This drops all the pending messages.
     * @returns {void}
     *
     */
    flush(): void;
    /**
     * Sends a request and waits for a response. This method returns a {Promise} which resolves when a response
     * is received or if no response is received after the timeout.
     *
     * @param {string} msg - message to send
     * @param {number} [timeout=1000] - timeout in milliseconds
     * @returns {Promise<?Message>} - a promise which resolves with the received response message, null on timeout
     */
    request(msg: string, timeout?: number): Promise<Message | null>;
    /**
     * Returns a response message received by the gateway. This method returns a {Promise} which resolves when
     * a response is received or if no response is received after the timeout.
     *
     * @param {function} [filter=] - original message to which a response is expected, or a MessageClass of the type
     * of message to match, or a closure to use to match against the message
     * @param {number} [timeout=0] - timeout in milliseconds
     * @returns {Promise<?Message>} - received response message, null on timeout
     */
    receive(filter?: Function, timeout?: number): Promise<Message | null>;
    /**
     * Closes the gateway. The gateway functionality may not longer be accessed after
     * this method is called.
     * @returns {void}
     */
    close(): void;
}
export namespace Services {
    let SHELL: string;
}
import TCPConnector from './TCPConnector';
import WSConnector from './WSConnector';
