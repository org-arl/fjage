import { Performative } from './performative.js';
import { UUID7 } from './utils.js';
import { AgentID } from './agentid.js';  // import AgentID class for type checking. Remove if not needed.

/**
 * @typedef MessageJSON
 * @property {string} clazz
 * @property {Record<string, unknown>} data
 */;


/**
* Base class for messages transmitted by one agent to another. Creates an empty message.
*/
export class Message {
  /**
   * unique message ID
   * @type {string}
   */
  msgID;

  /**
   * performative of the message
   * @type {Performative}
   */
  perf;

  /**
   * AgentID of the sender of the message
   * @type {AgentID}
   */
  sender;

  /**
   * AgentID of the recipient of the message
   * @type {AgentID}
   */
  recipient;

  /**
   * ID of the message to which this message is a response
   * @type {string}
   */
  inReplyTo;

  /**
   * timestamp when the message was sent
   * @type {number}
   */
  sentAt;

  /**
  * @param {Message} [inReplyToMsg] - message to which this message is a response
  * @param {Performative} [perf=Performative.INFORM] - performative of the message
  */
  constructor(inReplyToMsg, perf=Performative.INFORM) {
    this.__clazz__ = 'org.arl.fjage.Message';
    this.msgID = UUID7.generate().toString();
    this.perf = perf;
    this.sender = null;
    this.recipient = inReplyToMsg ? inReplyToMsg.sender : null;
    this.inReplyTo = inReplyToMsg ? inReplyToMsg.msgID : null;
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

  /** Convert a message into a object for JSON serialization.
  *
  * NOTE: we don't do any base64 encoding for TX as
  *       we don't know what data type is intended
  *
  * @return {Object} - JSON string representation of the message
  */
  toJSON() {
    let props = {};
    for (let key in this) {
      if (key.startsWith('_')) continue; // skip private properties
      // @ts-ignore
      props[key] = this[key];
    }
    return { 'clazz': this.__clazz__, 'data': props };
  }

  /**
  * Create a message from a object parsed from the JSON representation of the message.
  *
  * @param {MessageJSON} jsonObj - Object containing all the properties of the message
  * @returns {Message} - A message created from the Object
  */
  static fromJSON(jsonObj) {
    if (!('clazz' in jsonObj) || typeof jsonObj.clazz !== 'string' || !('data' in jsonObj) || typeof jsonObj.data !== 'object') {
      throw new Error(`Invalid Object for Message : ${jsonObj}`);
    }
    let qclazz = jsonObj.clazz;
    let clazz = qclazz.replace(/^.*\./, '');
    /** @type Message */
    let rv = MessageClass[clazz] ? new MessageClass[clazz] : new Message();
    rv.__clazz__ = qclazz;
    // copy all properties from the data object
    for (var key in jsonObj.data){
      if (key === 'sender' || key === 'recipient') {
        const val = jsonObj.data[key];
        if (val && typeof val === 'string') {
          rv[key] = AgentID.fromJSON(val);
        }
      } else rv[key] = jsonObj.data[key];
    }
    return rv;
  }
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
  constructor() {
    super();
    this.__clazz__ = 'org.arl.fjage.GenericMessage';
  }
}

/**
* Creates a unqualified message class based on a fully qualified name.
*
* @template {object} T - additional properties that the subclass adds to {@link Message}
* @template {object} P - constructor parameters
* @param {string} name - fully qualified name of the message class to be created
* @param {typeof Message} [parent] - class of the parent MessageClass to inherit from
* @returns {new (params?: Record<string, unknown>) => T & Message}
*
* @constructs Message
* @example
* const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');
* let pReq = new ParameterReq()
*/
export function MessageClass(name, parent=Message) {
  let sname = name.replace(/^.*\./, '');
  if (MessageClass[sname]) return MessageClass[sname];
  let cls = class extends parent {
    /**
    * @param {P} params
    */
    constructor(params) {
      super();
      this.__clazz__ = name;
      if (params){
        const keys = Object.keys(params);
        for (let k of keys) {
          this[k] = params[k];
        }
      }
      if (name.endsWith('Req')) this.perf = Performative.REQUEST;
    }
  };
  cls.__clazz__ = name;
  MessageClass[sname] = cls;

  // Safety: the assumption is that the caller is correct when passing T.
  // This is a type hint and should thus be treated as such. There's no
  // guarantee that the caller's type is correct, but it is their responsibility
  // to pass the right type.
  /** @type {any} */
  const ret = cls;
  return /** @type {new (args: P) => T & Message} */ ret;
}

/**
 * @typedef {Pick<Message, "perf" | "sender" | "recipient" | "inReplyTo">} MessageProps
 */

/**
* @typedef {object} ParameterReqEntry
* @property {string} param - parameter name
* @property {unknown} [value] - parameter value
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
* @typedef {object} ParameterReqProps
* @property {string} param - parameters name to be get/set if only a single parameter is to be get/set
* @property {unknown} value - parameters value to be set if only a single parameter is to be set
* @property {Array<ParameterReqEntry>} requests - a list of multiple parameters to be get/set
* @property {number} [index=-1] - index of parameter(s) to be set
*
* @typedef {ParameterReqProps & Message} ParameterReq
*
* @type {new (params?: ParameterReqProps & MessageProps) => ParameterReq}
*/
export const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');

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
* @typedef {object} ParameterRspProps
* @property {string} param - parameters name if only a single parameter value was requested
* @property {unknown} value - parameters value if only a single parameter was requested
* @property {Record<string, unknown>} values - a map of multiple parameter names and their values if multiple parameters were requested
* @property {Array<boolean>} readonly - a list of booleans indicating if the parameters are read-only
* @property {number} [index=-1] - index of parameter(s) being returned
*
* @typedef {ParameterRspProps & Message} ParameterRsp
*
* @type {new (params?: ParameterRspProps & MessageProps) => ParameterRsp}
*/
export const ParameterRsp = MessageClass('org.arl.fjage.param.ParameterRsp');

/**
* Request to write contents to a file, or delete a file.
*
* @typedef {object} PutFileReqProps
* @property {string} filename - name of the file to be written to or deleted
* @property {number} [content] - content to be written to the file as a byte array. If not provided, the file will be deleted.
* @property {number} [offset=0] - offset in the file to write the content to.
*
* @typedef {PutFileReqProps & Message} PutFileReq
*
* @type {new (params?: PutFileReqProps & MessageProps) => PutFileReq}
*/
export const PutFileReq = MessageClass('org.arl.fjage.shell.PutFileReq');


/**
* Request to read a file or a directory.
*
* If the filename specified represents a directory, then the contents of the
* directory (list of files) are returned as a tab separated string with:
*   filename, file size, last modification time.
*
* The time is represented as epoch time (milliseconds since 1 Jan 1970).
*
* @typedef {object} GetFileReqProps
* @property {string} filename - name of the file or directory to be read
* @property {number} [offset=0] - offset in the file to read from (ignored for directories)
* @property {number} [length=0] - number of bytes to read from the file (ignored for directories). If 0,
* the entire file will be read.
*
* @typedef {GetFileReqProps & Message} GetFileReq
*
* @type {new (params?: GetFileReqProps & MessageProps) => GetFileReq}
*/
export const GetFileReq = MessageClass('org.arl.fjage.shell.GetFileReq');

/**
* Response to a {@link GetFileReq}, with the contents of the file or the directory.
*
* @typedef {object} GetFileRspProps
* @property {string} filename - name of the file or directory that was read
* @property {number} [content] - content of the file as a byte array. If the filename represents a directory,
* the content consists of a list of files (one file per line). Each line starts with the filename
* (with a trailing "/" if it is a directory), "\t", file size in bytes, "\t", and last modification date.
* @property {number} [offset=0] - offset in the file that the content corresponds to (ignored for directories)
* @property {boolean} [directory=false] - indicates if the filename represents a directory
*
* @typedef {GetFileRspProps & Message} GetFileRsp
*
* @type {new (params?: GetFileRspProps & MessageProps) => GetFileRsp}
*/
export const GetFileRsp = MessageClass('org.arl.fjage.shell.GetFileRsp');

/**
* Request to execute shell command/script.
*
* @typedef {object} ShellExecReqProps
* @property {string} command - shell command or script to be executed
* @property {boolean} ans  - indicates if the response to this request should include the output of the command/script execution
*
* @typedef {ShellExecReqProps & Message} ShellExecReq
*
* @type {new (params?: ShellExecReqProps & MessageProps) => ShellExecReq}
*/
export const ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq');
