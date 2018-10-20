////// constants

const TIMEOUT = 5000;              // ms, timeout to get response from to master container
const DEBUG = true;                // if set to true, prints debug info on Javascript console

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

////// interface classes

export const Performative = {
  REQUEST: "REQUEST",               // Request an action to be performed
  AGREE: "AGREE",                   // Agree to performing the requested action
  REFUSE: "REFUSE",                 // Refuse to perform the requested action
  FAILURE: "FAILURE",               // Notification of failure to perform a requested or agreed action
  INFORM: "INFORM",                 // Notification of an event
  CONFIRM: "CONFIRM",               // Confirm that the answer to a query is true
  DISCONFIRM: "DISCONFIRM",         // Confirm that the answer to a query is false
  QUERY_IF: "QUERY_IF",             // Query if some statement is true or false
  NOT_UNDERSTOOD: "NOT_UNDERSTOOD", // Notification that a message was not understood
  CFP: "CFP",                       // Call for proposal
  PROPOSE: "PROPOSE",               // Response for CFP
  CANCEL: "CANCEL"                  // Cancel pending request
}

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

  toString() {
    if (this.topic) return "#"+this.name;
    return this.name;
  }

  toJSON() {
    return this.toString();
  }

  // TODO

}

export class Message {

  constructor() {
    this.__clazz__ = "org.arl.fjage.Message";
    this.msgID = _guid(8);
  }

  // TODO: support for base64 arrays

  // convert a message into a JSON string
  serialize() {
    let clazz = this.__clazz__;
    let data = JSON.stringify(this, (k,v) => {
      if (k.startsWith("__")) return undefined;
      return v;
    });
    return '{ "clazz": "'+clazz+'", "data": '+data+' }';
  }

  // convert a dictionary (usually from decoding JSON) into a message
  static deserialize(obj) {
    if (typeof obj == 'string' || obj instanceof String) obj = JSON.parse(obj);
    let clazz = obj.clazz;
    clazz = clazz.replace(/^.*\./, "");
    let rv = eval("new "+clazz+"()");
    for (var key in obj.data)
      rv[key] = obj.data[key];
    return rv;
  }

}

export class GenericMessage extends Message {
  // TODO
}

export class Gateway {

  // connect back to the master container over a websocket to the server
  constructor() {
    this.pending = {};                    // msgid to callback mapping for pending requests to server
    this.aid = "WebGW-"+_guid(4);         // gateway agent name
    this.subscriptions = {};              // hashset for all topics that are subscribed
    this.listener = {};                   // set of all callbacks that want to listen to incoming messages
    this.queue = [];                      // incoming message queue
    let self = this;
    this.sock = new WebSocket("ws://"+window.location.hostname+":"+window.location.port+"/ws/");
    this.sock.onopen = (event) => {
      self._onWebsockOpen();
    };
    this.sock.onmessage = (event) => {
      self._onWebsockRx(event.data);
    }
  }

  _onWebsockOpen() {
    this.sock.send("{'alive': true}\n");
    if ("onOpen" in this.pending) {       // "onOpen" is a special msgid for a callback
      this.pending.onOpen();              //   when websock connection is first opened
      delete this.pending.onOpen;
    }
  }

  _onWebsockRx(data) {
    let obj = JSON.parse(data);
    if (DEBUG) console.log("< "+data);
    if ("id" in obj && obj.id in this.pending) {
      // response to a pending request to master
      this.pending[id](obj);
      delete this.pending[id];
    } else if (obj.action == "send") {
      // incoming message from master
      let msg = Message.deserialize(obj.message);
      if (msg.recipient == this.aid || this.subscriptions[msg.recipient]) {
        this.queue.push(msg);
        for (var key in this.listener)        // iterate over all callbacks, until one consumes the message
          if (this.listener[key]()) break;    // callback returns true if it has consumed the message
      }
    } else {
      // respond to standard requests that every container must
      let rsp = { id: obj.id, inResponseTo: obj.action };
      switch (obj.action) {
        case "agents":
          rsp.agentIDs = [this.aid];
          break;
        case "containsAgent":
          rsp.answer = (obj.agentID == this.aid);
          break;
        case "services":
          rsp.services = [];
          break;
        case "agentForService":
          rsp.agentID = "";
          break;
        case "agentsForService":
          rsp.agentIDs = [];
          break;
        default:
          rsp = undefined;
      }
      if (rsp != undefined) this._websockTx(JSON.stringify(rsp));
    }
  }

