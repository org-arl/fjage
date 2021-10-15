/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage

import org.arl.fjage.param.*

/**
 * Groovy agent extensions for fjage classes.  This provides Groovy
 * agents with neater syntax.  To enable:
 * <pre>
 * GroovyExtensions.enable();
 * </pre>
 */
class GroovyExtensions {
  static void enable() {

    AgentID.metaClass.leftShift = { Message msg ->
      request(msg)
    }

    Agent.metaClass.receive = { Closure filter, long timeout = 1000 ->
      MessageFilter f = new MessageFilter() {
        boolean matches(Message m) {
          return filter(m)
        }
      }
      receive(f, timeout)
    }

    AgentID.metaClass.setProperty = { String name, value ->
      if (owner == null) throw new FjageException('Parameters not suported on unowned AgentID')
      Parameter p = new NamedParameter(name)
      ParameterReq req = new ParameterReq().set(p, value)
      Message rsp = request(req, 2000)
      if (rsp == null) throw new FjageException("Parameter ${name} could not be set [no response]")
      if (rsp instanceof ParameterRsp) {
        def p1 = rsp.parameters()
        if (p1.size() == 0) throw new FjageException("Parameter ${name} could not be set [empty response]")
        def v = rsp.get(p1.first())
        if (v == null) throw new FjageException("Parameter ${name} could not be set [no such parameter]")
        if (v instanceof Number && value instanceof Number) {
          def dv = Math.abs(value-v)
          if (dv >= 1e-6) throw new FjageException("WARNING: Parameter ${name} set to ${v}")
        } else if (v != value) throw new FjageException("WARNING: Parameter ${name} set to ${v}")
      } else {
        throw new FjageException('Parameters not supported by agent')
      }
    }

    AgentID.metaClass.propertyMissing = { String name ->
      if (delegate.owner == null) throw new FjageException('Parameters not suported on unowned AgentID')
      Parameter p = new NamedParameter(name)
      ParameterReq req = new ParameterReq().get(p)
      Message rsp = request(req, 2000)
      if (rsp == null) return null
      if (rsp instanceof ParameterRsp) {
        def p1 = rsp.parameters()
        if (p1.size() == 0) return null
        return rsp.get(p1.first())
      } else {
        throw new FjageException('Parameters not supported by agent')
      }
    }

    AgentID.metaClass.getAllParameters = { ndx = -1 ->
      if (delegate.owner == null) throw new FjageException('Parameters not suported on unowned AgentID')
      ParameterReq req = new ParameterReq()
      if (ndx >= 0) req.index = ndx
      ParameterRsp rsp = null
      try {
        rsp = request(req, 10000)
        if (rsp == null) return null
      } catch (ex) {
        return null
      }
      List<String> out = new ArrayList<String>()
      rsp.parameters().each {
        if (it instanceof NamedParameter) {
          if (it.name != 'title' && it.name != 'description') {
            String s = "${rsp.get(it)}"
            String p = "$it"
            if (!p.contains('.')) p = ".$p"
            boolean ro = rsp.isReadonly(it)
            out.add "${p} ${ro?'⤇':'='} ${s}\n"
          }
        } else {
          String s = "${rsp.get(it)}"
          boolean ro = rsp.isReadonly(it)
          if (s.length() > 64) s = "${s[0..31]} ... ${s[-31..-1]}"
          out.add "${it.class.name}.${it} ${ro?'⤇':'='} ${s}\n"
        }
      }
      String title = rsp.get(new NamedParameter('title'))
      String description = rsp.get(new NamedParameter('description'))
      if (title == null) title = req.recipient.name.toUpperCase()
      if (!out.isEmpty()) Collections.sort(out)
      StringBuffer sb = new StringBuffer()
      sb.append "« ${title} »\n"
      if (description) sb.append "\n${description}\n"
      String pcls = null
      out.each {
        def pos = it.indexOf(' ')
        pos = it.substring(0, pos).lastIndexOf('.')
        if (pos <= 0) {
          def pc = 'NamedParameter'
          if (!pc.equals(pcls)) {
            sb.append "\n[${pc}]\n"
            pcls = pc
          }
          if (pos == 0) it = it.substring(1)
        } else {
          def pc = it.substring(0, pos).replace('$', '.')
          if (!pc.equals(pcls)) {
            sb.append "\n[${pc}]\n"
            pcls = pc
          }
          it = it.substring(pos+1)
        }
        sb.append "  ${it}"
      }
      return sb.toString()
    }

    AgentID.metaClass.getAt = { int n ->
      if (delegate.owner == null) throw new FjageException('Parameters not suported on unowned AgentID')
      if (n < 0) throw new FjageException("Index must be non-negative");
      return new IndexedParameterHelper(parent: delegate, index: n)
    }

    AgentID.metaClass.toString = { ->
      String s = null
      if (delegate.owner instanceof Agent) s = delegate.getAllParameters()
      return s?:delegate.name
    }

  }
}

@groovy.transform.PackageScope
class IndexedParameterHelper {

  def parent
  int index

  def propertyMissing(String name, value) {
    Parameter p = new NamedParameter(name)
    ParameterReq req = new ParameterReq().set(p, value)
    req.index = index
    Message rsp = parent.request(req, 5000)
    if (rsp == null) throw new FjageException("Parameter ${name} could not be set [no response]")
    if (rsp instanceof ParameterRsp) {
      def p1 = rsp.parameters()
      if (p1.size() == 0) throw new FjageException("Parameter ${name} could not be set [empty response]")
      def v = rsp.get(p1.first())
      if (v == null) throw new FjageException("Parameter ${name} could not be set [no such parameter]")
      if (v instanceof Number && value instanceof Number) {
        def dv = Math.abs(value-v)
        if (dv >= 1e-6) throw new FjageException("WARNING: Parameter ${name} set to ${v}")
      } else if (v != value) throw new FjageException("WARNING: Parameter ${name} set to ${v}")
    } else {
      throw new FjageException('Parameters not supported by agent')
    }
  }

  def propertyMissing(String name) {
    Parameter p = new NamedParameter(name)
    ParameterReq req = new ParameterReq().get(p)
    req.index = index
    Message rsp = parent.request(req, 2000)
    if (rsp == null) return null
    if (rsp instanceof ParameterRsp) {
      def p1 = rsp.parameters()
      if (p1.size() == 0) return null
      return rsp.get(p1.first())
    } else {
      throw new FjageException('Parameters not supported by agent')
    }
  }

  String toString() {
    String s = parent.getAllParameters(index)
    return s ?: "${parent as String}[${index}]"
  }

}
