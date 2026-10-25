// Adds an all-day "Today" tab. Only runs inside the Android app (native step service).
(function () {
  const C = window.Capacitor;
  if (!C || !C.registerPlugin || (C.isNativePlatform && !C.isNativePlatform())) return;
  const S = C.registerPlugin('Steps'), old = show;
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
      const h = ((await S.history()).days || []).map(x => ({d: x.d, n: Math.round(x.n * set.cf / 100)})), mx = Math.max(g, ...h.map(x => x.n), 1);
      $('dPlot').innerHTML = h.map(x => '<div class="col"><i class="' + (x.n >= g ? 'hit' : '') + '" style="height:' + x.n / mx * 100 + '%"></i></div>').join('')
        + '<div class="goal" style="bottom:' + g / mx * 100 + '%"></div>';
      $('dLab').innerHTML = h.map(x => '<span>' + 'SMTWTFS'[new Date(+x.d.slice(0, 4), +x.d.slice(4, 6) - 1, +x.d.slice(6)).getDay()] + '</span>').join('');
      $('dAvg').textContent = Math.round(h.reduce((a, x) => a + x.n, 0) / Math.max(1, h.length)).toLocaleString();
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
  S.start().then(refresh).catch(() => { $('dMsg').textContent = 'Allow the Physical activity permission so steps can be counted all day.'; });
})();
