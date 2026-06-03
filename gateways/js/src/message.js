import { Performative } from './performative.js';
import { UUID7 } from './utils.js';
import { AgentID } from './agentid.js';  // import AgentID class for type checking. Remove if not needed.

/**
 * Registry of message classes for de/serialization.
 * @type {Object.<string, typeof Message>}
 */
const _MESSAGE_REGISTRY = {};

/**
* Base class for messages transmitted by one agent to another. Creates an empty message.
*
* @property {string} msgID - unique message ID
* @property {Performative} perf - performative of the message
* @property {AgentID} [sender] - AgentID of the sender of the message
* @property {AgentID} [recipient] - AgentID of the recipient of the message
* @property {string} [inReplyTo] - ID of the message to which this message is a response
* @property {number} [sentAt] - timestamp when the message was sent
*/
export class Message {

  msgID = UUID7.generate().toString();
  perf = Performative.INFORM;
  sender = null;
  recipient = null;
  inReplyTo = null;
  sentAt = 0;

  constructor(values = {}) {
    Object.assign(this, values);
    // if the message class name ends with "Req" and the performative is not set, set the performative to REQUEST
    if (this.constructor.name.toLowerCase().endsWith('req')) this.perf = Performative.REQUEST;
  }

  static _fields() {
    return Object.keys(new this());
  }

  /**
  * Gets a string representation of the message.
  *
  * @returns {string} - string representation
  */
  toString() {
    let p = this.perf ? this.perf.toString() : 'MESSAGE';
    if (this.__clazz__ == 'org.arl.fjage.Message') return p;
    return p + ': ' + this.__clazz__.replace(/^.*\./, '');
  }


  /**
  * @typedef {Object} MessageJSON
  * @property {string} clazz - Fully qualified class name of the message
  * @property {Object.<string, *>} data - Message data as key-value pairs with string keys
  */

  /** Convert a message into a object for JSON serialization.
  *
  * NOTE: we don't do any base64 encoding for TX as
  *       we don't know what data type is intended
  *
  * @return {MessageJSON} - JSON string representation of the message
  */
  toJSON() {
    let props = {};
    const cls = /** @type {typeof Message} */ (this.constructor);
    for (const field of cls._fields()) {
      if (field.startsWith('_')) continue; // skip private properties
      // @ts-ignore
      props[field] = this[field];
    }
    return { 'clazz': this.__clazz__, 'data': props };
  }

  /**
  * Create a message from a object parsed from the JSON representation of the message.
  *
  * @param {MessageJSON} jsonObj - JS Object parsed from the Message's JSON string with `.clazz` (string) and `.data` (object with string keys)
  * @returns {Message} - A message created from the Object
  *
  */
  static fromJSON(jsonObj) {
    if (!( 'clazz' in jsonObj) || !( 'data' in jsonObj)) {
      throw new Error(`Invalid Object for Message : ${jsonObj}`);
    }
    let clazz = jsonObj.clazz.replace(/^.*\./, '');
    let rv = _MESSAGE_REGISTRY[clazz] ? new _MESSAGE_REGISTRY[clazz]() : new Message();

    // copy all properties from the data object
    for (var key in jsonObj.data) {
      const cls = /** @type {typeof Message} */ (rv.constructor);
      if (cls._fields().includes(key)) {
        if (key === 'sender' || key === 'recipient') {
          if (jsonObj.data[key] && typeof jsonObj.data[key] === 'string') {
            rv[key] = AgentID.fromJSON(jsonObj.data[key]);
          }
        } else rv[key] = jsonObj.data[key];
      } else if (rv instanceof GenericMessage) {
        rv[key] = jsonObj.data[key];
      } else {
        console.warn(`Property '${key}' in JSON data is not a valid field for message class '${jsonObj.clazz}'`);
      }
    }
    return rv;
  }
}

/**
 * Default class name for messages that are not of a specific subclass.
 */
Message.prototype.__clazz__ = 'org.arl.fjage.Message';

/**
 * Registers a message class for de/serialization. The message class must be a subclass of {@link Message} .
 *
 * @param {typeof Message} cls - message class to be registered
 * @param {String} fqcn - fully qualified class name of the message class
 */
