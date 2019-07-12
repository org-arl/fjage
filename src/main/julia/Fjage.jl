module Fjage

# NOTES:
#   - not threadsafe

using Sockets, Distributed, JSON, Base64, UUIDs

export Performative, AgentID, Gateway
export agent, topic, send, receive, request, flush, agentForService, agentsForService, subscribe, unsubscribe, close

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
          # TODO implement
        end
      end
    elseif haskey(json, "alive") && json["alive"]
      println(gw.sock, "{\"alive\": true}")
    end
  end
end

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
  function Gateway(name::String, host::String, port::Integer)
    gw = new(
      AgentID(name, false),
      connect(host, port),
      Dict{String,Bool}(),
      Dict{String,Channel}()
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

agent(gw::Gateway, name::String) = AgentID(name, false, gw)
topic(gw::Gateway, name::String) = AgentID(name, true, gw)
topic(gw::Gateway, aid::AgentID) = aid.istopic ? aid : AgentID(aid.name*"__ntf", true, gw)
topic(gw::Gateway, aid::AgentID, topic2::String) = AgentID(aid.name*"__"*topic2*"__ntf", true, gw)

function send(gw::Gateway, msg)

end

function receive(gw::Gateway)

end

function receive(gw::Gateway, timeout)

end

function receive(gw::Gateway, filter, timeout)

end

function request(gw::Gateway, msg, timeout)

end

function flush(gw::Gateway)

end

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

# abstract type AbstractMessage end

# mutable struct Message <: AbstractMessage
#   __clazz__::String
#   msgID::String
#   sender::AgentID
#   recipient::AgentID
#   perf::String
# end

# Message(clazz) = Message(clazz, "boo", AgentID("boo"), AgentID("boo"), Performative.INFORM)
# MessageClass(clazz) = Message(clazz)

end
