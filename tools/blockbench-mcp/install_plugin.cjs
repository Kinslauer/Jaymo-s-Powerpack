// Installs the MCP plugin into a running Blockbench through its DevTools port (Node 22+, no packages).
// env: BB_CDP (http://127.0.0.1:9222), BB_PLUGIN (path to mcp.js)
const cdp = process.env.BB_CDP || 'http://127.0.0.1:9222';
const plugin = process.env.BB_PLUGIN;

(async () => {
  const pages = await (await fetch(cdp + '/json')).json();
  const page = pages.find(p => p.type === 'page' && p.title.includes('Blockbench')) || pages[0];
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((ok, bad) => { ws.onopen = ok; ws.onerror = bad; });
  const expr = `(async () => {
    if (Plugins.all.some(p => p.id === 'mcp' && p.installed)) return 'already installed';
    await new Plugin('mcp').loadFromFile({ path: ${JSON.stringify(plugin)} }, false);
    return 'installed ' + (Plugins.all.find(p => p.id === 'mcp') || {}).version;
  })()`;
  ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: expr, awaitPromise: true, returnByValue: true } }));
  const msg = await new Promise(ok => { ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id === 1) ok(m); }; });
  ws.close();
  const r = msg.result || {};
  if (r.exceptionDetails) { console.error('plugin install failed:', r.exceptionDetails.text, r.exceptionDetails.exception?.description); process.exit(1); }
  console.log('MCP plugin:', r.result?.value);
})().catch(e => { console.error(e.message); process.exit(1); });
