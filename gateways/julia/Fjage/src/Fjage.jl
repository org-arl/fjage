"""
Julia-fjåge Gateway.

Note: This implementation is not thread-safe.

# Examples

Assuming fjåge master container is running on `localhost` at port 1100:

```julia-repl
julia> using Fjage
julia> ShellExecReq = MessageClass("org.arl.fjage.shell.ShellExecReq");
julia> gw = Gateway("localhost", 1100);
julia> shell = agentforservice(gw, "org.arl.fjage.shell.Services.SHELL")
shell
julia> request(gw, ShellExecReq(recipient=shell, cmd="ps"))
AGREE
julia> request(shell, ShellExecReq(cmd="ps"))
AGREE
julia> shell << ShellExecReq(cmd="ps")
AGREE
julia> close(gw)
```
"""
module Fjage

# install and use dependencies
# using Pkg
# Pkg.add("JSON")

using Sockets, Distributed, Base64, UUIDs, Dates, JSON

# exported symbols
export Performative, AgentID, Gateway, Message, GenericMessage, MessageClass
export agent, topic, send, receive, request, agentforservice, agentsforservice, subscribe, unsubscribe

# package settings
const MAX_QUEUE_LEN = 256

"An action represented by a message."
module Performative
  const REQUEST = "REQUEST"
  const AGREE = "AGREE"
  const REFUSE = "REFUSE"
  const FAILURE = "FAILURE"
  const INFORM = "INFORM"
  const CONFIRM = "CONFIRM"
  const DISCONFIRM = "DISCONFIRM"
  const QUERY_IF = "QUERY_IF"
  const NOT_UNDERSTOOD = "NOT_UNDERSTOOD"
  const CFP = "CFP"
  const PROPOSE = "PROPOSE"
  const CANCEL = "CANCEL"
end

# respond to master container
function _respond(gw, rq::Dict, rsp::Dict)
  s = JSON.json(merge(Dict("id" => rq["id"], "inResponseTo" => rq["action"]), rsp))
  println(gw.sock, s)
end

# ask master container a question, and wait for reply
function _ask(gw, rq::Dict)
  id = string(uuid4())
  s = JSON.json(merge(rq, Dict("id" => id)))
  ch = Channel{Dict}(1)
  gw.pending[id] = ch
  try
    println(gw.sock, s)
    return take!(ch)
  finally
    delete!(gw.pending, id)
  end
end

# update master container about changes to recipient watch list
function _update_watch(gw)
  watch = [gw.agentID.name]
  for s in keys(gw.subscriptions)
    push!(watch, s)
  end
  s = JSON.json(Dict(
    "action" => "wantsMessagesFor",
    "agentIDs" => watch
  ))
  println(gw.sock, s)
end

# task monitoring incoming JSON messages from master container
function _run(gw)
  println(gw.sock, "{\"alive\": true}")
  _update_watch(gw)
  while isopen(gw.sock)
    s = readline(gw.sock)
    json = JSON.parse(s)
    if haskey(json, "id") && haskey(gw.pending, json["id"])
      put!(gw.pending[json["id"]], json)
    elseif haskey(json, "action")
      if json["action"] == "agents"
        _respond(gw, json, Dict("agentIDs" => [gw.agentID.name]))
      elseif json["action"] == "agentForService"
        _respond(gw, json, Dict())
      elseif json["action"] == "agentsForService"
        _respond(gw, json, Dict("agentIDs" => []))
      elseif json["action"] == "services"
        _respond(gw, json, Dict("services" => []))
      elseif json["action"] == "containsAgent"
        ans = (json["agentID"] == gw.agentID.name)
        _respond(gw, json, Dict("answer" => ans))
      elseif json["action"] == "send"
        rcpt = json["message"]["data"]["recipient"]
        if rcpt == gw.agentID.name || get(gw.subscriptions, rcpt, false)
          try
            msg = _inflate(json["message"])
            while length(gw.queue.data) >= MAX_QUEUE_LEN
              take!(gw.queue)
            end
            put!(gw.queue, msg)
          catch ex
            # silently ignore bad messages
          end
        end
      end
    elseif haskey(json, "alive") && json["alive"]
      println(gw.sock, "{\"alive\": true}")
    end
  end
end

"Base class for messages transmitted by one agent to another."
abstract type Message end

"An identifier for an agent or a topic."
struct AgentID
  name::String
  istopic::Bool
  owner
end

"""
    aid = AgentID(name[, istopic])

Create an unowned AgentID.

See also: [`agent`](@ref), [`topic`](@ref)
"""
AgentID(name::String) = name[1] == '#' ? AgentID(name[2:end], true, nothing) : AgentID(name, false, nothing)
AgentID(name::String, istopic::Bool) = AgentID(name, istopic, nothing)
Base.show(io::IO, aid::AgentID) = print(io, aid.istopic ? "#"*aid.name : aid.name)
JSON.lower(aid::AgentID) = aid.istopic ? "#"*aid.name : aid.name

