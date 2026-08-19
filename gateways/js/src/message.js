import { Performative } from './performative.js';
import { UUID7 } from './utils.js';
import { AgentID } from './agentid.js';

/** Registry of message classes for de/serialization. */
const _MESSAGE_REGISTRY = Object.create(null);

/**
 * @typedef {Object} MessageJSON
 * @property {string} clazz - fully qualified message class name
 * @property {Object.<string, *>} data - message data
 */

/**
 * Initialize the base fields of a message.
 *
 * @param {Message} message - message to initialize
 * @param {Message} [inReplyToMsg] - message to reply to
 * @param {Performative} [perf] - performative of the message
 */
function initializeMessage(message, inReplyToMsg, perf) {
  message.msgID = UUID7.generate().toString();
  message.perf = perf === undefined ? Performative.INFORM : perf;
  message.sender = null;
  message.recipient = null;
  message.inReplyTo = null;
  message.sentAt = 0;

  if (inReplyToMsg != null && !(inReplyToMsg instanceof Message)) {
    throw new TypeError('inReplyToMsg must be a Message');
  }
  if (inReplyToMsg) {
    message.recipient = inReplyToMsg.sender;
    message.inReplyTo = inReplyToMsg.msgID;
  }

  if (message.constructor.name.toLowerCase().endsWith('req') && perf === undefined) {
    message.perf = Performative.REQUEST;
  }
}

/**
 * Instantiate a message class for JSON inflation.
 *
 * @param {Function} messageClass - class to instantiate
 * @returns {Message} inflated message instance
 */
function instantiateMessage(messageClass) {
  try {
    // @ts-ignore Function is validated by registerMessage().
    return new messageClass();
  } catch (error) {
    if (!(error instanceof TypeError)) throw error;
    const message = Object.create(messageClass.prototype);
    initializeMessage(message);
    return message;
  }
}

/**
 * Base class for messages transmitted by one agent to another.
 *
 * @property {string} msgID - unique message ID
 * @property {Performative} perf - performative of the message
 * @property {AgentID|null} sender - AgentID of the sender
 * @property {AgentID|null} recipient - AgentID of the recipient
 * @property {string|null} inReplyTo - ID of the message being replied to
 * @property {number} sentAt - timestamp when the message was sent
 */
export class Message {
  /** @type {string} */
  msgID;
  /** @type {Performative} */
  perf;
  /** @type {AgentID|null} */
  sender;
  /** @type {AgentID|null} */
  recipient;
  /** @type {string|null} */
  inReplyTo;
  /** @type {number} */
  sentAt;

  /**
   * @param {Message} [inReplyToMsg] - message to reply to
   * @param {Performative} [perf] - performative of the message
   */
  constructor(inReplyToMsg, perf) {
    initializeMessage(this, inReplyToMsg, perf);
  }

  /**
   * Gets a string representation of the message.
   *
   * @returns {string} string representation
   */
  toString() {
    const p = this.perf ? this.perf.toString() : 'MESSAGE';
    if (this.__clazz__ === 'org.arl.fjage.Message') return p;
    return `${p}: ${this.__clazz__.replace(/^.*\./, '')}`;
  }

  /**
   * Convert a message into an object for JSON serialization.
   *
   * @returns {MessageJSON} JSON representation of the message
   */
  toJSON() {
    const data = {};
    for (const key of Object.keys(this)) {
      if (!key.startsWith('_')) data[key] = this[key];
    }
    return {clazz: this.__clazz__, data};
  }

  /**
   * Create a message from a parsed JSON representation.
   *
   * @param {MessageJSON} jsonObj - parsed message JSON
   * @returns {Message} message created from the JSON object
   */
  static fromJSON(jsonObj) {
    if (!jsonObj || typeof jsonObj !== 'object' || typeof jsonObj.clazz !== 'string' ||
        !jsonObj.data || typeof jsonObj.data !== 'object') {
      throw new Error(`Invalid Object for Message : ${jsonObj}`);
    }
    const shortName = jsonObj.clazz.replace(/^.*\./, '');
    const registeredClass = _MESSAGE_REGISTRY[jsonObj.clazz] || _MESSAGE_REGISTRY[shortName];
    const messageClass = registeredClass || Message;
    const message = instantiateMessage(messageClass);
    if (!registeredClass && jsonObj.clazz !== Message.prototype.__clazz__) {
      message.__clazz__ = jsonObj.clazz;
    }

    for (const key in jsonObj.data) {
      if ((key === 'sender' || key === 'recipient') && typeof jsonObj.data[key] === 'string') {
        message[key] = AgentID.fromJSON(jsonObj.data[key]);
      } else {
        message[key] = jsonObj.data[key];
      }
    }
    return message;
  }
}

/** Default class name for messages that are not of a specific subclass. */
Message.prototype.__clazz__ = 'org.arl.fjage.Message';

