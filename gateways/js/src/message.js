import { Performative } from './performative.js';
import { UUID7 } from './utils.js';
import { AgentID } from './agentid.js';  // import AgentID class for type checking. Remove if not needed.

/**
* Base class for messages transmitted by one agent to another. Creates an empty message.
* @class
*
* @property {string} msgID - unique message ID
* @property {Performative} perf - performative of the message
* @property {AgentID} [sender] - AgentID of the sender of the message
* @property {AgentID} [recipient] - AgentID of the recipient of the message
* @property {string} [inReplyTo] - ID of the message to which this message is a response
* @property {number} [sentAt] - timestamp when the message was sent
*/
export class Message {

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
  * @param {Object} jsonObj - Object containing all the properties of the message
  * @returns {Message} - A message created from the Object
  *
  */
  static fromJSON(jsonObj) {
    if (!( 'clazz' in jsonObj) || !( 'data' in jsonObj)) {
      throw new Error(`Invalid Object for Message : ${jsonObj}`);
    }
    let qclazz = jsonObj.clazz;
    let clazz = qclazz.replace(/^.*\./, '');
    let rv = MessageClass[clazz] ? new MessageClass[clazz] : new Message();
    rv.__clazz__ = qclazz;
    // copy all properties from the data object
    for (var key in jsonObj.data){
      if (key === 'sender' || key === 'recipient') {
        if (jsonObj.data[key] && typeof jsonObj.data[key] === 'string') {
          rv[key] = AgentID.fromJSON(jsonObj.data[key]);
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
* @param {string} name - fully qualified name of the message class to be created
* @param {typeof Message} [parent] - class of the parent MessageClass to inherit from
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
    * @param {{ [x: string]: any; }} params
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
  return cls;
}

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
* @typedef {Message} ParameterReq
* @property {string} param - parameters name to be get/set if only a single parameter is to be get/set
* @property {Object} value - parameters value to be set if only a single parameter is to be set
* @property {Array<ParameterReq.Entry>} requests - a list of multiple parameters to be get/set
* @property {number} [index=-1] - index of parameter(s) to be set*
* @exports ParameterReq
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
* @typedef {Message} ParameterRsp
* @property {string} param - parameters name if only a single parameter value was requested
* @property {Object} value - parameters value if only a single parameter was requested
* @property {Map<string, Object>} values - a map of multiple parameter names and their values if multiple parameters were requested
* @property {Array<boolean>} readonly - a list of booleans indicating if the parameters are read-only
* @property {number} [index=-1] - index of parameter(s) being returned
* @exports ParameterReq
*/
export const ParameterRsp = MessageClass('org.arl.fjage.param.ParameterRsp');

/**
* Request to write contents to a file, or delete a file.
*
* @typedef {Message} PutFileReq
* @property {string} filename - name of the file to be written to or deleted
* @property {number} [content] - content to be written to the file as a byte array. If not provided, the file will be deleted.
* @property {number} [offset=0] - offset in the file to write the content to.
* @exports PutFileReq
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
* @typedef {Message} GetFileReq
* @property {string} filename - name of the file or directory to be read
* @property {number} [offset=0] - offset in the file to read from (ignored for directories)
* @property {number} [length=0] - number of bytes to read from the file (ignored for directories). If 0,
* the entire file will be read.
*  @exports GetFileReq
*/
export const GetFileReq = MessageClass('org.arl.fjage.shell.GetFileReq');

/**
* Response to a {@link GetFileReq}, with the contents of the file or the directory.
*
* @typedef {Message} GetFileRsp
* @property {string} filename - name of the file or directory that was read
* @property {number} [content] - content of the file as a byte array. If the filename represents a directory,
* the content consists of a list of files (one file per line). Each line starts with the filename
* (with a trailing "/" if it is a directory), "\t", file size in bytes, "\t", and last modification date.
* @property {number} [offset=0] - offset in the file that the content corresponds to (ignored for directories)
* @property {boolean} [directory=false] - indicates if the filename represents a directory
* @exports GetFileRsp
*/
export const GetFileRsp = MessageClass('org.arl.fjage.shell.GetFileRsp');

/**
* Request to execute shell command/script.
*
* @typedef {Message} ShellExecReq
* @property {string} command - shell command or script to be executed
* @property {boolean} ans  - indicates if the response to this request should include the output of the command/script execution
* @exports ShellExecReq
*/
export const ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq');