/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.param;

import static org.junit.Assert.*;

import org.arl.fjage.remote.JsonMessage;
import org.junit.Test;

public class ParameterRspDefaultsTest {

  private static ParameterRsp decode(String data) {
    return (ParameterRsp)JsonMessage.fromJson("{\"message\":{\"clazz\":\""
      + ParameterRsp.class.getName() + "\",\"data\":" + data + "}}").message;
  }

  @Test
  public void omittedFieldsGetDefaultsWithoutAddingHeaders() {
    ParameterRsp response = decode("{\"param\":\"test\",\"value\":1}");
    NamedParameter parameter = new NamedParameter("test");
    assertEquals(-1, response.getIndex());
    assertFalse(response.isReadonly(parameter));
    assertNull(response.getMessageID());
    assertNull(response.getPerformative());
    response.set(parameter, 2, true);
    assertTrue(response.isReadonly(parameter));
  }

  @Test
  public void explicitNullReadonlyIsSafeToQueryAndUpdate() {
    ParameterRsp response = decode("{\"param\":\"test\",\"value\":1,\"readonly\":null}");
    NamedParameter parameter = new NamedParameter("test");
    assertFalse(response.isReadonly(parameter));
    response.set(new NamedParameter("writable"), 2, false);
    response.set(parameter, 3, true);
    assertTrue(response.isReadonly(parameter));
    assertFalse(response.isReadonly(new NamedParameter("writable")));
  }

}
