/******************************************************************************

Copyright (c) 2016, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

// CompileStatic type-checking extension used by GroovyScriptEngine.parse() to statically
// check shell scripts/commands without running them (see ShellCheckReq).
//
// The fjage shell shares one Binding across shell.exec() calls: any `name = value` (without
// `def`) becomes a binding variable reused by later commands. CompileStatic has no knowledge
// of these and would reject them as "undeclared". This extension tolerates them, and keeps
// the shell's dynamic features (agent-name shortcuts, aid.param metaclass access,
// methodMissing) compilable, while still letting CompileStatic flag genuine issues such as
// syntax errors, type mismatches, unknown methods on known types and arity errors.

import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.syntax.Types
import org.arl.fjage.shell.GroovyScriptEngine

// Names assigned without `def` earlier in this script; treated as dynamic locals so that a
// later read of the same name within the script does not get flagged.
def assigned = new HashSet<String>()

unresolvedVariable { var ->
  def b = GroovyScriptEngine.currentCheckBinding()
  if ((b != null && b.hasVariable(var.name)) || assigned.contains(var.name)) {
    // a known shell binding variable (or one assigned earlier in this script)
    makeDynamic(var)
    return
  }
  def bin = enclosingBinaryExpression
  if (bin instanceof BinaryExpression && bin.operation.type == Types.ASSIGN && bin.leftExpression.is(var)) {
    // `name = value` at the top level creates/updates a shell binding variable
    assigned << var.name
    makeDynamic(var)
    return
  }
  // otherwise leave it unresolved so CompileStatic reports it as a real error
}

// aid.param metaclass access and other dynamic property reads/writes
unresolvedProperty { pexp ->
  makeDynamic(pexp)
}

// methodMissing routing (agent-name shortcuts, running scripts by name, etc.)
methodNotFound { receiver, name, argList, argTypes, call ->
  makeDynamic(call)
}
