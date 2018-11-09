////// settings

const TIMEOUT = 5000;              // ms, timeout to get response from to master container

////// private utilities

// generate random ID with length 4*len characters
function _guid(len) {
  function s4() {
    return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
  }
  let s = s4();
  for (var i = 0; i < len-1; i++)
    s += s4();
  return s;
}

// convert from base 64 to array
function _b64toArray(base64, dtype, littleEndian=true) {
  let s =  window.atob(base64);
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
        rv.push(view.getInt64(i, littleEndian));
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

// base 64 JSON decoder
function _decodeBase64(k, d) {
  if (typeof d == 'object' && 'clazz' in d) {
    let clazz = d.clazz;
    if (clazz.startsWith('[') && clazz.length == 2 && 'data' in d) {
      let x = _b64toArray(d.data, d.clazz);
      if (x) d = x;
    }
  }
  return d;
}

////// interface classes

export const Performative = {
  REQUEST: 'REQUEST',               // Request an action to be performed
  AGREE: 'AGREE',                   // Agree to performing the requested action
  REFUSE: 'REFUSE',                 // Refuse to perform the requested action
  FAILURE: 'FAILURE',               // Notification of failure to perform a requested or agreed action
  INFORM: 'INFORM',                 // Notification of an event
  CONFIRM: 'CONFIRM',               // Confirm that the answer to a query is true
  DISCONFIRM: 'DISCONFIRM',         // Confirm that the answer to a query is false
  QUERY_IF: 'QUERY_IF',             // Query if some statement is true or false
  NOT_UNDERSTOOD: 'NOT_UNDERSTOOD', // Notification that a message was not understood
  CFP: 'CFP',                       // Call for proposal
  PROPOSE: 'PROPOSE',               // Response for CFP
  CANCEL: 'CANCEL'                  // Cancel pending request
};

export class AgentID {

  constructor(name, topic, gw) {
    this.name = name;
    this.topic = topic;
    this.gw = gw;
  }

  getName() {
    return this.name;
  }

  isTopic() {
    return this.topic;
  }

  send(msg) {
    msg.recipient = this;
    this.gw.send(msg);
  }

  // returns a Promise
  request(msg, timeout=1000) {
    msg.recipient = this;
    return this.gw.request(msg, timeout);
  }

  toString() {
    if (this.topic) return '#'+this.name;
    return this.name;
  }

  toJSON() {
    return this.toString();
  }

}

export class Message {

  constructor() {
    this.__clazz__ = 'org.arl.fjage.Message';
    this.msgID = _guid(8);
    this.sender = '';
    this.recipient = '';
    this.perf = '';
  }

  toString() {
    let s = '';
    let suffix = '';
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
  _serialize() {
    let clazz = this.__clazz__;
    let data = JSON.stringify(this, (k,v) => {
      if (k.startsWith('__')) return;
      return v;
    });
    return '{ "clazz": "'+clazz+'", "data": '+data+' }';
  }

  // inflate a data dictionary into the message
  _inflate(data) {
    for (var key in data)
      this[key] = data[key];
  }

  // convert a dictionary (usually from decoding JSON) into a message
  static _deserialize(obj) {
    if (typeof obj == 'string' || obj instanceof String) obj = JSON.parse(obj);
    let qclazz = obj.clazz;
    let clazz = qclazz.replace(/^.*\./, '');
    let rv = eval('try { new '+clazz+'() } catch(ex) { new Message() }');
    rv.__clazz__ = qclazz;
    rv._inflate(obj.data);
    return rv;
  }

}

export class GenericMessage extends Message {
  constructor() {
    super();
    this.__clazz__ = 'org.arl.fjage.GenericMessage';
  }
}

export class Gateway {

  // connect back to the master container over a websocket to the server
  constructor() {
    this.pending = {};                    // msgid to callback mapping for pending requests to server
    this.pendingOnOpen = [];              // list of callbacks make as soon as gateway is open
    this.aid = 'WebGW-'+_guid(4);         // gateway agent name
    this.subscriptions = {};              // hashset for all topics that are subscribed
    this.listener = {};                   // set of callbacks that want to listen to incoming messages
    this.observers = [];                  // external observers wanting to listen incoming messages
    this.queue = [];                      // incoming message queue
    this.debug = false;                   // debug info to be logged to console?
    this.sock = new WebSocket('ws://'+window.location.hostname+':'+window.location.port+'/ws/');
    this.sock.onopen = this._onWebsockOpen.bind(this);
    this.sock.onmessage = event => {
      this._onWebsockRx.call(this,event.data);
    };
    if (typeof window.fjage === 'undefined') {
      window.fjage = {};
      window.fjage.gateways = [];
      window.fjage.getGateway = function (url){
        var f = window.fjage.gateways.filter(g => g.sock.url == url)
        if (f.length ) return f[0];
      }
    }
    window.fjage.gateways.push(this);
  }

  _onWebsockOpen() {
    this.sock.send("{'alive': true}\n");
    this.pendingOnOpen.forEach(cb => cb());
    this.pendingOnOpen.length = 0;
  }

  _onWebsockRx(data) {
    let obj = JSON.parse(data, _decodeBase64);
    if (this.debug) console.log('< '+data);
    if ('id' in obj && obj.id in this.pending) {
      // response to a pending request to master
      this.pending[obj.id](obj);
      delete this.pending[obj.id];
    } else if (obj.action == 'send') {
      // incoming message from master
      let msg = Message._deserialize(obj.message);
      if (msg.recipient == this.aid || this.subscriptions[msg.recipient]) {
        for (var i = 0; i < this.observers.length; i++)
          if (this.observers[i](msg)) return;
        this.queue.push(msg);
        for (var key in this.listener)        // iterate over internal callbacks, until one consumes the message
          if (this.listener[key]()) break;    // callback returns true if it has consumed the message
      }
    } else {
      // respond to standard requests that every container must
      let rsp = { id: obj.id, inResponseTo: obj.action };
      switch (obj.action) {
        case 'agents':
          rsp.agentIDs = [this.aid];
          break;
        case 'containsAgent':
          rsp.answer = (obj.agentID == this.aid);
          break;
        case 'services':
          rsp.services = [];
          break;
        case 'agentForService':
          rsp.agentID = '';
          break;
        case 'agentsForService':
          rsp.agentIDs = [];
          break;
        default:
          rsp = undefined;
      }
      if (rsp) this._websockTx(rsp);
    }
  }

  _websockTx(s) {
    let sock = this.sock;
    if (typeof s != 'string' && !(s instanceof String)) s = JSON.stringify(s);
    if (sock.readyState == sock.OPEN) {
      if (this.debug) console.log('> '+s);
      sock.send(s+'\n');
      return true;
    } else if (sock.readyState == sock.CONNECTING) {
      this.pendingOnOpen.push(() => {
        if (this.debug) console.log('> '+s);
        sock.send(s+'\n');
      });
      return true;
    }
    return false;
  }

  // returns a Promise
  _websockTxRx(rq) {
    rq.id = _guid(8);
    return new Promise((resolve, reject) => {
      let timer = setTimeout(() => {
        delete this.pending[rq.id];
        reject();
      }, TIMEOUT);
      this.pending[rq.id] = rsp => {
        clearTimeout(timer);
        resolve(rsp);
      };
      if (!this._websockTx.call(this,rq)) {
        clearTimeout(timer);
        delete this.pending[rq.id];
        reject();
      }
    });
  }

  _getMessageFromQueue(filter) {
    if (!this.queue.length) return;
    if (!filter) return this.queue.shift();

    var filtMsgs = this.queue.filter( msg => {
      if (typeof filter == 'string' || filter instanceof String) {
        return 'inReplyTo' in msg && msg.inReplyTo == filter;
      }else{
        return msg instanceof filter;
      }
    });

    if (filtMsgs){
      this.queue.splice(this.queue.indexOf(filtMsgs[0]), 1);
      return filtMsgs[0];
    }
  }

  // creates a unqualified message class based on a fully qualified name
  import(name) {
    let sname = name.replace(/^.*\./, '');
    window[sname] = class extends Message {
      constructor() {
        super();
        this.__clazz__ = name;
      }
    };
  }

  addMessageListener(listener) {
    this.observers.push(listener);
  }

  removeMessageListener(listener) {
    let ndx = this.observers.indexOf(listener);
    if (ndx >= 0) this.observers.splice(ndx, 1);
  }

  getAgentID() {
    return this.aid;
  }

  agent(name) {
    return new AgentID(name, false, this);
  }

  topic(topic, topic2) {
    if (typeof topic == 'string' || topic instanceof String) return new AgentID(topic, true, this);
    if (topic instanceof AgentID) {
      if (topic.isTopic()) return topic;
      return new AgentID(topic.getName()+(topic2 ? topic2 + '__' : '')+'__ntf', true, this);
    }
  }

  subscribe(topic) {
    if (!topic.isTopic()) topic = new AgentID(topic, true, this);
    this.subscriptions[topic.toString()] = true;
  }

  unsubscribe(topic) {
    if (!topic.isTopic()) topic = new AgentID(topic, true, this);
    delete this.subscriptions[topic.toString()];
  }

  // returns a Promise
  async agentForService(service) {
    let rq = { action: 'agentForService', service: service };
    let rsp = await this._websockTxRx(rq);
    return new AgentID(rsp.agentID, false, this);
  }

  // returns a Promise
  async agentsForService(service) {
    let rq = { action: 'agentsForService', service: service };
    let rsp = await this._websockTxRx(rq);
    let aids = [];
    for (var i = 0; i < rsp.agentIDs.length; i++)
      aids.push(new AgentID(rsp.agentIDs[i], false, this));
    return aids;
  }

  send(msg, relay=true) {
    msg.sender = this.aid;
    if (msg.perf == '') {
      if (msg.__clazz__.endsWith('Req')) msg.perf = Performative.REQUEST;
      else msg.perf = Performative.INFORM;
    }
    let rq = JSON.stringify({ action: 'send', relay: relay, message: '###MSG###' });
    rq = rq.replace('"###MSG###"', msg._serialize());
    this._websockTx(rq);
  }

  flush() {
    this.queue.length = 0;
  }

  // returns a Promise
  async request(msg, timeout=10000) {
    this.send(msg);
    let rsp = await this.receive(msg.msgID, timeout);
    return rsp;
  }

  // returns a Promise
  receive(filter=undefined, timeout=1000) {
    return new Promise((resolve, reject) => {
      let msg = this._getMessageFromQueue.call(this,filter);
      if (msg) {
        resolve(msg);
        return;
      }
      let lid = _guid(8);
      let timer = setTimeout(() => {
        delete this.listener[lid];
        reject();
      }, timeout);
      this.listener[lid] = () => {
        msg = this._getMessageFromQueue.call(this,filter);
        if (!msg) return false;
        clearTimeout(timer);
        delete this.listener[lid];
        resolve(msg);
        return true;
      };
    });
  }

  close() {
    if (this.sock.readyState == this.sock.CONNECTING) {
      this.pendingOnOpen.push(() => {
        this.sock.send("{'alive': false}\n");
        this.sock.close();
      });
      return true;
    } else if (this.sock.readyState == this.sock.OPEN) {
      this.sock.send("{'alive': false}\n");
      this.sock.close();
      return true;
    }
    return false;
  }

  shutdown() {
    this.close();
  }
}
