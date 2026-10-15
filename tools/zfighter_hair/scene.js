// Builds a head+body and a hair spec in MC model space, flipped into three.js space.
function hash(a, b, c) {
  let h = (a * 374761393 + b * 668265263 + c * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function rgb(c) { return [(c >> 16) & 255, (c >> 8) & 255, c & 255]; }
function tex(w, h, fn) {
  const cv = document.createElement('canvas');
  cv.width = Math.max(1, Math.round(w)); cv.height = Math.max(1, Math.round(h));
  const g = cv.getContext('2d'); const img = g.createImageData(cv.width, cv.height);
  for (let y = 0; y < cv.height; y++) for (let x = 0; x < cv.width; x++) {
    const c = fn(x, y, cv.width, cv.height); const i = (y * cv.width + x) * 4;
    img.data[i] = c[0]; img.data[i + 1] = c[1]; img.data[i + 2] = c[2]; img.data[i + 3] = 255;
  }
  g.putImageData(img, 0, 0);
  const t = new THREE.CanvasTexture(cv); t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter;
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
function shade(c, f) { return c.map(v => Math.max(0, Math.min(255, Math.round(v * f)))); }

// hair strands: streaks along the lock, a touch brighter toward the tip (t 0 root .. 1 tip)
function hairMats(color, w, h, d, t0, t1, seed) {
  const base = rgb(color);
  const side = (fw, fh, s) => new THREE.MeshLambertMaterial({map: tex(fw, fh, (x, y, W, H) => {
    const t = t1 + (t0 - t1) * (H <= 1 ? 0.5 : y / (H - 1));
    let f = 0.82 + 0.28 * t;
    f *= 0.9 + 0.18 * hash(x, s, seed);
    if (hash(x, y >> 1, seed + s) > 0.86) f *= 0.82;
    return shade(base, f);
  })});
  const cap = (fw, fh, s) => new THREE.MeshLambertMaterial({map: tex(fw, fh, (x, y) => shade(base, (0.85 + 0.25 * t1) * (0.9 + 0.15 * hash(x, y, seed + s))))});
  // three box face order: +x,-x,+y,-y,+z,-z
  return [side(d, h, 1), side(d, h, 2), cap(w, d, 3), cap(w, d, 4), side(w, h, 5), side(w, h, 6)];
}

function mcBox(x, y, z, w, h, d, inflate, mats) {
  const g = new THREE.BoxGeometry(w + inflate * 2, h + inflate * 2, d + inflate * 2);
  g.translate(x + w / 2, y + h / 2, z + d / 2);
  return new THREE.Mesh(g, mats);
}
function pose(obj, p, r) {
  obj.position.set(p[0], p[1], p[2]);
  obj.rotation.order = 'ZYX';
  obj.rotation.set(r[0], r[1], r[2]);
}

// the same wind as ZFighterHair.Layer: w 0-1 blows the locks back, c 0-1 is charging, t the time in ticks
function buildHair(spec) {
  const W = spec.wind || 0, C = spec.charge || 0, T = spec.time || 0;
  const flutter = 0.012 + 0.05 * W + 0.07 * C, speed = 0.2 + 0.35 * W + 0.55 * C;
  const head = new THREE.Group();
  let seed = 1;
  for (const b of spec.cap) head.add(mcBox(b[0], b[1], b[2], b[3], b[4], b[5], b[6] || 0, hairMats(spec.color, b[3], b[4], b[5], 0, 0.15, seed++)));
  spec.locks.forEach((l, li) => {
    let parent = head; const n = l.seg.length;
    const back = Math.cos(l.r[0]) >= 0 ? -1 : 1, blow = W * 0.1 + (back > 0 ? C * 0.04 : 0), phase = li * 1.7;
    l.seg.forEach((s, i) => {
      const grp = new THREE.Group();
      const wave = flutter * Math.sin(T * speed + phase + i * 0.7), sway = flutter * 0.6 * Math.cos(T * speed * 1.3 + phase + i * 0.5);
      if (i === 0) pose(grp, l.p, [l.r[0] + back * W * 0.04 + wave * 0.5, l.r[1], l.r[2] + sway * 0.5]);
      else pose(grp, [0, -l.seg[i - 1][2] + 0.5, 0], [s[3] * (1 - 0.5 * C) + back * blow + wave, 0, s[4] * (1 - 0.5 * C) + sway]);
      grp.add(mcBox(-s[0] / 2, -s[2], -s[1] / 2, s[0], s[2], s[1], 0, hairMats(spec.color, s[0], s[2], s[1], i / n, (i + 1) / n, seed++)));
      parent.add(grp); parent = grp;
    });
  });
  return head;
}

function skinFace(form) {
  const skin = [214, 160, 118];
  const face = tex(8, 8, (x, yy) => {
    const y = 7 - yy;
    if (y === 3 && (x === 1 || x === 2 || x === 5 || x === 6)) return form >= 3 ? shade(skin, 0.92) : [120, 80, 40];
    if (y === 4 && (x === 1 || x === 6)) return [245, 245, 245];
    if (y === 4 && (x === 2 || x === 5)) return form > 0 ? [40, 190, 170] : [40, 30, 25];
    if (y === 6 && x >= 3 && x <= 4) return shade(skin, 0.7);
    return shade(skin, 0.97 + 0.05 * hash(x, y, 9));
  });
  const plain = (w, h) => new THREE.MeshLambertMaterial({map: tex(w, h, (x, y) => shade(skin, 0.95 + 0.06 * hash(x, y, 3)))});
  return [plain(8, 8), plain(8, 8), plain(8, 8), plain(8, 8), plain(8, 8), new THREE.MeshLambertMaterial({map: face})];
}
function cloth(c, w, h) { return new THREE.MeshLambertMaterial({map: tex(w, h, (x, y) => shade(rgb(c), 0.92 + 0.1 * hash(x, y, 5)))}); }

function buildFigure(spec) {
  const root = new THREE.Group();
  root.rotation.x = Math.PI; // MC (y down, face -z) -> three (y up, face +z)
  const head = new THREE.Group();
  head.add(mcBox(-4, -8, -4, 8, 8, 8, 0, skinFace(spec.form)));
  head.add(buildHair(spec));
  root.add(head);
  const gi = 0xD9661F, under = 0x2F4C8F;
  const torso = mcBox(-4, 0, -2, 8, 12, 4, 0, cloth(gi, 8, 12)); root.add(torso);
  root.add(mcBox(-4, 0, -2.1, 8, 3, 0.2, 0, cloth(under, 8, 3)));
  root.add(mcBox(-4.05, 9, -2.05, 8.1, 1.2, 4.1, 0, cloth(under, 8, 1)));
  root.add(mcBox(4, 0, -2, 4, 12, 4, 0, cloth(under, 4, 12)));
  root.add(mcBox(-8, 0, -2, 4, 12, 4, 0, cloth(under, 4, 12)));
  root.add(mcBox(4, 0, -2, 4, 4, 4, 0.05, cloth(gi, 4, 4)));
  root.add(mcBox(-8, 0, -2, 4, 4, 4, 0.05, cloth(gi, 4, 4)));
  return root;
}

window.renderSpec = function (spec, views, size) {
  const W = size[0], H = size[1];
  const renderer = new THREE.WebGLRenderer({canvas: document.getElementById('c'), antialias: true, preserveDrawingBuffer: true});
  renderer.setSize(W * views.length, H); renderer.setScissorTest(true);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  const scene = new THREE.Scene();
  scene.add(new THREE.AmbientLight(0xffffff, 1.35));
  const l0 = new THREE.DirectionalLight(0xffffff, 1.5); l0.position.set(0.3, 1, 0.8); scene.add(l0);
  const l1 = new THREE.DirectionalLight(0xffffff, 0.6); l1.position.set(-0.4, 0.6, -0.8); scene.add(l1);
  const fig = buildFigure(spec); scene.add(fig);
  views.forEach((v, i) => {
    const cam = new THREE.PerspectiveCamera(v.fov || 26, W / H, 1, 400);
    const a = v.yaw * Math.PI / 180, e = (v.pitch || 8) * Math.PI / 180, dist = v.dist || 70;
    const tgt = new THREE.Vector3(0, v.ty ?? 10, 0);
    cam.position.set(tgt.x + Math.sin(a) * Math.cos(e) * dist, tgt.y + Math.sin(e) * dist, tgt.z + Math.cos(a) * Math.cos(e) * dist);
    cam.lookAt(tgt);
    renderer.setViewport(i * W, 0, W, H); renderer.setScissor(i * W, 0, W, H);
    renderer.setClearColor(new THREE.Color(v.bg || 0x30343c), 1);
    renderer.render(scene, cam);
  });
  return true;
};