export function registerMessageClass(cls, fqcn) {
  // if cls is not a subclass of Message, throw an error
  if (!(cls.prototype instanceof Message)) {
    throw new Error(`Class ${cls.name} is not a subclass of Message`);
  }

  // if fqcn is a string set the __clazz__ property of the class to the fqcn
  if (typeof fqcn === 'string' && fqcn.trim() !== '') cls.prototype.__clazz__ = fqcn;

  // if name is already registered with a different class, log a warning
  if (cls.name != 'cls'){
    if (cls.name in _MESSAGE_REGISTRY && _MESSAGE_REGISTRY[cls.name] != cls) {
      console.warn(`Overriding existing message class registered with name '${cls.name}': ${_MESSAGE_REGISTRY[cls.name]} -> ${cls}`);
    }

    // also register the class with its unqualified name (last part of the fully qualified name) if it is not already registered with a different class
    if (cls.name.split('.').slice(-1)[0] in _MESSAGE_REGISTRY && _MESSAGE_REGISTRY[cls.name.split('.').slice(-1)[0]] != cls) {
      console.warn(`Overriding existing message class registered with unqualified name '${cls.name.split('.').slice(-1)[0]}': ${_MESSAGE_REGISTRY[cls.name.split('.').slice(-1)[0]]} -> ${cls}`);
    }
  }

  // if fqcn is already registered with a different class, log a warning
  if (fqcn in _MESSAGE_REGISTRY && _MESSAGE_REGISTRY[fqcn] != cls) {
    console.warn(`Overriding existing message class registered with clazz '${fqcn}': ${_MESSAGE_REGISTRY[fqcn]} -> ${cls}`);
  }

  _MESSAGE_REGISTRY[cls.name] = cls;
  if (typeof fqcn === 'string' && fqcn.trim() !== ''){
    _MESSAGE_REGISTRY[fqcn] = cls;
    _MESSAGE_REGISTRY[fqcn.split('.').slice(-1)[0]] = cls;
  }
  return cls;
}

/**
*  @deprecated since version 3.0.0. Use {@link registerMessageClass} instead.
*
* Creates a unqualified message class based on a fully qualified name.
* @param {string} name - fully qualified name of the message class to be created
* @param {typeof Message} [parent] - class of the parent MessageClass to inherit from
* @constructs Message
* @example
* const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');
* let pReq = new ParameterReq()
* @returns {typeof Message} - the message class
*/
export function MessageClass(name, parent=Message) {
  // if parent is not a subclass of Message, throw an error
  if (!(parent == Message || parent.prototype instanceof Message)) {
    throw new Error(`Parent class ${parent.name} is not a subclass of Message`);
  }
  let sname = name.replace(/^.*\./, '');
  if (sname in _MESSAGE_REGISTRY) return _MESSAGE_REGISTRY[sname];
  let cls = class extends parent {
    /**
    * @param {{ [x: string]: any; }} fields
    */
    constructor(fields={}) {
      super(fields);
      // if the message class name ends with "Req" and the performative is not set, set the performative to REQUEST
      if (name.endsWith('Req') && !fields.perf) this.perf = Performative.REQUEST;
    }
  };
  return registerMessageClass(cls, name);
}

/**
* A message class that can convey generic messages represented by key-value pairs.
* @class
* @extends Message
*/
export class GenericMessage extends Message {}
registerMessageClass(GenericMessage, 'org.arl.fjage.GenericMessage');

/**
* @typedef {Object} ParameterReq.Entry
* @property {string} param - parameter name
* @property {Object} value - parameter value
* @exports ParameterReq.Entry
*/