  _websockTx(s) {
    let sock = this.sock;
    if (sock.readyState == sock.OPEN) {
      if (DEBUG) console.log("> "+s);
      sock.send(s+"\n");
      return true;
    } else if (sock.readyState == sock.CONNECTING) {
      this.pending.onOpen = () => {
        if (DEBUG) console.log("> "+s);
        sock.send(s+"\n");
      };
      return true;
    }
    return false;
  }

  // returns a Promise
  _websockTxRx(rq) {
    rq.id = _guid(8);
    let self = this;
    return new Promise((resolve, reject) => {
      let timer = setTimeout(() => {
        delete self.pending[rq.id];
        reject();
      }, TIMEOUT);
      self.pending[rq.id] = (rsp) => {
        clearTimeout(timer);
        resolve(rsp);
      };
      if (!self._websockTx(JSON.stringify(rq))) {
        clearTimeout(timer);
        delete self.pending[rq.id];
        reject();
      }
    });
  }

  _getMessageFromQueue(filter) {
    if (filter == undefined) {
      if (this.queue.length == 0) return undefined;
      return this.queue.shift();
    }
    if (typeof filter == 'string' || filter instanceof String) {
      for (var i = 0; i < this.queue.length; i++) {
        let msg = this.queue[i];
        if ("inReplyTo" in msg && msg.inReplyTo == filter) {
          delete this.queue[i];
          return msg;
        }
      }
    }
    for (var i = 0; i < this.queue.length; i++) {
      let msg = this.queue[i];
      if (msg instanceof filter) {
        delete this.queue[i];
        return msg;
      }
    }
    return undefined;
  }

  // creates a unqualified message class based on a fully qualified name
  import(name) {
    let sname = name.replace(/^.*\./, "");
    window[sname] = class extends Message {
      constructor() {
        super();
        this.__clazz__ = name;
      }
    };
  }

  getAgentID() {
    return this.aid;
  }

  agent(name) {
    return new AgentID(name, false, this);
  }

  topic(topic, topic2) {
    if (typeof topic == 'string' || topic instanceof String) return new AgentID(topic, true, this);
    if (topic2 == undefined) {
      if (topic instanceof AgentID) {
        if (topic.isTopic()) return topic;
        return new AgentID(topic.getName()+"__ntf", true, this);
      }
    } else {
      return new AgentID(topic.getName()+"__"+topic2+"__ntf", true, this)
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
    let s = '{ "action": "send", "relay": '+relay+', "message": '+msg.serialize()+' }';
    this._websockTx(s);
  }

  // returns a Promise
  async request(msg, timeout=10000) {
    this.send(msg);
    let rsp = await this.receive(msg.msgID, timeout);
    return rsp;
  }

  // returns a Promise
  receive(filter=undefined, timeout=1000) {
    let queue = this.queue;
    let listener = this.listener;
    let self = this;
    return new Promise((resolve, reject) => {
      let msg = self._getMessageFromQueue(filter);
      if (msg != undefined) {
        resolve(msg);
        return;
      }
      let lid = _guid(8);
      let timer = setTimeout(() => {
        delete listener[lid];
        reject();
      }, timeout);
      listener[lid] = () => {
        msg = self._getMessageFromQueue(filter);
        if (msg == undefined) return false;
        clearTimeout(timer);
        delete listener[lid];
        resolve(msg);
        return true;
      };
    });
  }

  close() {
    if (sock.readyState == sock.CONNECTING) {
      this.pending.onOpen = () => {
        this.sock.send("{'alive': false}\n");
        this.sock.close();
      };
      return true;
    } else if (sock.readyState == sock.OPEN) {
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
