import { Performative } from './performative.js';
import { _guid, _atob } from './utils.js';
import { AgentID } from './agentid.js';  // import AgentID class for type checking. Remove if not needed.

/**
* Base class for messages transmitted by one agent to another. Creates an empty message.
* @class
* @param {Object} inReplyTo
* @param {string} [inReplyTo.msgID] - ID of the message to which this message response corresponds to
* @param {AgentID} [inReplyTo.sender] - AgentID of the sender of the message to which this message response corresponds to
* @param {Performative} [perf=Performative.INFORM] - performative
*/
export class Message {

  constructor(inReplyTo={msgID:null, sender:null}, perf=Performative.INFORM) {
    this.__clazz__ = 'org.arl.fjage.Message';
    this.msgID = _guid(8);
    this.sender = null;
    this.recipient = inReplyTo.sender;
    this.perf = perf;
    this.inReplyTo = inReplyTo.msgID || null;
  }

  /**
  * Gets a string representation of the message.
  *
  * @returns {string} - string representation
  */
  toString() {
    let s = '';
    let suffix = '';
    if (!this.__clazz__) return'';
    let clazz = this.__clazz__;
    clazz = clazz.replace(/^.*\./, '');
    let perf = this.perf;
    for (var k in this) {
      if (k.startsWith('__')) continue;
      if (k == 'sender') continue;
      if (k == 'recipient') continue;
      if (k == 'msgID') continue;
      if (k == 'perf') continue;
      if (k == 'inReplyTo') continue;
      if (typeof this[k] == 'object') {
        suffix = ' ...';
        continue;
      }
      s += ' ' + k + ':' + this[k];
    }
    s += suffix;
    return clazz+':'+perf+'['+s.replace(/^ /, '')+']';
  }

  // convert a message into a JSON string
  // NOTE: we don't do any base64 encoding for TX as
  //       we don't know what data type is intended
  /**
  * @private
  *
  * @return {string} - JSON string representation of the message
  */
  _serialize() {
    let clazz = this.__clazz__ || 'org.arl.fjage.Message';
    let data = JSON.stringify(this, (k,v) => {
      if (k.startsWith('__')) return;
      return v;
    });
    return '{ "clazz": "'+clazz+'", "data": '+data+' }';
  }

  // add all keys from an Object to the message
  /** @private */
  _assign(data) {
    for (var key in data)
      this[key] = data[key];
  }

  // convert a dictionary (usually from decoding JSON) into a message
  /**
  * @private
  *
  * @param {(string|Object)} json - JSON string or object to be converted to a message
  * @returns {Message} - message created from the JSON string or object
  * */
  static _deserialize(json) {
    let obj = null;
    if (typeof json == 'string') {
      try {
        obj = JSON.parse(json);
      }catch(e){
        return null;
      }
    } else obj = json;
    let qclazz = obj.clazz;
    let clazz = qclazz.replace(/^.*\./, '');
    let rv = MessageClass[clazz] ? new MessageClass[clazz] : new Message();
    rv.__clazz__ = qclazz;
    rv._assign(obj.data);
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

// base64 JSON decoder
/**
* @private
*
* @param {string} k - key
* @param {any} d - data
* @returns {Array} - decoded data in array format
* */
function _decodeBase64(k, d) {
  if (d === null) {
    return null;
  }
  if (typeof d == 'object' && 'clazz' in d) {
    let clazz = d.clazz;
    if (clazz.startsWith('[') && clazz.length == 2 && 'data' in d) {
      let x = _b64toArray(d.data, d.clazz);
      if (x) d = x;
    }
  }
  return d;
}

/**
* Parses a string representation of a message into a JavaScript object
* using a custom base64 decoder.
* @private
*
* @param {string} json - JSON string representation of the message
* @returns {Object} - JavaScript object created from the JSON string
*/
export function createJSONMessage(json) {
  return JSON.parse(json, _decodeBase64);
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
* @property {number} [index=-1] - index of parameter(s) to be set
* @exports ParameterReq
*/
export const ParameterReq = MessageClass('org.arl.fjage.param.ParameterReq');

////// private utilities

// convert from base 64 to array
/** @private */
function _b64toArray(base64, dtype, littleEndian=true) {
  let s = _atob(base64);
  let len = s.length;
  let bytes = new Uint8Array(len);
  for (var i = 0; i < len; i++)
    bytes[i] = s.charCodeAt(i);
  let rv = [];
  let view = new DataView(bytes.buffer);
  switch (dtype) {
    case '[B': // byte array
    for (i = 0; i < len; i++)
      rv.push(view.getUint8(i));
    break;
    case '[S': // short array
    for (i = 0; i < len; i+=2)
      rv.push(view.getInt16(i, littleEndian));
    break;
    case '[I': // integer array
    for (i = 0; i < len; i+=4)
      rv.push(view.getInt32(i, littleEndian));
    break;
    case '[J': // long array
    for (i = 0; i < len; i+=8)
      rv.push(view.getBigInt64(i, littleEndian));
    break;
    case '[F': // float array
    for (i = 0; i < len; i+=4)
      rv.push(view.getFloat32(i, littleEndian));
    break;
    case '[D': // double array
    for (i = 0; i < len; i+=8)
      rv.push(view.getFloat64(i, littleEndian));
    break;
    default:
    return;
  }
  return rv;
}
