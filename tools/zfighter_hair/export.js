// Runs hair.js in a fake window and prints the six chosen models as Java arrays.
const fs = require('fs');
const window = {}; const console2 = {log() {}};
new Function('window', 'console', fs.readFileSync('hair.js', 'utf8'))(window, console2);
const f = v => { const s = (Math.round(v * 10000) / 10000).toString(); return (s.includes('.') ? s : s + '.0') + 'F'; };
let out = '';
for (const spec of window.SPECS) {
  out += `\t\t// ${spec.name}\n\t\tnew Style(new float[][] {\n`;
  out += spec.cap.map(b => '\t\t\t{' + [b[0], b[1], b[2], b[3], b[4], b[5], b[6] || 0].map(f).join(', ') + '}').join(',\n') + '\n\t\t}, new float[][] {\n';
  out += spec.locks.map(l => '\t\t\t{' + [...l.p, ...l.r, ...l.seg.flat()].map(f).join(', ') + '}').join(',\n') + '\n\t\t}),\n';
}
fs.writeFileSync('styles.java.txt', out);
console.log(window.SPECS.map(s => s.name + ' ' + s.locks.length + ' locks ' + s.locks.reduce((a, l) => a + l.seg.length, 0) + ' segs').join('\n'));