"""
    gw = Gateway([name,] host, port)

Open a new TCP/IP gateway to communicate with fjåge agents from Julia.

See also: [`Fjage`](@ref)
"""
struct Gateway
  agentID::AgentID
  sock::TCPSocket
  subscriptions::Dict{String,Bool}
  pending::Dict{String,Channel}
  queue::Channel
  function Gateway(name::String, host::String, port::Integer)
    gw = new(
      AgentID(name, false),
      connect(host, port),
      Dict{String,Bool}(),
      Dict{String,Channel}(),
      Channel(MAX_QUEUE_LEN)
    )
    @async _run(gw)
    return gw
  end
end

Gateway(host::String, port::Integer) = Gateway("julia-gw-" * string(uuid1()), host, port)
Base.show(io::IO, gw::Gateway) = print(io, gw.agentID.name)

"""
    aid = agent([gw,] name)

Creates an AgentID for a named agent, optionally owned by a gateway. AgentIDs that are
associated with gateways can be used directly in `send()` and `request()` calls.
"""
agent(name::String) = AgentID(name, false)
agent(gw::Gateway, name::String) = AgentID(name, false, gw)

"""
    aid = topic([gw,] name[, subtopic])

Creates an AgentID for a named topic, optionally owned by a gateway. AgentIDs that are
associated with gateways can be used directly in `send()` and `request()` calls.
"""
topic(name::String) = AgentID(name, true)
topic(aid::AgentID) = aid.istopic ? aid : AgentID(aid.name*"__ntf", true)
topic(aid::AgentID, topic2::String) = AgentID(aid.name*"__"*topic2*"__ntf", true)
topic(gw::Gateway, name::String) = AgentID(name, true, gw)
topic(gw::Gateway, aid::AgentID) = aid.istopic ? aid : AgentID(aid.name*"__ntf", true, gw)
topic(gw::Gateway, aid::AgentID, topic2::String) = AgentID(aid.name*"__"*topic2*"__ntf", true, gw)

"Find an agent that provides a named service."
function agentforservice(gw::Gateway, svc::String)
  rq = Dict("action" => "agentForService", "service" => svc)
  rsp = _ask(gw, rq)
  if haskey(rsp, "agentID")
    return AgentID(rsp["agentID"], false, gw)
  else
    return nothing
  end
end

"Find all agents that provides a named service."
function agentsforservice(gw::Gateway, svc::String)
  rq = Dict("action" => "agentsForService", "service" => svc)
  rsp = _ask(gw, rq)
  return [AgentID(a, false, gw) for a in rsp["agentIDs"]]
end

"Subscribe to receive all messages sent to the given topic."
function subscribe(gw::Gateway, aid::AgentID)
  gw.subscriptions[string(topic(gw, aid))] = true
  _update_watch(gw)
  return true
end

"Unsubscribe from receiving messages sent to the given topic."
function unsubscribe(gw::Gateway, aid::AgentID)
  k = string(topic(gw, aid))
  if haskey(gw.subscriptions, k)
    delete!(gw.subscriptions, k)
    _update_watch(gw)
    return true
  end
  return false
end

"Close a gateway connection to the master container."
function close(gw::Gateway)
  println(gw.sock, "{\"alive\": false}")
  Base.close(gw.sock)
end

# create a Message subclass from a qualified classname
macro _define_message(sname::Symbol, clazz, perf)
  quote
    struct $(esc(sname)) <: Message
      clazz::String
      data::Dict{String,Any}
    end
    function $(esc(sname))(; kwargs...)
      dict = Dict{String,Any}(
        "msgID" => string(uuid4()),
        "perf" => string($perf)
      )
      for k in keys(kwargs)
        dict[string(k)] = kwargs[k]
      end
      return $(esc(sname))(string($clazz), dict)
    end
  end
end

"""
    mtype = MessageClass(clazz[, perf])

Create a message class from a fully qualified class name. If a performative is not
specified, it is guessed based on the class name. For class names ending with "Req",
the performative is assumed to be REQUEST, and for all other messages, INFORM.

# Examples

```julia-repl
julia> using Fjage
julia> ShellExecReq = MessageClass("org.arl.fjage.shell.ShellExecReq");
julia> req = ShellExecReq(cmd="ps")
ShellExecReq: REQUEST [cmd:"ps"]
```
"""
function MessageClass(clazz::String, perf=nothing)
  if perf == nothing
    if match(r"Req$", clazz) != nothing
      perf = Performative.REQUEST
    else
      perf = Performative.INFORM
    end
  end
  sname = Symbol(replace(clazz, "." => "_"))
  return @eval @_define_message($sname, $clazz, $perf)
