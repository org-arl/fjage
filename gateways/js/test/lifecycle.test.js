import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { setImmediate } from 'node:timers/promises';
import { test } from 'node:test';
import { Gateway, Message } from '../src/fjage.js';
import TCPConnector from '../src/tcpconnector.js';

// Exercise the initial deferred net import before any other TCP connector exists.
test('TCP close before socket creation prevents a later connection', async () => {
  const connector = new TCPConnector({ keepAlive: true });
  assert.equal(connector.sock, undefined);
  connector.close();
  await import('net');
  await setImmediate();
  assert.equal(connector.sock, undefined);
  assert.equal(connector.write('queued'), false);
});

function createGateway(t, transport, opts = {}) {
  const ws = transport === 'WebSocket';
  const socket = new EventEmitter();
  Object.assign(socket, { CONNECTING: 0, OPEN: 1, CLOSED: 3, readyState: ws ? 0 : 'opening' });
  socket.send = t.mock.fn();
  const close = () => { socket.readyState = ws ? 3 : 'closed'; };
  socket.close = t.mock.fn(close);
  socket.destroy = t.mock.fn(close);
  socket.end = t.mock.fn(close);

  if (ws) {
    t.mock.method(globalThis, 'WebSocket', function () { return socket; });
    const createConnector = Gateway.prototype._createConnector;
    t.mock.method(Gateway.prototype, '_createConnector', function () {
      return createConnector.call(this, new URL('ws://localhost'));
    });
  } else {
    t.mock.method(TCPConnector.prototype, '_sockInit', function () { this.sock = socket; });
  }

  const gateway = new Gateway({ ...opts, keepAlive: true });
  t.after(() => gateway.close());
  const connector = gateway.connector;
  connector._reconnectTime = 100;
  return {
    gateway, connector, socket,
    goodbye: ws ? socket.send : socket.end,
    abort: ws ? socket.close : socket.destroy,
    setup: ws ? '_websockSetup' : '_sockSetup',
    onOpen: ws ? '_onWebsockOpen' : '_onSockOpen',
    open() {
      socket.readyState = ws ? 1 : 'open';
      connector[this.onOpen]();
    },
    disconnect() {
      socket.readyState = ws ? 3 : 'closed';
      connector[ws ? '_websockReconnect' : '_sockReconnect']();
    }
  };
}

for (const transport of ['TCP', 'WebSocket']) {
  test(`${transport}: disconnect cancels every pending receive`, { timeout: 2000 }, async t => {
    t.mock.timers.enable({ apis: ['setTimeout'] });
    const { gateway, connector } = createGateway(t, transport, { cancelPendingOnDisconnect: true });
    const filter = t.mock.fn(() => false);
    const receives = [gateway.receive(Message, 1000), gateway.receive('request-id', -1), gateway.receive(filter, -1)];
    connector._sendConnEvent(false);
    assert.deepEqual(await Promise.all(receives), [null, null, null]);
    assert.equal(Object.keys(gateway._pending_receives).length, 0);
    assert.equal(filter.mock.callCount(), 0);
    t.mock.timers.tick(1000);
  });

  test(`${transport}: disconnect leaves receives pending by default`, async t => {
    const { gateway, connector } = createGateway(t, transport);
    const receives = [gateway.receive(Message, -1), gateway.receive(Message, -1)];
    connector._sendConnEvent(false);
    assert.equal(Object.keys(gateway._pending_receives).length, 2);
    gateway._sendReceivers(null);
    await Promise.all(receives);
  });

  test(`${transport}: close aborts an opening connection and discards queued writes`, t => {
    const fixture = createGateway(t, transport);
    const { gateway, connector, socket, goodbye, abort } = fixture;
    const listener = t.mock.fn();
    gateway.addConnListener(listener);
    const lateOpen = connector[fixture.onOpen].bind(connector);
    assert.equal(connector.write('queued'), true);
    gateway.close();
    lateOpen();
    assert.equal(abort.mock.callCount(), 1);
    assert.equal(goodbye.mock.callCount(), 0);
    assert.equal(socket.send.mock.callCount(), 0);
    assert.equal(connector.pendingOnOpen.length, 0);
    assert.equal(connector.write('after close'), false);
    assert.equal(gateway.connected, false);
    assert.deepEqual(listener.mock.calls.map(call => call.arguments), [[false]]);
  });

  test(`${transport}: closing an open gateway sends one goodbye and cancels receives`, async t => {
    const fixture = createGateway(t, transport, { cancelPendingOnDisconnect: true });
    const { gateway, socket, goodbye } = fixture;
    const listener = t.mock.fn();
    gateway.addConnListener(listener);
    fixture.open();
    socket.send.mock.resetCalls();
    const receive = gateway.receive(Message, -1);
    gateway.close();
    gateway.close();
    assert.equal(await receive, null);
    assert.deepEqual(goodbye.mock.calls.map(call => call.arguments), [['{"alive": false}\n']]);
    assert.equal(gateway.connected, false);
    assert.deepEqual(listener.mock.calls.map(call => call.arguments), [[true], [false]]);
  });

  test(`${transport}: closing cancels a scheduled reconnect`, t => {
    t.mock.timers.enable({ apis: ['setTimeout'] });
    const fixture = createGateway(t, transport);
    const { gateway, connector } = fixture;
    fixture.open();
    const setup = t.mock.method(connector, fixture.setup, () => {});
    fixture.disconnect();
    fixture.disconnect();
    gateway.close();
    t.mock.timers.tick(200);
    assert.equal(setup.mock.callCount(), 0);
    assert.equal(gateway.connected, false);
  });

  test(`${transport}: closing aborts a reconnect already in progress`, t => {
    t.mock.timers.enable({ apis: ['setTimeout'] });
    const fixture = createGateway(t, transport);
    const { gateway, connector, socket, abort } = fixture;
    fixture.open();
    t.mock.method(connector, fixture.setup, () => { socket.readyState = transport === 'TCP' ? 'opening' : 0; });
    fixture.disconnect();
    t.mock.timers.tick(100);
    connector.write('queued');
    gateway.close();
    connector[fixture.onOpen]();
    t.mock.timers.tick(200);
    assert.equal(abort.mock.callCount(), 1);
    assert.equal(connector.pendingOnOpen.length, 0);
    assert.equal(gateway.connected, false);
  });

  test(`${transport}: a connection listener can close the gateway before queued writes run`, t => {
    const fixture = createGateway(t, transport);
    const { gateway, connector, socket, goodbye } = fixture;
    connector.write('queued');
    gateway.addConnListener(connected => { if (connected) gateway.close(); });
    fixture.open();
    assert.equal(gateway.connected, false);
    assert.equal(goodbye.mock.calls.filter(call => call.arguments[0] === '{"alive": false}\n').length, 1);
    assert.equal(socket.send.mock.calls.some(call => call.arguments[0] === 'queued\n'), false);
    assert.equal(socket.listenerCount('close'), 0);
    assert.equal(socket.listenerCount('data'), 0);
    assert.equal(connector.pendingOnOpen.length, 0);
  });
}

test('a normal message satisfies only one matching receive', async t => {
  const { gateway } = createGateway(t, 'TCP');
  const first = gateway.receive(Message, -1);
  const second = gateway.receive(Message, -1);
  const message = new Message();
  assert.equal(gateway._sendReceivers(message), true);
  assert.equal(await first, message);
  assert.equal(Object.keys(gateway._pending_receives).length, 1);
  gateway._sendReceivers(null);
  assert.equal(await second, null);
});
