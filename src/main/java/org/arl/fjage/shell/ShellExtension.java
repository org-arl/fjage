/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

/**
 * This interface tags a class as providing shell commands to be loaded by a
 * script engine. Shell commands are exposed as static methods (or attributes)
 * of a class. Static attribute "__doc__" is exported as markdown help text.
 * A static method "__init__(ScriptEngine)" is called on initialization, if
 * present. A shell extension may be called from multiple threads (if multiple
 * shell agents load the extension), and so any persistent variables must be
 * thread local.
 */
public interface ShellExtension {
  // tag interface only
}