/**
* A message that requests one or more parameters of an agent.
*
* @example <caption>Setting a parameter myAgent.x to 42</caption>
* let req = new ParameterReq({
*  recipient: myAgentId,
*  param: 'x',
*  value: 42
* });
*
* @example <caption>Getting the value of myAgent.x</caption>
* let req = new ParameterReq({
* recipient: myAgentId,
* param: 'x'
* });
*
* @property {string} param - parameters name to be get/set if only a single parameter is to be get/set
* @property {Object} value - parameters value to be set if only a single parameter is to be set
* @property {Array<ParameterReq.Entry>} requests - a list of multiple parameters to be get/set
* @property {number} [index=-1] - index of parameter(s) to be set*
* @exports ParameterReq
*/
export class ParameterReq extends Message {
  param = null;
  value = null;
  /** @type {Array<ParameterReq.Entry>} */
  requests = [];
  index = -1;
}
registerMessageClass(ParameterReq, 'org.arl.fjage.param.ParameterReq');

/**
* A message that is a response to a {@link ParameterReq} message.
*
* @example <caption>Receiving a parameter from myAgent</caption>
* let rsp = gw.receive(ParameterRsp)
* rsp.sender // = myAgentId; sender of the message
* rsp.param  // = 'x'; parameter name that was get/set
* rsp.value  // = 42;  value of the parameter that was set
* rsp.readonly // = [false]; indicates if the parameter is read-only
*
*
* @property {string} param - parameters name if only a single parameter value was requested
* @property {Object} value - parameters value if only a single parameter was requested
* @property {Map<string, Object>} values - a map of multiple parameter names and their values if multiple parameters were requested
* @property {Array<boolean>} readonly - a list of booleans indicating if the parameters are read-only
* @property {number} [index=-1] - index of parameter(s) being returned
* @exports ParameterReq
*/
export class ParameterRsp extends Message {
  param = null;
  value = null;
  values = {};
  /** @type {Array<boolean>} */
  readonly = [];
  index = -1;
}
registerMessageClass(ParameterRsp, 'org.arl.fjage.param.ParameterRsp');

/**
* Request to write contents to a file, or delete a file.
*
* @property {string} filename - name of the file to be written to or deleted
* @property {number} [contents] - contents to be written to the file as a byte array. If not provided, the file will be deleted.
* @property {number} [offset=0] - offset in the file to write the contents to.
* @exports PutFileReq
*/
export class PutFileReq extends Message {
  filename = null;
  contents = null;
  offset = 0;
}
registerMessageClass(PutFileReq, 'org.arl.fjage.shell.PutFileReq');


/**
* Request to read a file or a directory.
*
* If the filename specified represents a directory, then the contents of the
* directory (list of files) are returned as a tab separated string with:
*   filename, file size, last modification time.
*
* The time is represented as epoch time (milliseconds since 1 Jan 1970).
*
* @property {string} filename - name of the file or directory to be read
* @property {number} [offset=0] - offset in the file to read from (ignored for directories)
* @property {number} [length=0] - number of bytes to read from the file (ignored for directories). If 0,
* the entire file will be read.
*  @exports GetFileReq
*/
export class GetFileReq extends Message {
  filename = null;
  offset = 0;
  length = 0;
}
registerMessageClass(GetFileReq, 'org.arl.fjage.shell.GetFileReq');

/**
* Response to a {@link GetFileReq}, with the contents of the file or the directory.
*
* @property {string} filename - name of the file or directory that was read
* @property {number} [contents] - contents of the file as a byte array. If the filename represents a directory,
* the content consists of a list of files (one file per line). Each line starts with the filename
* (with a trailing "/" if it is a directory), "\t", file size in bytes, "\t", and last modification date.
* @property {number} [offset=0] - offset in the file that the content corresponds to (ignored for directories)
* @property {boolean} [directory=false] - indicates if the filename represents a directory
* @exports GetFileRsp
*/
export class GetFileRsp extends Message {
  filename = null;
  contents = null;
  offset = 0;
  directory = false;
}
registerMessageClass(GetFileRsp, 'org.arl.fjage.shell.GetFileRsp');

/**
* Request to execute shell command/script.
*
* @property {string} command - shell command or script to be executed
* @property {boolean} ans  - indicates if the response to this request should include the output of the command/script execution
* @exports ShellExecReq
*/
export class ShellExecReq extends Message {
  command = null;
  ans = false;
}
registerMessageClass(ShellExecReq, 'org.arl.fjage.shell.ShellExecReq');