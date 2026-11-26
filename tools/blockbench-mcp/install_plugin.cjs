// Installs Blockbench plugins (MCP, GeckoLib) into a running Blockbench through its DevTools port (Node 22+, no packages).
// env: BB_CDP (http://127.0.0.1:9222), BB_PLUGINS ("id=/path/file.js,id2=/path/two.js")
const cdp = process.env.BB_CDP || 'http://127.0.0.1:9222';
const plugins = (process.env.BB_PLUGINS || '').split(',').filter(Boolean).map(x => x.split('='));

(async () => {
  const pages = await (await fetch(cdp + '/json')).json();
  const page = pages.find(p => p.type === 'page' && p.title.includes('Blockbench')) || pages[0];
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((ok, bad) => { ws.onopen = ok; ws.onerror = bad; });
  const expr = `(async () => {
    const out = [];
    for (const [id, path] of ${JSON.stringify(plugins)}) {
      if (Plugins.all.some(p => p.id === id && p.installed)) { out.push(id + ' already installed'); continue; }
      await new Plugin(id).loadFromFile({ path }, false);
      out.push(id + ' ' + (Plugins.all.find(p => p.id === id) || {}).version);
    }
    return out.join(', ');
  })()`;
  ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: expr, awaitPromise: true, returnByValue: true } }));
  const msg = await new Promise(ok => { ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id === 1) ok(m); }; });
  ws.close();
  const r = msg.result || {};
  if (r.exceptionDetails) { console.error('plugin install failed:', r.exceptionDetails.text, r.exceptionDetails.exception?.description); process.exit(1); }
  console.log('plugins:', r.result?.value);
})().catch(e => { console.error(e.message); process.exit(1); });