end

# prepares a message to be sent to the server
function _prepare!(gw::Gateway, msg::Message)
  msg.sender = gw.agentID
  for k in keys(msg.__data__)
    v = msg.__data__[k]
    if typeof(v) <: Array && typeof(v).parameters[1] <: Complex
      btype = typeof(v).parameters[1].parameters[1]
      msg.__data__[k] = reinterpret(btype, v)
    end
  end
end

# converts Base64 encoded arrays to Julia arrays
function _b64toarray(v)
  try
    dtype = v["clazz"]
    if dtype == "[B"  # byte array
      dtype = Int8
    elseif dtype == "[S"  # short array
      dtype = Int16
    elseif dtype == "[I"  # integer array
      dtype = Int32
    elseif dtype == "[J"  # long array
      dtype = Int64
    elseif dtype == "[F"  # float array
      dtype = Float32
    elseif dtype == "[D"  # double array
      dtype = Float64
    else
      return v
    end
    return Array{dtype}(reinterpret(dtype, base64decode(v["data"])))
  catch ex
    return v
  end
end

# creates a message object from a JSON representation of the object
function _inflate(json)
  if typeof(json) == String
    json = JSON.parse(json)
  end
  clazz = json["clazz"]
  data = json["data"]
  stype = MessageClass(clazz)
  obj = @eval $stype()
  for k in keys(data)
    v = data[k]
    if endswith(k, "__isComplex")
      continue
    end
    if k == "sender" || k == "recipient"
      v = AgentID(v)
    end
    if typeof(v) <: Dict && haskey(v, "clazz") && match(r"^\[.$", v["clazz"]) != nothing
      v = _b64toarray(v)
    end
    if typeof(v) <: Array && length(v) > 0
      t = typeof(v[1])
      v = Array{t}(v)
      kcplx = k*"__isComplex"
      if haskey(data, kcplx) && data[kcplx]
        v = Array{Complex{t}}(reinterpret(Complex{t}, v))
      end
    end
    obj.__data__[k] = v
  end
  return obj
end

"""
    send(gw, msg)
    send(aid, msg)

Send a message via the gateway to the specified agent. If the gateway (`gw`) is specified then the
`recipient` field of the message must be populated with an agentID. If the agentID (`aid`) is specified,
it must be an "owned" agentID obtained from the `agent(gw, name)` function or returned by the
`agentforservice(gw, service)` function.
"""
function send(gw::Gateway, msg::Message)
  _prepare!(gw, msg)
  json = JSON.json(Dict("action" => "send", "relay" => true, "message" => msg))
  println(gw.sock, json)
end

function send(aid::AgentID, msg::Message)
  if aid.owner == nothing
    error("cannot send message to an unowned agentID")
  end
  msg.recipient = aid
  send(aid.owner, msg)
end

# helper function to see if a message matches a filter
function _matches(filt, msg)
  if msg == nothing
    return true
  end
  if typeof(filt) == DataType
    return typeof(msg) <: filt
  elseif typeof(filt) <: Message
    return msg.inReplyTo == filt.msgID
  elseif typeof(filt) <: Function
    return filt(msg)
  end
  return false
end

"""
    msg = receive(gw[, filter][, timeout])

Receive an incoming message from other agents or topics. Timeout is specified in
milliseconds. If no timeout is specified, the call is non-blocking. If a negative timeout
is specified, the call is blocking until a message is available.

If a `filter` is specified, only messages matching the filter are retrieved. A filter
may be a message type, a message or a function. If it is a message type, only messages
of that type or a subtype are retrieved. If it is a message, any message whose `inReplyTo`
field is set to the `msgID` of the specified message is retrieved. If it is a function,
it must take in a message and return `true` or `false`. A message for which it returns
`true` is retrieved.
"""
function receive(gw::Gateway, timeout::Integer=0)
  if isready(gw.queue)
    return take!(gw.queue)
  end
  if timeout == 0
    return nothing
  end
  waiting = true
  if timeout > 0
    @async begin
      timer = Timer(timeout/1000.0)
      wait(timer)
      if waiting
        push!(gw.queue, nothing)
      end
    end
  end
  rv = take!(gw.queue)
  waiting = false
  return rv
end

function receive(gw::Gateway, filt, timeout::Integer=0)
  t1 = now() + Millisecond(timeout)
  cache = []
  while true
    msg = receive(gw, (t1-now()).value)
    if _matches(filt, msg)
      if length(cache) > 0
        while isready(gw.queue)
          push!(cache, take!(gw.queue))
        end
        for m in cache
          push!(gw.queue, m)
        end
      end
      return msg
    end
    push!(cache, msg)
  end
