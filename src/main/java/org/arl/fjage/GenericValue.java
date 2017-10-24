/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

final public class GenericValue {

  final private Object data;

  public GenericValue(Object value) {
    data = value;
  }

  public Object getValue() {
    return data;
  }

  public Class<?> getType() {
    if (data == null) return null;
    return data.getClass();
  }

  public int hashCode() {
    if (data == null) return super.hashCode();
    return data.hashCode();
  }

  public boolean equals(Object value) {
    if (data == null) {
      if (value == null) return true;
      if (value instanceof GenericValue && ((GenericValue)value).data == null) return true;
      return false;
    }
    if (value instanceof GenericValue) return data.equals(((GenericValue)value).data);
    return data.equals(value);
  }

  public String toString() {
    if (data == null) return "null";
    return data.toString();
  }

}