/**
 * Register a message class for JSON inflation.
 *
 * A received fully qualified name resolves exactly. A received unqualified
 * name resolves to the most recently registered class with that short name.
 *
 * @param {string} qualifiedName - fully qualified message class name
 * @param {Function} messageClass - class to register
 * @returns {Function} registered message class
 */
export function registerMessage(qualifiedName, messageClass) {
  if (typeof qualifiedName !== 'string' || qualifiedName.trim() === '') {
    throw new Error('Message class name must be a non-empty string');
  }
  if (typeof messageClass !== 'function' || !(messageClass.prototype instanceof Message)) {
    throw new Error('Message class must be a subclass of Message');
  }

  const shortName = qualifiedName.split('.').pop();
  if (_MESSAGE_REGISTRY[qualifiedName] && _MESSAGE_REGISTRY[qualifiedName] !== messageClass) {
    console.warn(`Overriding existing message class registered with name '${qualifiedName}'`);
  }
  if (_MESSAGE_REGISTRY[shortName] && _MESSAGE_REGISTRY[shortName] !== messageClass) {
    console.warn(`Overriding existing message class registered with short name '${shortName}'`);
  }
  messageClass.prototype.__clazz__ = qualifiedName;
  _MESSAGE_REGISTRY[qualifiedName] = messageClass;
  _MESSAGE_REGISTRY[shortName] = messageClass;
  return messageClass;
}

/**
 * @deprecated since version 3.0.0. Use {@link registerMessage} instead.
 *
 * Creates an unqualified message class based on a fully qualified name.
 *
 * @param {string} name - fully qualified message class name
 * @param {Function} [parent] - parent Message class
 * @returns {Function} message class
 */
export function MessageClass(name, parent=Message) {
  if (!(parent === Message || parent.prototype instanceof Message)) {
    throw new Error(`Parent class ${parent.name} is not a subclass of Message`);
  }
  const shortName = name.replace(/^.*\./, '');
  if (_MESSAGE_REGISTRY[shortName]) return _MESSAGE_REGISTRY[shortName];
  // @ts-ignore Parent is validated above.
  const messageClass = class extends parent {
    constructor(fields={}) {
      super();
      Object.assign(this, fields);
    }
  };
  registerMessage(name, messageClass);
  return messageClass;
}

/** A message class that can convey generic key-value messages. */
export class GenericMessage extends Message {}
registerMessage('org.arl.fjage.GenericMessage', GenericMessage);

/**
 * @typedef {Object} ParameterReq.Entry
 * @property {string} param - parameter name
 * @property {*} [value] - parameter value
 */

/** A message that requests one or more parameters of an agent. */
export class ParameterReq extends Message {
  /** @type {string|null} */
  param = null;
  /** @type {*} */
  value = null;
  /** @type {Array<ParameterReq.Entry>} */
  requests = [];
  /** @type {number} */
  index = -1;
}
registerMessage('org.arl.fjage.param.ParameterReq', ParameterReq);

/** A message that responds to a {@link ParameterReq}. */
export class ParameterRsp extends Message {
  /** @type {string|null} */
  param = null;
  /** @type {*} */
  value = null;
  /** @type {Object.<string, *>} */
  values = {};
  /** @type {Array<boolean>} */
  readonly = [];
  /** @type {number} */
  index = -1;
}
registerMessage('org.arl.fjage.param.ParameterRsp', ParameterRsp);

/** Request to write contents to a file, or delete a file. */
export class PutFileReq extends Message {
  /** @type {string|null} */
  filename = null;
  /** @type {Array<number>|null} */
  contents = null;
  /** @type {number} */
  offset = 0;
}
registerMessage('org.arl.fjage.shell.PutFileReq', PutFileReq);

/** Request to delete a file or directory. */
export class DeleteFileReq extends Message {
  /** @type {string|null} */
  filename = null;
}
registerMessage('org.arl.fjage.shell.DeleteFileReq', DeleteFileReq);

/** Request to read a file or directory. */
export class GetFileReq extends Message {
  /** @type {string|null} */
  filename = null;
  /** @type {number} */
  offset = 0;
  /** @type {number} */
  length = 0;
}
registerMessage('org.arl.fjage.shell.GetFileReq', GetFileReq);

/** Response to a {@link GetFileReq}. */
export class GetFileRsp extends Message {
  /** @type {string|null} */
  filename = null;
  /** @type {Array<number>|null} */
  contents = null;
  /** @type {number} */
  offset = 0;
  /** @type {boolean} */
  directory = false;
}
registerMessage('org.arl.fjage.shell.GetFileRsp', GetFileRsp);

/** Request to execute a shell command or script. */
export class ShellExecReq extends Message {
  /** @type {string|null} */
  command = null;
  /** @type {boolean} */
  ans = false;
}
registerMessage('org.arl.fjage.shell.ShellExecReq', ShellExecReq);