end

"""
    rsp = request(gw, msg[, timeout])
    rsp = request(aid, msg[, timeout])

Send a request via the gateway to the specified agent, and wait for a response. The response is returned.
If the gateway (`gw`) is specified then the `recipient` field of the request message (`msg`) must be
populated with an agentID. If the agentID (`aid`) is specified, it must be an "owned" agentID obtained
from the `agent(gw, name)` function or returned by the `agentforservice(gw, service)` function. The timeout
is specified in milliseconds, and defaults to 1 second if unspecified.
"""
function request(gw::Gateway, msg::Message, timeout::Integer=1000)
  send(gw, msg)
  return receive(gw, msg, timeout)
end

function request(aid::AgentID, msg::Message, timeout::Integer=1000)
  send(aid, msg)
  return receive(aid.owner, msg, timeout)
end

"""
    rsp = aid << msg

Send a request via the gateway to the specified agent, and wait for a response.

See also: [`request`](@ref), [`Fjage`](@ref)
"""
Base.:<<(aid::AgentID, msg::Message) = request(aid, msg)

"Flush the incoming message queue."
function flush(gw::Gateway)
  while isready(gw.queue)
    take!(gw.queue)
  end
end

# adds notation message.field
function Base.getproperty(s::Message, p::Symbol)
  if p == :__clazz__
    return getfield(s, :clazz)
  elseif p == :__data__
    return getfield(s, :data)
  else
    p1 = string(p)
    if p1 == "performative"
      p1 = "perf"
    elseif p1 == "messageID"
      p1 = "msgID"
    end
    v = getfield(s, :data)
    if !haskey(v, p1)
      return nothing
    end
    v = v[p1]
    return v
  end
end

# adds notation message.field
function Base.setproperty!(s::Message, p::Symbol, v)
  if p == :__clazz__ || p == :__data__
    error("read-only property cannot be set")
  else
    p1 = string(p)
    if p1 == "performative"
      p1 = "perf"
    elseif p1 == "messageID"
      p1 = "msgID"
    end
    getfield(s, :data)[p1] = v
  end
end

# pretty prints arrays without type names
function _repr(x)
  x = repr(x)
  m = match(r"[A-Za-z0-9]+(\[.+\])", x)
  if m != nothing
    x = m[1]
  end
  return x
end

# pretty printing of messages
function Base.show(io::IO, msg::Message)
  ndx = findlast(".", msg.__clazz__)
  s = ndx == nothing ? msg.__clazz__ : msg.__clazz__[ndx[1]+1:end]
  p = ""
  data_suffix = ""
  signal_suffix = ""
  suffix = ""
  data = msg.__data__
  for k in keys(data)
    x = data[k]
    if k == "perf"
      s *= ": " * x
    elseif k == "data"
      if typeof(x) <: Array
        data_suffix *= "($(length(x)) bytes)"
      else
        p *= " $k:" * _repr(data[k])
      end
    elseif k == "signal"
      if typeof(x) <: Array
        signal_suffix *= "($(length(x)) samples)"
      else
        p *= " $k:" * _repr(data[k])
      end
    elseif k != "sender" && k != "recipient" && k != "msgID" && k != "inReplyTo"
      if typeof(x) <: Number || typeof(x) == String || typeof(x) <: Array || typeof(x) == Bool
        p *= " $k:" * _repr(x)
      else
        suffix = "..."
      end
    end
  end
  if length(suffix) > 0
    p *= " " * suffix
  end
  if length(signal_suffix) > 0
    p *= " " * signal_suffix
  end
  if length(data_suffix) > 0
    p *= " " * data_suffix
  end
  p = strip(p)
  if length(p) > 0
    s *= " [$p]"
  end
  if msg.__clazz__ == "org.arl.fjage.Message"
    m = match(r"^Message: (.*)$", s)
    if m != nothing
      s = m[1]
    end
  end
  print(io, s)
end

"Generic message type that can carry arbitrary name-value pairs as data."
GenericMessage = MessageClass("org.arl.fjage.GenericMessage", Performative.INFORM)

"""
    msg = Message([perf])
    msg = Message(inreplyto[, perf])

Create a message with just a performative (`perf`) and no data. If the performative
is not specified, it defaults to INFORM. If the inreplyto is specified, the message
`inReplyTo` and `recipient` fields are set accordingly.
"""
Message(perf::String=Performative.INFORM) = GenericMessage(performative=perf)
Message(inreplyto::Message, perf::String=Performative.INFORM) = GenericMessage(performative=perf, inReplyTo=inreplyto.msgID, recipient=inreplyto.sender)

# Base functions to add local methods
Base.close(gw::Gateway) = close(gw)
Base.flush(gw::Gateway) = flush(gw)

end
