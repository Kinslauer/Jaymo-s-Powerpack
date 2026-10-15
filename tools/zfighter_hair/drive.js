const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 2000, height: 600 } });
  p.on('console', m => console.log('page:', m.text()));
  p.on('pageerror', e => console.log('ERR', e.message));
  await p.goto('file://' + path.resolve('render.html'));
  await p.addScriptTag({ path: 'node_modules/three/build/three.min.js' });
  await p.addScriptTag({ path: 'hair.js' });
  await p.addScriptTag({ path: 'scene.js' });
  const n = await p.evaluate(() => SPECS.length);
  const only = process.argv[2];
  const wind = process.argv[3]; if (wind) await p.evaluate(([w, c]) => SPECS.forEach(s => { s.wind = w; s.charge = c; s.time = 40; }), [Number(wind), Number(process.argv[4] || 0)]);
  for (let i = 0; i < n; i++) {
    if (only !== undefined && String(i) !== only) continue;
    const f = await p.evaluate(i => SPECS[i].form, i); const nm = await p.evaluate(i => SPECS[i].name, i); const v3 = nm === 'Wild 3' ? {dist: 118, ty: 4} : f === 3 ? {dist: 92, ty: 4} : {dist: 82, ty: 13}; const views = [{yaw: 0, ...v3}, {yaw: 35, ...v3}, {yaw: 90, ...v3}, {yaw: 160, ...v3}];
    await p.evaluate(([i, views]) => renderSpec(SPECS[i], views, [480, 560]), [i, views]);
    await p.locator('#c').screenshot({ path: `out/spec${i}${wind ? '-wind' + wind + '-' + (process.argv[4] || 0) : ''}.png` });
    console.log('rendered', i, await p.evaluate(i => SPECS[i].name, i));
  }
  await b.close();
})();
