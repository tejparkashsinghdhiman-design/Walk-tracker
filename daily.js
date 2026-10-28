// Adds an all-day "Today" tab. Only runs inside the Android app (native step service).
(function () {
  const C = window.Capacitor;
  if (!C || !C.registerPlugin || (C.isNativePlatform && !C.isNativePlatform())) return;
  const S = C.registerPlugin('Steps'), old = show;
  let lastG = 0;
  const st = document.createElement('style');
  st.textContent = '#dSteps{font-size:clamp(68px,22vw,110px);font-weight:700;letter-spacing:-.04em;line-height:1;font-variant-numeric:tabular-nums}#dDist{font-size:30px;font-weight:650;margin-top:8px}#vW .pb,#pt{display:none}';
  document.head.appendChild(st);
  const tab = document.createElement('button');
  tab.textContent = 'Today';
  const nav = document.querySelector('.tabs');
  nav.insertBefore(tab, nav.firstChild);
  const v = document.createElement('section');
  v.hidden = true;
  v.innerHTML = '<section class="hero"><div id="dSteps">0</div><div class="u">steps today</div><div id="dDist">0.00 km</div></section>'
    + '<div class="pb"><i id="dPf"></i></div><small id="dPt" style="display:block;color:var(--mut);font-size:13px;margin:6px 2px 14px"></small>'
    + '<section class="stats"><div class="stat"><b id="dKcal">0</b><span>Calories (kcal)</span></div><div class="stat"><b id="dAvg">0</b><span>Average, last 7 days</span></div></section>'
    + '<div class="plot" id="dPlot"></div><div class="lab" id="dLab"></div>'
    + '<section class="stats"><div class="stat"><b id="dStr">0</b><span>Day streak (goal met)</span></div><div class="stat"><b id="dBest">0</b><span>Best day, last 30 days</span></div></section>'
    + '<h2>Steps by hour (today)</h2><div class="plot" id="hPlot" style="gap:2px"></div><div class="lab" id="hLab" style="gap:2px;font-size:10px"></div>'
    + '<h2>Keep it running</h2><div class="f"><span id="kaTxt">Checking...</span><button class="x" id="kaBtn">Fix</button></div>'
    + '<div class="f"><span>Autostart / background launch</span><button class="x" id="asBtn">Open</button></div>'
    + '<div class="f"><span>Export your data</span><button class="x" id="exBtn">Share</button></div>'
    + '<p class="tip" id="dDiag"></p>'
    + '<p class="tip" id="dMsg">Counting all day in the background. The count resets at midnight.</p>';
  document.querySelector('main').appendChild(v);

  async function refresh() {
    try {
      const r = await S.today(), n = Math.round((r.steps || 0) * set.cf / 100), g = set.g, m = n * stride();
      $('dSteps').textContent = n.toLocaleString();
      $('dDist').textContent = fmtDist(m) + ' ' + unit;
      $('dKcal').textContent = Math.round(0.55 * set.w * m / 1000);
      $('dPf').style.width = Math.min(100, n / g * 100) + '%';
      $('dPt').textContent = n.toLocaleString() + ' / ' + g.toLocaleString() + ' steps' + (n >= g ? ' \u2713 Goal reached' : '');
      if (r.sensor === false) $('dMsg').textContent = 'This phone has no step counter sensor.';
      const hr = await S.history({ days: 30 }), all = (hr.days || []).map(x => ({d: x.d, n: Math.round(x.n * set.cf / 100)})), h = all.slice(-7), mx = Math.max(g, ...h.map(x => x.n), 1);
      $('dPlot').innerHTML = h.map(x => '<div class="col"><i class="' + (x.n >= g ? 'hit' : '') + '" style="height:' + x.n / mx * 100 + '%"></i></div>').join('')
        + '<div class="goal" style="bottom:' + g / mx * 100 + '%"></div>';
      $('dLab').innerHTML = h.map(x => '<span>' + 'SMTWTFS'[new Date(+x.d.slice(0, 4), +x.d.slice(4, 6) - 1, +x.d.slice(6)).getDay()] + '</span>').join('');
      $('dAvg').textContent = Math.round(h.reduce((a, x) => a + x.n, 0) / Math.max(1, h.length)).toLocaleString();
      let s = 0, i = all.length - 1; if (all[i].n < g) i--; while (i >= 0 && all[i].n >= g) { s++; i--; }
      $('dStr').textContent = s; $('dBest').textContent = Math.max(...all.map(x => x.n)).toLocaleString();
      const hh = (hr.hours || []).map(x => Math.round(x * set.cf / 100)), hm = Math.max(...hh, 1);
      $('hPlot').innerHTML = hh.map(x => '<div class="col"><i style="height:' + x / hm * 100 + '%"></i></div>').join('');
      $('hLab').innerHTML = hh.map((x, k) => '<span>' + (k % 6 === 0 ? k : '') + '</span>').join('');
      if (set.g !== lastG) { lastG = set.g; S.setGoal({ goal: set.g }); }
      const st = await S.diag();
      $('kaTxt').textContent = st.ignoring ? 'Battery restrictions: off (good)' : 'Battery restrictions: ON, may stop counting';
      $('dDiag').textContent = 'Step service: ' + (st.svc ? 'running' : 'NOT running') + ' | last sensor update: ' + (st.evtAgo < 0 ? 'none yet' : st.evtAgo + ' s ago') + ' | Physical activity: ' + (st.act ? 'allowed' : 'DENIED') + ' | Step sensor: ' + (st.sensor ? 'yes' : 'NO') + ' | ' + st.brand + ', Android ' + st.sdk;
      if (!st.svc && st.act && st.sensor) S.start().catch(() => {});
    } catch (e) {}
  }

  window.show = function (k) {
    if (k === 'D') {
      old('W');
      $('vW').hidden = true;
      $('tW').classList.remove('on');
      v.hidden = false;
      tab.classList.add('on');
      refresh();
    } else {
      v.hidden = true;
      tab.classList.remove('on');
      old(k);
    }
  };
  tab.onclick = () => show('D');
  setInterval(() => { if (!v.hidden && !document.hidden) refresh(); }, 3000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && !v.hidden) refresh(); });
  show('D');
  $('kaBtn').onclick = () => S.openSettings({ kind: 'battery' }).catch(() => {});
  $('asBtn').onclick = () => S.openSettings({ kind: 'autostart' }).then(r => {
    if (!r.opened) $('dMsg').textContent = 'Opened app settings. Look for Autostart, Battery or Background activity and allow it.';
  }).catch(() => {});
  $('exBtn').onclick = async () => {
    try {
      const x = await S.history({ days: 30 });
      const rows = ['date,all_day_steps'].concat((x.days || []).map(d => d.d + ',' + Math.round(d.n * set.cf / 100)));
      rows.push('', 'walk_start,distance_m,seconds,steps,kcal');
      hist.forEach(w => rows.push(new Date(w.ts).toISOString() + ',' + Math.round(w.m) + ',' + Math.round(w.s / 1000) + ',' + hn(w) + ',' + Math.round(w.k || 0)));
      await S.shareText({ text: rows.join('\n') });
    } catch (e) {}
  };

  // ---- Native walk session: GPS + steps keep counting with the screen off
  let pollT = null;
  const apply = r => {
    dist = r.dist; sSteps = r.steps; elapsed = r.ms; sensor = true; estD = r.estD || 0;
    gSteps = Math.max(0, r.steps - Math.round(estD / stride()));
    tStart = Date.now();
    if (r.acc >= 0) setSignal(r.acc);
    render();
  };
  const attach = r => {
    if (!r.active) { clearInterval(pollT); pollT = null; return; }
    setState(r.running ? 'running' : 'paused');
    apply(r);
    if (!pollT) pollT = setInterval(async () => {
      try { const x = await S.walkState(); if (!x.active) { clearInterval(pollT); pollT = null; return; } apply(x); } catch (e) {}
    }, 1000);
  };
  window.begin = async function (reset) {
    msg('');
    try { attach(reset ? await S.walkStart({ stride: stride(), accel: set.sa === 1 }) : await S.walkResume()); }
    catch (e) { msg('Allow Precise location and Physical activity so walks keep counting with the screen off.'); }
  };
  $('pauseBtn').onclick = async () => { try { attach(await S.walkPause()); } catch (e) {} };
  $('stopBtn').onclick = async () => {
    try { const r = await S.walkStop(); apply(r); clearInterval(pollT); pollT = null; state = 'paused'; finish(); } catch (e) {}
  };
  document.addEventListener('visibilitychange', () => { if (!document.hidden) { msg(''); S.walkState().then(attach).catch(() => {}); } });
  const tips = document.querySelectorAll('#vW .tip');
  if (tips[1]) tips[1].textContent = 'Walks keep counting with the screen off or the app closed (a notification shows while a walk runs). Start outdoors and wait for 3 or more bars.';
  S.walkState().then(r => {
    if (r.active) attach(r);
    else if (state === 'paused') { dist = elapsed = sSteps = gSteps = estD = 0; setState('idle'); msg(''); render(); }
  }).catch(() => {});

  S.start().then(() => {
    refresh();
    try { if (!localStorage.getItem('wt_ka')) { localStorage.setItem('wt_ka', '1'); S.keepAlive(); } } catch (e) {}
  }).catch(() => { $('dMsg').textContent = 'Allow the Physical activity permission so steps can be counted all day.'; });
})();
