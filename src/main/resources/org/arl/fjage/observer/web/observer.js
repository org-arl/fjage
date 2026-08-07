/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

(function () {
  'use strict';

  var MAX = 5000;         // messages retained in this browser
  var MAX_ROWS = 400;     // arrows drawn in the sequence diagram
  var STORE = 'fjage-observer';

  var evs = [];                 // ring buffer of observed messages, oldest first
  var endpoints = {};           // name -> {topic, count}
  var order = [];               // endpoint names, in lifeline order
  var shown = {};               // endpoint names selected for display
  var dropped = {};             // endpoint names dropped in the container
  var paused = false, selected = null, pending = false;
  // while paused, nothing newer than this sequence number is displayed, so that
  // a render triggered by something other than an arriving message — selecting
  // a message, say — cannot pull in traffic that arrived after the pause
  var freezeSeq = null;

  var $ = function (id) { return document.getElementById(id); };

  //////////// persistence

  function save() {
    try {
      localStorage.setItem(STORE, JSON.stringify({
        theme: document.documentElement.getAttribute('data-theme'),
        order: order, shown: Object.keys(shown)
      }));
    } catch (ex) { /* private browsing, quota, ... — not worth breaking over */ }
  }

  function load() {
    var s = null;
    try { s = JSON.parse(localStorage.getItem(STORE)); } catch (ex) { s = null; }
    if (!s) return;
    if (s.theme) document.documentElement.setAttribute('data-theme', s.theme);
    if (s.order) order = s.order.slice();
    (s.shown || []).forEach(function (n) { shown[n] = true; });
    // `order` is only a preference for how to lay out lifelines — endpoints are
    // never resurrected from it, or a restarted container would still be shown
    // listing whatever the previous one happened to talk to
    // `dropped` is not persisted: it mirrors the container's filter, which the
    // server sends on connect
  }

  //////////// websocket

  var base = location.pathname.replace(/\/(index\.html)?$/, '');
  var ws = null, buf = '', backoff = 500;

  function connect() {
    // the trailing slash matters: Jetty redirects a bare context path, and
    // browsers do not follow redirects on a web socket upgrade
    var url = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + base + '/ws/';
    ws = new WebSocket(url);
    ws.onopen = function () {
      backoff = 500;
      $('status').className = 'up';
      $('status').title = 'connected';
      // the container filter is shared by all clients, so a new client adopts
      // the current state rather than pushing its own — only an explicit apply
      // or drop mutates the shared filter
      send({ action: 'state' });
    };
    ws.onclose = function () {
      $('status').className = '';
      $('status').title = 'disconnected';
      setTimeout(connect, backoff);
      backoff = Math.min(backoff * 2, 5000);
    };
    ws.onerror = function () { /* onclose follows, and handles the retry */ };
    ws.onmessage = function (ev) {
      // the hub connector coalesces writes, so frames do not align with events
      buf += ev.data;
      var i;
      while ((i = buf.indexOf('\n')) >= 0) {
        var line = buf.slice(0, i);
        buf = buf.slice(i + 1);
        if (!line.trim()) continue;
        var o = null;
        try { o = JSON.parse(line); } catch (ex) { o = null; }
        if (o) handle(o);
      }
    };
  }

  function send(obj) {
    if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj) + '\n');
  }

  function handle(o) {
    if (o.action === 'send') addEvent(o);
    else if (o.action === 'endpoints') addEndpoints(o);
    else if (o.action === 'state') applyState(o);
    else if (o.action === 'stats') stats(o);
  }

  function stats(o) {
    $('count').textContent = o.count;
    $('dropped').textContent = o.dropped;
  }

  function applyState(o) {
    // the container filter is shared by all clients, so mirror it here
    var f = o.filter || {};
    if (document.activeElement !== $('f-clazz')) $('f-clazz').value = f.clazz || '';
    if (document.activeElement !== $('f-xclazz')) $('f-xclazz').value = f.excludeClazz || '';
    var d = {};
    (f.excludeEndpoints || []).forEach(function (n) { d[n] = true; });
    dropped = d;
    $('scope').textContent = o.container ? 'container ' : '';
    if (o.container) {
      var b = document.createElement('b');
      b.textContent = o.container;
      $('scope').appendChild(b);
    }
    stats(o);
    renderEndpoints();
    schedule();
  }

  //////////// events and endpoints

  function addEndpoints(o) {
    var list = o.endpoints || [];
    // the observer is authoritative about what it has seen, so a full list
    // replaces what we knew — otherwise clearing endpoints in the container
    // would leave them on screen here
    if (o.full) endpoints = {};
    for (var i = 0; i < list.length; i++) {
      var e = list[i];
      endpoints[e.name] = { topic: !!e.topic, count: e.count || 0 };
      if (order.indexOf(e.name) < 0) order.push(e.name);
    }
    renderEndpoints();
    schedule();
  }

  function addEvent(o) {
    var d = (o.message && o.message.data) || {};
    var clazz = (o.message && o.message.clazz) || '?';
    evs.push({
      seq: o.seq, time: o.time, ptime: o.ptime,
      from: d.sender || '', to: d.recipient || '',
      clazz: clazz, sclazz: clazz.replace(/^.*\./, ''),
      perf: d.perf || '',
      id: d.msgID || '', inReplyTo: d.inReplyTo || '',
      topic: typeof d.recipient === 'string' && d.recipient.charAt(0) === '#',
      raw: o
    });
    if (evs.length > MAX) evs.splice(0, evs.length - MAX);
    schedule();
  }

  function visible(e) {
    if (freezeSeq !== null && e.seq > freezeSeq) return false;
    if (dropped[e.from] || dropped[e.to]) return false;
    return !!(shown[e.from] || shown[e.to]);
  }

  function visibleEvents() {
    if (!anyShown()) return [];
    var out = [];
    for (var i = evs.length - 1; i >= 0 && out.length < MAX_ROWS; i--)
      if (visible(evs[i])) out.push(evs[i]);
    return out.reverse();
  }

  function anyShown() {
    for (var k in shown) if (shown[k]) return true;
    return false;
  }

  //////////// rendering

  function schedule() {
    if (pending || paused) return;
    pending = true;
    // a render scheduled just before a pause must not land after it
    requestAnimationFrame(function () { pending = false; if (!paused) render(); });
  }

  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                    .replace(/"/g, '&quot;');
  }

  function isTopic(n) {
    // the observer flags topics explicitly, but a name carried over in a drop
    // rule may not have been seen yet, and the '#' prefix is enough
    return n in endpoints ? endpoints[n].topic : n.charAt(0) === '#';
  }

  function fmtCount(n) {
    if (!n) return '';
    if (n >= 1000000) return (n / 1000000).toFixed(1).replace(/\.0$/, '') + 'M';
    if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
    return String(n);
  }

  function renderEndpoints() {
    var ul = $('endpoints');
    // list what the observer has actually seen, plus anything it is currently
    // dropping, so that a drop rule can always be undone
    var known = {};
    for (var e in endpoints) known[e] = true;
    for (var d in dropped) if (dropped[d]) known[d] = true;
    var names = order.filter(function (n) { return known[n]; });
    for (var n2 in known) if (names.indexOf(n2) < 0) names.push(n2);
    var h = [];
    for (var i = 0; i < names.length; i++) {
      var n = names[i];
      h.push('<li draggable="true" data-name="' + esc(n) + '"' +
             (dropped[n] ? ' class="dropped"' : '') + '>' +
             '<span class="grip">⣿</span>' +
             '<span class="nm' + (isTopic(n) ? ' topic' : '') + '" title="' + esc(n) + '">' +
             esc(n) + '</span>' +
             '<span class="cnt" title="messages to or from this endpoint">' +
             fmtCount(n in endpoints ? endpoints[n].count : 0) + '</span>' +
             '<button class="tog show' + (shown[n] ? ' on' : '') +
             '" data-act="show" title="show messages to/from this endpoint">show</button>' +
             '<button class="tog drop' + (dropped[n] ? ' on' : '') +
             '" data-act="drop" title="drop these messages in the container">drop</button>' +
             '</li>');
    }
    ul.innerHTML = h.join('') || '<li class="empty">waiting for traffic…</li>';
  }

  function lifelines(list) {
    var used = {};
    for (var i = 0; i < list.length; i++) {
      if (list[i].from) used[list[i].from] = true;
      if (list[i].to) used[list[i].to] = true;
    }
    // an endpoint the user asked to see gets a lifeline even before it says
    // anything, so that selecting it has a visible effect -- but only if this
    // observer knows it, since selections are remembered per host and port, and
    // would otherwise leak onto a different container served at the same address
    for (var k in shown) if (shown[k] && !dropped[k] && (k in endpoints)) used[k] = true;
    // `order` only decides the layout — every used endpoint must get a lifeline
    // whether or not it has been ordered yet, or an arrow could end up with no
    // lifeline to attach to
    var out = order.filter(function (n) { return used[n] && !dropped[n]; });
    for (var n in used)
      if (used[n] && !dropped[n] && out.indexOf(n) < 0) out.push(n);
    return out;
  }

  /**
   * Sequence numbers of the messages correlated with the selected one: the
   * message it is a reply to, and any replies to it.
   */
  function correlated() {
    var rel = {};
    if (selected === null) return rel;
    var sel = null;
    for (var i = evs.length - 1; i >= 0; i--) if (evs[i].seq === selected) { sel = evs[i]; break; }
    if (!sel) return rel;
    for (var j = 0; j < evs.length; j++) {
      var e = evs[j];
      if (e.seq === sel.seq) continue;
      if (freezeSeq !== null && e.seq > freezeSeq) continue;
      if ((sel.inReplyTo && e.id === sel.inReplyTo) || (sel.id && e.inReplyTo === sel.id))
        rel[e.seq] = true;
    }
    return rel;
  }

  function render() {
    var list = visibleEvents();
    $('shown').textContent = list.length;
    var parts = lifelines(list);
    var view = $('view');
    if (!parts.length) {
      $('seqhdr').innerHTML = '';
      $('seq').innerHTML = '';
      $('empty').style.display = '';
      $('empty').textContent = anyShown() ? 'No messages yet for the selected endpoints.'
                                          : 'Select one or more endpoints to show their messages.';
      return;
    }
    $('empty').style.display = 'none';

    var idx = {};
    for (var p = 0; p < parts.length; p++) idx[parts[p]] = p;
    var rel = correlated();
    var GUT = 150;                          // time gutter down the left
    var W = 180, X0 = GUT + 90, H = 34, Y0 = 22;
    var width = X0 + 90 + Math.max(0, parts.length - 1) * W;
    var height = Y0 + list.length * H + 16;
    var cx = function (name) { return X0 + idx[name] * W; };

    var hdr = ['<svg width="' + width + '" height="36" xmlns="http://www.w3.org/2000/svg">',
               '<text class="lbl" x="8" y="16">time</text>',
               '<text class="lbl" x="' + (GUT - 8) + '" y="16" text-anchor="end">Δ</text>'];
    for (var q = 0; q < parts.length; q++) {
      var x = X0 + q * W;
      var nm = parts[q], shrt = nm.length > 22 ? nm.slice(0, 21) + '…' : nm;
      hdr.push('<text class="part' + (isTopic(nm) ? ' topic' : '') + '" x="' + x +
               '" y="16" text-anchor="middle">' + esc(shrt) + '<title>' + esc(nm) + '</title></text>');
      hdr.push('<line class="life" x1="' + x + '" y1="22" x2="' + x + '" y2="36"/>');
    }
    hdr.push('</svg>');

    var s = ['<svg width="' + width + '" height="' + height + '" xmlns="http://www.w3.org/2000/svg">',
             '<defs><marker id="a" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" ' +
             'markerHeight="7" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="currentColor"/>' +
             '</marker></defs>'];
    for (var r = 0; r < parts.length; r++) {
      var lx = X0 + r * W;
      s.push('<line class="life" x1="' + lx + '" y1="0" x2="' + lx + '" y2="' + height + '"/>');
    }
    for (var k2 = 0; k2 < list.length; k2++) {
      var e = list[k2], y = Y0 + k2 * H;
      var known1 = e.from in idx, known2 = e.to in idx;
      if (!known1 && !known2) continue;      // nothing to attach an arrow to
      var cls = 'arrow' + (e.topic ? ' topic' : '');
      var col = e.topic ? 'var(--topic)' : 'var(--accent)';
      s.push('<g class="row' + (selected === e.seq ? ' sel' : (rel[e.seq] ? ' rel' : '')) +
             '" data-seq="' + e.seq + '" color="' + col + '">');
      s.push('<text class="lbl time" x="8" y="' + (y + 4) + '">' + fmtTime(e.time) + '</text>');
      if (k2 > 0) {
        var dt = e.time - list[k2-1].time;
        s.push('<text class="lbl time" x="' + (GUT - 8) + '" y="' + (y + 4) +
               '" text-anchor="end">+' + dt + 'ms</text>');
      }
      if (!known1 || !known2) {
        // one end is off the diagram (dropped, or an unnamed sender) — draw a stub
        var xx = cx(known1 ? e.from : e.to);
        var into = !known1;
        var x1 = into ? xx - 46 : xx, x2 = into ? xx : xx + 46;
        s.push('<line class="' + cls + '" x1="' + x1 + '" y1="' + y + '" x2="' + x2 + '" y2="' + y +
               '" marker-end="url(#a)"/>');
        s.push('<text class="lbl" x="' + ((x1 + x2) / 2) + '" y="' + (y - 5) +
               '" text-anchor="middle">' + esc(e.sclazz) + '</text>');
      } else if (e.from === e.to) {
        var xs = cx(e.from);
        s.push('<path class="' + cls + '" d="M' + xs + ',' + (y - 8) + ' h34 v16 h-30" ' +
               'marker-end="url(#a)"/>');
        s.push('<text class="lbl" x="' + (xs + 40) + '" y="' + (y - 6) + '">' + esc(e.sclazz) + '</text>');
      } else {
        var a1 = cx(e.from), a2 = cx(e.to);
        s.push('<line class="' + cls + '" x1="' + a1 + '" y1="' + y + '" x2="' + a2 + '" y2="' + y +
               '" marker-end="url(#a)"/>');
        s.push('<text class="lbl" x="' + ((a1 + a2) / 2) + '" y="' + (y - 5) +
               '" text-anchor="middle">' + esc(e.sclazz) +
               (e.perf ? ' <tspan opacity="0.6">' + esc(e.perf) + '</tspan>' : '') + '</text>');
      }
      s.push('<rect class="hit" x="0" y="' + (y - H / 2) + '" width="' + width + '" height="' + H +
             '"><title>' + esc(e.from + ' → ' + e.to + '  ' + e.clazz) + '</title></rect>');
      s.push('</g>');
    }
    s.push('</svg>');

    var atBottom = view.scrollTop + view.clientHeight >= view.scrollHeight - 40;
    $('seqhdr').innerHTML = hdr.join('');
    $('seq').innerHTML = s.join('');
    if (atBottom) view.scrollTop = view.scrollHeight;
  }

  function fmtTime(t) {
    var d = new Date(t);
    return ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2) + ':' +
           ('0' + d.getSeconds()).slice(-2) + '.' + ('00' + d.getMilliseconds()).slice(-3);
  }

  function showDetail(seq) {
    selected = seq;
    var e = null;
    for (var i = evs.length - 1; i >= 0; i--) if (evs[i].seq === seq) { e = evs[i]; break; }
    if (!e) {
      $('detail').innerHTML = '<div class="empty">Click a message to see its contents.</div>';
      return;
    }
    var rel = Object.keys(correlated());
    $('detail').innerHTML = '<pre>' + esc(
      '#' + e.seq + '  ' + fmtTime(e.time) + '  (ptime ' + e.ptime + ')\n' +
      e.from + ' → ' + e.to + '\n' + e.clazz + '\n' +
      (rel.length ? 'correlated with #' + rel.join(', #') + '\n' : '') + '\n' +
      JSON.stringify(e.raw, null, 2)) + '</pre>';
    render();
  }

  //////////// interaction

  function scrollSelectedIntoView() {
    var g = document.querySelector('#seq g.row[data-seq="' + selected + '"]');
    if (g && g.scrollIntoView) g.scrollIntoView({ block: 'nearest' });
  }

  /**
   * Steps the selection through the messages on screen. Moving off either end
   * stays put rather than wrapping, so holding a key does not loop around.
   */
  function step(delta) {
    var list = visibleEvents();
    if (!list.length) return;
    var i = -1;
    for (var j = 0; j < list.length; j++) if (list[j].seq === selected) { i = j; break; }
    if (i < 0) i = delta > 0 ? -1 : list.length;      // start from the near end
    i += delta;
    if (i < 0 || i >= list.length) return;
    showDetail(list[i].seq);
    scrollSelectedIntoView();
  }

  document.addEventListener('keydown', function (ev) {
    if (ev.ctrlKey || ev.metaKey || ev.altKey) return;
    var t = ev.target;
    if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return;
    if (ev.key === 'ArrowDown') step(1);
    else if (ev.key === 'ArrowUp') step(-1);
    else return;
    ev.preventDefault();                              // do not scroll the page too
  });

  $('view').addEventListener('click', function (ev) {
    var t = ev.target;
    while (t && t !== this && !(t.dataset && t.dataset.seq)) t = t.parentNode;
    if (t && t.dataset && t.dataset.seq) showDetail(parseInt(t.dataset.seq, 10));
  });

  function sendFilter() {
    send({
      action: 'filter',
      filter: {
        clazz: $('f-clazz').value,
        excludeClazz: $('f-xclazz').value,
        excludeEndpoints: Object.keys(dropped).filter(function (n) { return dropped[n]; })
      }
    });
  }

  $('endpoints').addEventListener('click', function (ev) {
    var b = ev.target;
    if (!b.dataset || !b.dataset.act) return;
    var li = b.parentNode, n = li.dataset.name;
    if (b.dataset.act === 'show') {
      if (shown[n]) delete shown[n]; else shown[n] = true;
    } else {
      if (dropped[n]) delete dropped[n]; else dropped[n] = true;
      sendFilter();
    }
    save();
    renderEndpoints();
    schedule();
  });

  // drag to reorder lifelines
  var dragging = null;

  $('endpoints').addEventListener('dragstart', function (ev) {
    var li = ev.target.closest ? ev.target.closest('li') : null;
    if (!li || !li.dataset.name) return;
    dragging = li.dataset.name;
    ev.dataTransfer.effectAllowed = 'move';
    // Firefox will not start a drag without data set
    try { ev.dataTransfer.setData('text/plain', dragging); } catch (e) { /* ignore */ }
  });

  $('endpoints').addEventListener('dragover', function (ev) {
    if (!dragging) return;
    ev.preventDefault();
    ev.dataTransfer.dropEffect = 'move';
    var li = ev.target.closest ? ev.target.closest('li') : null;
    var all = $('endpoints').children;
    for (var i = 0; i < all.length; i++) all[i].classList.remove('drop-target');
    if (li) li.classList.add('drop-target');
  });

  $('endpoints').addEventListener('drop', function (ev) {
    if (!dragging) return;
    ev.preventDefault();
    var li = ev.target.closest ? ev.target.closest('li') : null;
    var target = li && li.dataset ? li.dataset.name : null;
    var from = order.indexOf(dragging);
    if (from >= 0 && target !== dragging) {
      order.splice(from, 1);
      var to = target ? order.indexOf(target) : order.length;
      order.splice(to < 0 ? order.length : to, 0, dragging);
    }
    dragging = null;
    save();
    renderEndpoints();
    schedule();
  });

  $('endpoints').addEventListener('dragend', function () {
    dragging = null;
    var all = $('endpoints').children;
    for (var i = 0; i < all.length; i++) all[i].classList.remove('drop-target');
  });

  $('none').onclick = function () {
    shown = {};
    save();
    renderEndpoints();
    schedule();
  };

  $('clearep').onclick = function () {
    // the observer forgets too, and echoes the empty list back to every client;
    // selections are kept, so an endpoint that is still active comes straight
    // back with its selection intact
    order = order.filter(function (n) { return dropped[n]; });
    save();
    send({ action: 'clearEndpoints' });
  };

  $('pause').onclick = function () {
    // messages keep arriving and buffering while paused, so resuming shows
    // what happened rather than a gap
    paused = !paused;
    freezeSeq = paused ? (evs.length ? evs[evs.length-1].seq : -1) : null;
    this.textContent = paused ? 'Resume' : 'Pause';
    this.className = paused ? 'on' : '';
    if (!paused) schedule();
  };

  $('clear').onclick = function () {
    evs = [];
    selected = null;
    $('detail').innerHTML = '<div class="empty">Click a message to see its contents.</div>';
    render();
  };

  $('apply').onclick = sendFilter;
  $('f-clazz').onkeydown = function (ev) { if (ev.key === 'Enter') sendFilter(); };
  $('f-xclazz').onkeydown = function (ev) { if (ev.key === 'Enter') sendFilter(); };

  $('theme').onclick = function () {
    var cur = document.documentElement.getAttribute('data-theme');
    if (!cur) cur = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
                    ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', cur === 'dark' ? 'light' : 'dark');
    save();
  };

  load();
  renderEndpoints();
  render();
  connect();
})();
