import { Performative } from './performative.js';
import { UUID7 } from './utils.js';
import { AgentID } from './agentid.js';

const MESSAGE_REGISTRY = Object.create(null);

/**
 * Gets a registered message class by name. Message classes are registered under both their
 * qualified and unqualified names, so either will resolve, but a qualified name never falls
 * back to a class registered for the same unqualified name in a different package.
 *
 * @param {string} name - qualified or unqualified message class name
 * @returns {Function|undefined} registered message class
 */
export function messageClassForName(name) {
  return MESSAGE_REGISTRY[name];
}

/**
 * Registers a message class for JSON serialization and inflation.
 *
 * @param {string} className - fully qualified message class name
 * @param {Function} messageClass - Message subclass to register
 * @returns {Function} registered message class
 */
export function registerMessageClass(className, messageClass) {
  if (typeof className !== 'string' || className.trim() === '') {
    throw new Error('Message class name must be a non-empty string');
  }
  if (!className.includes('.')) {
    throw new Error(`Message class name '${className}' must be fully qualified`);
  }
  if (typeof messageClass !== 'function' ||
      !(messageClass === Message || messageClass.prototype instanceof Message)) {
    throw new Error('Message class must be a subclass of Message');
  }
  const shortName = className.split('.').pop();
  if (!shortName) throw new Error('Message class name must not end with a dot');
  for (const name of [className, shortName]) {
    if (MESSAGE_REGISTRY[name] && MESSAGE_REGISTRY[name] !== messageClass) {
      console.warn(`Overriding existing message class registered with name '${name}'`);
    }
    MESSAGE_REGISTRY[name] = messageClass;
  }
  messageClass.prototype.__clazz__ = className;
  return messageClass;
}

/**
 * @typedef {Object} MessageJSON
 * @property {string} clazz - qualified or unqualified message class name
 * @property {Object.<string, *>} data - message data
 */

/**
 * Base class for messages transmitted by one agent to another.
 *
 * @property {string} msgID - unique message ID
 * @property {Performative} perf - performative of the message
 * @property {AgentID|null} sender - AgentID of the sender
 * @property {AgentID|null} recipient - AgentID of the recipient
 * @property {string|null} inReplyTo - ID of the message being replied to
 * @property {number} [sentAt] - timestamp when the message was sent, set by the container on receipt
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
  /** @type {number|undefined} */
  sentAt;

  /**
   * @param {Message} [inReplyToMsg] - message to reply to
   * @param {Performative} [perf] - performative of the message
   */
  constructor(inReplyToMsg, perf) {
    if (inReplyToMsg != null && !(inReplyToMsg instanceof Message)) {
      throw new TypeError('Message fields cannot be passed to the constructor, set them after construction; the first argument must be the Message being replied to');
    }
    this.msgID = UUID7.generate().toString();
    // messages named like Java requests default to a REQUEST performative
    this.perf = perf ?? (this.__clazz__.endsWith('Req') ? Performative.REQUEST : Performative.INFORM);
    this.sender = null;
    this.recipient = inReplyToMsg ? inReplyToMsg.sender : null;
    this.inReplyTo = inReplyToMsg ? inReplyToMsg.msgID : null;
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
    // Unregistered subclasses inherit their parent's __clazz__. Use their own name instead.
    let clazz = this.__clazz__;
    if (!Object.prototype.hasOwnProperty.call(this, '__clazz__') && messageClassForName(this.__clazz__) !== this.constructor) {
      clazz = this.constructor.name;
    }
    return {clazz: clazz, data};
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
    const registeredClass = messageClassForName(jsonObj.clazz);
    // @ts-ignore Registered classes are validated by registerMessageClass().
    const message = registeredClass ? new registeredClass() : new Message();
    if (!registeredClass) message.__clazz__ = jsonObj.clazz;

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

/** Fully qualified class name, set on the prototype of each registered message class. */
Message.prototype.__clazz__ = 'org.arl.fjage.Message';

registerMessageClass('org.arl.fjage.Message', Message);

/**
 * @deprecated since version 3.0.0. Use `Gateway.registerMessage()` instead.
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
  const registeredClass = messageClassForName(name);
  if (registeredClass) return registeredClass;
  // @ts-ignore Parent is validated above.
  const messageClass = class extends parent {
    constructor(fields={}) {
      super();
      Object.assign(this, fields);
    }
  };
  registerMessageClass(name, messageClass);
  return messageClass;
}

/** A message class that can convey generic key-value messages. */
export class GenericMessage extends Message {}

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

/** Request to write contents to a file, or delete a file. */
export class PutFileReq extends Message {
  /** @type {string|null} */
  filename = null;
  /** @type {Array<number>|null} */
  contents = null;
  /** @type {number} */
  offset = 0;
}

/** Request to delete a file or directory. */
export class DeleteFileReq extends Message {
  /** @type {string|null} */
  filename = null;
}

/** Request to read a file or directory. */
export class GetFileReq extends Message {
  /** @type {string|null} */
  filename = null;
  /** @type {number} */
  offset = 0;
  /** @type {number} */
  length = 0;
}

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

/** Request to execute a shell command or script. */
export class ShellExecReq extends Message {
  /** @type {string|null} */
  command = null;
  /** @type {boolean} */
  ans = false;
}

registerMessageClass('org.arl.fjage.GenericMessage', GenericMessage);
registerMessageClass('org.arl.fjage.param.ParameterReq', ParameterReq);
registerMessageClass('org.arl.fjage.param.ParameterRsp', ParameterRsp);
registerMessageClass('org.arl.fjage.shell.PutFileReq', PutFileReq);
registerMessageClass('org.arl.fjage.shell.DeleteFileReq', DeleteFileReq);
registerMessageClass('org.arl.fjage.shell.GetFileReq', GetFileReq);
registerMessageClass('org.arl.fjage.shell.GetFileRsp', GetFileRsp);
registerMessageClass('org.arl.fjage.shell.ShellExecReq', ShellExecReq);
