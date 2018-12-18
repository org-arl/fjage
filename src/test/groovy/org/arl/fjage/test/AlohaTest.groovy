/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test

import org.arl.fjage.*
import org.junit.Test

class AlohaTest {

  final static int FRAMELEN = 100
  final static int EXPDELAY = 800
  final static int NODES = 4
  final static int TESTTIME = 1000000

  @Test
  void alohaTest() {
    LogFormatter.install(null)
    def p = new DiscreteEventSimulator()
    //def p = new RealTimePlatform()
    def c1 = new Container(p)
    def a1 = new Channel()
    c1.add "channel", a1
    NODES.times { c1.add "node${it}", new Node() }
    def c2 = new Container(p)
    def a2 = new Channel()
    c2.add "channel", a2
    NODES.times { c2.add "node${it}", new Node() }
    long t0 = p.currentTimeMillis()
    p.start()
    println "Running..."
    p.delay(TESTTIME)
    println "Done"
    p.shutdown()
    long dt = p.currentTimeMillis()-t0
    int n1 = a1.attempts-a1.tx
    int s1 = a1.success
    int n2 = a2.attempts-a2.tx
    int s2 = a2.success
    double T1 = (double)s1*FRAMELEN/dt
    double T2 = (double)s2*FRAMELEN/dt
    println "Simulation time: ${dt/1000} seconds"
    println "Offered Load: ${100*NODES*FRAMELEN/EXPDELAY}%"
    println "Success: ${s1}/${n1} = ${n1?100*s1/n1:0}%, ${s2}/${n2} = ${n2?100*s2/n2:0}%"
    println "Throughput: ${100*T1}%, ${100*T2}%"
    assert T1 > 0.16 && T1 < 0.21 && T2 > 0.16 && T2 < 0.21
  }

  private static class Node extends Agent {

    AgentID channel

    void init() {
      channel = agent("channel")
      add new PoissonBehavior(EXPDELAY) {
        void onTick() {
          agent.send new Message(channel, Performative.REQUEST)
        }
      }
    }
  }

  private static class Channel extends Agent {

    int tx = 0          // number of nodes transmitting currently
    int attempts = 0    // number of transmission attempts
    int success = 0     // number of collision-free transmissions

    void init() {
      add new MessageBehavior() {
        void onReceive(Message msg) {
          agent.attempts++
          agent.tx++
          if (agent.tx == 1) {
            agent.add new WakerBehavior(AlohaTest.FRAMELEN) {
              void onWake() {
                if (agent.tx == 1) agent.success++
                agent.tx--
              }
            }
          } else {
            agent.add new WakerBehavior(AlohaTest.FRAMELEN) {
              void onWake() {
                agent.tx--
              }
            }
          }
        }
      }
    }
  }

}

