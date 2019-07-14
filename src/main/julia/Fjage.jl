module Fjage

# NOTES:
#   - not threadsafe

using Sockets, Distributed, JSON, Base64, UUIDs, Dates

export Performative, AgentID, Gateway, Message, GenericMessage, MessageClass
export agent, topic, send, receive, request, agentforservice, agentsforservice, subscribe, unsubscribe

const MAX_QUEUE_LEN = 256

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

function _respond(gw, rq::Dict, rsp::Dict)
  s = JSON.json(merge(Dict("id" => rq["id"], "inResponseTo" => rq["action"]), rsp))
  println(gw.sock, s)
end

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

abstract type Message end

struct AgentID
  name::String
  istopic::Bool
  owner
end

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

AgentID(name::String) = name[1] == '#' ? AgentID(name[2:end], true, nothing) : AgentID(name, false, nothing)
AgentID(name::String, istopic::Bool) = AgentID(name, istopic, nothing)
AgentID(name::String, owner::Gateway) = name[1] == '#' ? AgentID(name[2:end], true, owner) : AgentID(name, false, owner)
Base.show(io::IO, aid::AgentID) = print(io, aid.istopic ? "#"*aid.name : aid.name)
JSON.lower(aid::AgentID) = aid.istopic ? "#"*aid.name : aid.name

Gateway(host::String, port::Integer) = Gateway("julia-gw-" * string(uuid1()), host, port)
Base.show(io::IO, gw::Gateway) = print(io, gw.agentID.name)

agent(name::String) = AgentID(name, false)
topic(name::String) = AgentID(name, true)
topic(aid::AgentID) = aid.istopic ? aid : AgentID(aid.name*"__ntf", true)
topic(aid::AgentID, topic2::String) = AgentID(aid.name*"__"*topic2*"__ntf", true)

agent(gw::Gateway, name::String) = AgentID(name, false, gw)
topic(gw::Gateway, name::String) = AgentID(name, true, gw)
topic(gw::Gateway, aid::AgentID) = aid.istopic ? aid : AgentID(aid.name*"__ntf", true, gw)
topic(gw::Gateway, aid::AgentID, topic2::String) = AgentID(aid.name*"__"*topic2*"__ntf", true, gw)

function agentforservice(gw::Gateway, svc::String)
  rq = Dict("action" => "agentForService", "service" => svc)
  rsp = _ask(gw, rq)
  if haskey(rsp, "agentID")
    return AgentID(rsp["agentID"], false, gw)
  else
    return nothing
  end
end

function agentsforservice(gw::Gateway, svc::String)
  rq = Dict("action" => "agentsForService", "service" => svc)
  rsp = _ask(gw, rq)
  return [AgentID(a, false, gw) for a in rsp["agentIDs"]]
end

function subscribe(gw::Gateway, aid::AgentID)
  gw.subscriptions[string(topic(gw, aid))] = true
  _update_watch(gw)
end

function unsubscribe(gw::Gateway, aid::AgentID)
  delete!(gw.subscriptions, string(topic(gw, aid)))
  _update_watch(gw)
end

function close(gw::Gateway)
  println(gw.sock, "{\"alive\": false}")
  Base.close(gw.sock)
end

Base.close(gw::Gateway) = close(gw)
Base.flush(gw::Gateway) = flush(gw)

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

function MessageClass(clazz::String, perf=Performative.INFORM)
  sname = Symbol(replace(clazz, "." => "_"))
  return @eval @_define_message($sname, $clazz, $perf)
end

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
    if k == "sender" || k == "recipient"
      v = AgentID(v)
    end
    if typeof(v) <: Dict && haskey(v, "clazz") && match(r"^\[.$", v["clazz"]) != nothing
      v = _b64toarray(v)
    end
    if typeof(v) <: Array && length(v) > 0
      t = typeof(v[1])
      v = Array{t}(v)
      if k == "signal" && haskey(data, "fc") && data["fc"] == 0
        v = Array{Complex{t}}(reinterpret(Complex{t}, v))
      end
    end
    obj.__data__[k] = v
  end
  return obj
end

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

function receive(gw::Gateway)
  if isready(gw.queue)
    return take!(gw.queue)
  end
end

function receive(gw::Gateway, timeout::Integer)
  if isready(gw.queue)
    return take!(gw.queue)
  end
  if timeout <= 0
    return nothing
  end
  waiting = true
  @async begin
    timer = Timer(timeout/1000.0)
    wait(timer)
    if waiting
      push!(gw.queue, nothing)
    end
  end
  rv = take!(gw.queue)
  waiting = false
  return rv
end

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

function receive(gw::Gateway, filt, timeout::Integer)
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

function request(gw::Gateway, msg::Message, timeout::Integer=1000)
  send(gw, msg)
  return receive(gw, msg, timeout)
end

function request(aid::AgentID, msg::Message, timeout::Integer=1000)
  send(aid, msg)
  return receive(aid.owner, msg, timeout)
end

function flush(gw::Gateway)
  while isready(gw.queue)
    take!(gw.queue)
  end
end

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

function _repr(x)
  x = repr(x)
  m = match(r"[A-Za-z0-9]+(\[.+\])", x)
  if m != nothing
    x = m[1]
  end
  return x
end

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
        if haskey(data, "fc") && data["fc"] == 0
          signal_suffix *= "($(length(x)) baseband samples)"
        else
          signal_suffix *= "($(length(x)) samples)"
        end
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
  print(io, s)
end

GenericMessage = MessageClass("org.arl.fjage.GenericMessage", Performative.INFORM)

end
