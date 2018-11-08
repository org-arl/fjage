/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

// package org.arl.fjage.test

import org.arl.fjage.*
import org.arl.fjage.remote.*
import org.arl.fjage.shell.*
import org.arl.fjage.connectors.*

import org.junit.Test

class fjagejsTest {

  @Test
  void fjageJSTest() {
    def platform = new RealTimePlatform()
    def container = new MasterContainer(platform, 5081)
    WebServer.getInstance(8080).add("/", "/org/arl/fjage/web")
    WebServer.getInstance(8080).add("/test", new File('src/jsTest/groovy'))
    Connector conn = new WebSocketConnector(8080, "/shell/ws")
    container.addConnector(new WebSocketConnector(8080, "/ws", true))
    platform.start()

    sleep(50000)
  }
}
