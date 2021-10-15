/******************************************************************************

Copyright (c) 2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.param;

import java.io.Serializable;

/**
 * Class to define named parameters.
 * @see Parameter
 */
public class NamedParameter implements Parameter, Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  private int ord;

  /**
   * Constructs named parameter with specified name.
   *
   * @param name parameter name.
   */
  public NamedParameter(String name) {
    this.name = name;
    this.ord = -1;
  }

  /**
   * Constructs named parameter with given name and ordinal.
   *
   * @param name of parameter
   * @param ord ordinal
   */
  public NamedParameter(String name, int ord) {
    this.name = name;
    this.ord = ord;
  }

  /**
   * Gets the ordinal value.
   *
   * @return ordinal
   */
  @Override
  public int ordinal() {
    return ord;
  }

  /**
   * Gets the parameter name.
   *
   * @return name of parameter.
   */
  public String name() {
    return name;
  }

  /**
   * Gets the parameter name as string.
   *
   * @return name of parameter.
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * Compares if two named parameters are equals.
   *
   * @return true if two parameters are equal
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof NamedParameter)) return false;
    NamedParameter p = (NamedParameter)obj;
    String name1 = name;
    if (name1.contains(".")) name1 = name1.substring(name1.indexOf('.')+1);
    String name2 = p.name;
    if (name2.contains(".")) name2 = name2.substring(name2.indexOf('.')+1);
    if (!name1.equals(name2)) return false;
    if (ord >= 0 && p.ord >= 0 && p.ord != ord) return false;
    return true;
  }

  /**
   * Gets the hashcode value.
   *
   * @return hashcode value
   */
  @Override
  public int hashCode() {
    return name.hashCode();
  }

}
