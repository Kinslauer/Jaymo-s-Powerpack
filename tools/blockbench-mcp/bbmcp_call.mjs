// usage: node call.mjs list | node call.mjs <tool> '<json args>' [<tool> '<json>' ...]
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
const url = process.env.BB_MCP_URL || 'http://127.0.0.1:3000/bb-mcp';
const c = new Client({ name: 'bb-check', version: '1.0.0' });
await c.connect(new StreamableHTTPClientTransport(new URL(url)));
const a = process.argv.slice(2);
if (a[0] === 'list') { const t = await c.listTools(); console.log(t.tools.map(x => x.name).join(' ')); }
else for (let i = 0; i < a.length; i += 2) {
  const r = await c.callTool({ name: a[i], arguments: JSON.parse(a[i + 1] || '{}') });
  console.log('## ' + a[i] + (r.isError ? ' ERROR' : ''));
  for (const x of r.content) {
    if (x.type === 'image' && process.env.BB_IMG) { (await import('fs')).writeFileSync(process.env.BB_IMG, Buffer.from(x.data, 'base64')); console.log('[image saved to ' + process.env.BB_IMG + ']'); }
    else console.log(x.type === 'text' ? x.text.slice(0, 1500) : `[${x.type} ${x.mimeType || ''}]`);
  }
}
await c.close(); process.exit(0);
