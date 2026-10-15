// Hair specs in Minecraft model space (head part: y down, top of head y=-8, face at z=-4).
// lock: p pivot, r [xRot,yRot,zRot] (ZYX like ModelPart), seg [[w,d,len,bendX,bendZ],...]
// Each segment: addBox(-w/2,-len,-d/2,w,len,d); the next hangs at (0,-len+0.5,0) turned by its bend.
// +xRot leans an upright lock forward (toward the face), +zRot leans it toward +x.

function sym(l) {
  const m = {p: [-l.p[0], l.p[1], l.p[2]], r: [l.r[0], -l.r[1], -l.r[2]], seg: l.seg.map(s => [s[0], s[1], s[2], s[3], -s[4]])};
  return [l, m];
}
const PI = Math.PI;

// a tapering flame lock: base width/depth, segment length, segments, bend per joint
function flame(w, d, len, n, bx, bz, tipLen) {
  const seg = [];
  for (let i = 0; i < n; i++) {
    const t = n === 1 ? 1 : i / (n - 1);
    const ww = Math.max(1, Math.round(w - (w - 1) * t));
    const dd = Math.max(1, Math.round(d - (d - 1) * t));
    seg.push([ww, dd, i === n - 1 && tipLen ? tipLen : len, i === 0 ? 0 : bx, i === 0 ? 0 : bz]);
  }
  return seg;
}

// a triangle-ish spike: one segment per width step, widths falling to 1, total length L, total bend spread along it
function spike(w, d, L, bx, bz, hold = 0) {
  const n = Math.max(2, w) + hold;
  const seg = [];
  for (let i = 0; i < n; i++) {
    const k = Math.max(0, i - hold), m = n - 1 - hold;
    const ww = Math.max(1, w - Math.round(k * (w - 1) / m));
    const dd = Math.max(1, d - Math.round(k * (d - 1) / m));
    seg.push([ww, dd, Math.round(L / n * 2) / 2, i === 0 ? 0 : bx / (n - 1), i === 0 ? 0 : bz / (n - 1)]);
  }
  return seg;
}
const L = (x, y, z, rx, rz, w, d, len, bx = 0, bz = 0, hold = 0) => ({p: [x, y, z], r: [rx, 0, rz], seg: spike(w, d, len, bx, bz, hold)});
const S = (...a) => sym(L(...a));

const CAP = [
  [-4, -8, -4, 8, 2, 8, 0.6],
  [-4, -6, -1, 8, 2, 5, 0.6],
  [-4, -4, 1.5, 8, 3, 2.5, 0.6],
  [-4, -6, -3, 1, 3, 2, 0.5],
  [3, -6, -3, 1, 3, 2, 0.5],
];
// the mass the spikes grow out of: hair standing up off the crown, so there are no gaps between their roots
const MASS = [[-4, -11, -3.5, 8, 3.5, 7.5, 0.5]];

function spec(name, form, color, cap, locks) { return {name, form, color, cap, locks: locks.flat()}; }
const COL = {1: 0xFFD54A, 2: 0xFFE873, 3: 0xFFC020};

// A: upright flame. g = how much taller (Super 2), st = how much straighter
function upright(form, g, st) {
  return [
    L(0, -10, -2.2, 0.35 * st, 0, 5, 4, 8 + g, -0.5 * st),
    S(2.6, -10, -2, 0.25 * st, 0.4, 4, 3, 7 + g, -0.4 * st, 0.1),
    L(0, -10.5, 0.5, -0.2, 0, 6, 5, 11 + g * 1.3, -0.15 * st),
    S(2.8, -10.5, 0.8, -0.15, 0.65, 5, 4, 9 + g, -0.1, 0.15 * st),
    L(0, -10, 3, -0.75, 0, 5, 4, 9 + g, -0.15 * st),
    S(2.5, -10, 2.8, -0.65, 0.55, 5, 4, 8 + g, -0.1, 0.12),
    S(4.3, -7, -0.5, 0, 1.25, 4, 4, 6 + g / 2, 0, -0.35 * st),
    L(0, -5.5, 4, -1.35, 0, 5, 3, 6 + g / 2, 0.35 * st),
    S(2.6, -3.5, 3.8, -1.85, 0.45, 4, 3, 5, 0.3),
  ];
}
// B: swept back, every spike raked back like it is in a gale
function swept(form, g, st) {
  return [
    L(0, -9.5, -2.8, -0.2, 0, 5, 4, 8 + g, -0.7 * st),
    S(2.5, -9.5, -2.5, -0.3, 0.35, 4, 3, 8 + g, -0.6 * st, 0.1),
    L(0, -10.5, 0, -0.6, 0, 6, 5, 11 + g * 1.3, -0.5 * st),
    S(2.8, -10.5, 0.3, -0.6, 0.6, 5, 4, 10 + g, -0.4 * st, 0.15),
    L(0, -10, 2.8, -1.0, 0, 5, 4, 10 + g, -0.3 * st),
    S(2.5, -10, 2.8, -1.0, 0.5, 5, 4, 9 + g, -0.3 * st, 0.1),
    S(4.3, -7, 0.5, -0.5, 1.2, 4, 4, 6 + g / 2, 0, -0.25),
    L(0, -5.5, 4, -1.5, 0, 5, 3, 7 + g / 2, 0.2),
    S(2.6, -3.5, 3.8, -1.9, 0.45, 4, 3, 5, 0.25),
  ];
}
const BANG1 = [L(-0.5, -7.4, -4.5, PI - 0.15, -0.12, 3, 1, 5, -0.4)];
const BANGS1B = [L(-1.8, -7.4, -4.5, PI - 0.2, -0.25, 2, 1, 4, -0.4), L(1.2, -7.4, -4.5, PI - 0.25, 0.3, 2, 1, 3.5, -0.4)];
const BANG2 = [{p: [-1, -7.4, -4.5], r: [PI - 0.1, 0, -0.08], seg: [[2, 1, 2, 0, 0], [2, 1, 2, -0.2, 0.15], [1, 1, 2, -0.3, 0.2], [1, 1, 1.5, -0.3, 0.2]]}];

// Super 3: brow bare and high, everything swept back into a mane that flares out down to the waist
const CAP3 = [
  [-4, -8, -2, 8, 2, 6, 0.6],
  [-4, -6, -1, 8, 2, 5, 0.6],
  [-4, -4, 1.5, 8, 3, 2.5, 0.6],
  [-4, -7, -2, 1, 4, 1.5, 0.5],
  [3, -7, -2, 1, 4, 1.5, 0.5],
];
const MASS3 = [[-4, -12, -2.5, 8, 4.5, 7, 0.5]];
function mane(spread, len) {
  return [
    L(0, -7, 4, PI + 0.14, 0, 6, 4, len, -0.12, 0, 3),
    S(2.2, -7, 3.9, PI + 0.16, -0.14 * spread, 5, 4, len - 1, -0.12, -0.12, 3),
    S(3.9, -7, 2.6, PI + 0.14, -0.3 * spread, 5, 4, len - 3, -0.1, -0.15, 3),
    S(4.3, -7, 0.3, PI + 0.08, -0.36 * spread, 4, 3, len - 6, -0.05, -0.15, 2),
    S(4.4, -6.5, -1.8, PI - 0.05, -0.18 * spread, 3, 3, 12, 0.1, -0.08, 2),
    L(0, -3, 4.6, PI + 0.32, 0, 6, 3, len - 6, -0.1, 0, 2),
    S(2.6, -3, 4.4, PI + 0.34, -0.2 * spread, 5, 3, len - 8, -0.1, -0.1, 2),
  ];
}
function s3crown(up) {
  return [
    L(0, -10, -2.6, 0.15 + up / 2, 0, 6, 4, 11, -0.75, 0, 1),
    S(2.6, -10, -2.4, 0.1 + up / 2, 0.4, 5, 4, 10, -0.7, 0.1, 1),
    L(0, -10.5, 0, -1.0 + up, 0, 6, 5, 12, -0.35),
    S(2.8, -10.3, 0.5, -1.0 + up, 0.55, 5, 4, 11, -0.3, 0.1),
    S(4.2, -8.5, 0, -0.9 + up, 1.1, 4, 4, 8, -0.2, -0.1),
    L(0, -10, 2.8, -1.45 + up, 0, 6, 5, 11, -0.2),
  ];
}

// ---- pass 2 ----
// Swept: every lock raked back and kept close together
function sweptTight(g, st) {
  return [
    L(0, -9.5, -2.8, -0.25, 0, 5, 4, 9 + g, -0.6 * st),
    S(2.2, -9.5, -2.4, -0.35, 0.18, 4, 4, 9 + g, -0.55 * st, 0.05),
    L(0, -10.5, 0, -0.7, 0, 6, 5, 12 + g * 1.3, -0.4 * st),
    S(2.4, -10.3, 0.4, -0.7, 0.3, 5, 4, 11 + g, -0.35 * st, 0.05),
    L(0, -10, 2.8, -1.1, 0, 6, 5, 11 + g, -0.25 * st),
    S(2.3, -10, 2.8, -1.05, 0.25, 5, 4, 10 + g, -0.25 * st, 0.04),
    S(3.9, -7.5, 0.5, -0.8, 0.55, 4, 4, 7 + g / 2, -0.2, 0),
    L(0, -5.5, 4, -1.55, 0, 5, 4, 8 + g / 2, 0.15),
    S(2.4, -4, 3.9, -1.75, 0.25, 4, 3, 6, 0.15),
  ];
}
function s3tight() {
  return [
    L(0, -10, -2.6, -0.1, 0, 6, 4, 11, -0.9, 0, 1),
    S(2.4, -10, -2.4, -0.15, 0.2, 5, 4, 10, -0.85, 0.04, 1),
    L(0, -10.5, 0, -1.1, 0, 6, 5, 12, -0.3),
    S(2.5, -10.3, 0.5, -1.1, 0.3, 5, 4, 11, -0.25, 0.04),
    S(3.9, -8.5, 0, -1.0, 0.6, 4, 4, 9, -0.2, 0),
    L(0, -10, 2.8, -1.5, 0, 6, 5, 11, -0.15),
  ];
}

// Wild (after the reference sheet): long locks curling up and off to one side, Super 2 sharper and busier,
// Super 3 a huge mane flaring out sideways and back
function wild1(g, st) {
  return [
    L(0, -9.5, -2.6, 0.2, 0.1, 5, 4, 10 + g, -0.7 * st, 0.35),
    L(-2.4, -9.5, -2.2, 0.15, -0.35, 4, 4, 9 + g, -0.6 * st, 0.5),
    L(2.4, -9.5, -2.2, 0.1, 0.5, 4, 4, 10 + g, -0.4 * st, 0.4),
    L(0, -10.5, 0.4, -0.35, 0.15, 6, 5, 14 + g * 1.3, -0.3 * st, 0.45),
    L(-2.7, -10.3, 0.6, -0.3, -0.55, 5, 4, 11 + g, -0.2 * st, 0.55),
    L(2.7, -10.3, 0.6, -0.3, 0.75, 5, 4, 12 + g, -0.15 * st, 0.3),
    L(0, -10, 2.9, -0.85, 0.1, 6, 5, 12 + g, -0.2 * st, 0.3),
    S(2.5, -10, 2.8, -0.8, 0.6, 5, 4, 10 + g, -0.1, 0.2),
    L(-4.3, -7, 0, -0.1, -1.15, 4, 4, 8 + g / 2, 0, 0.5),
    L(4.3, -7, 0, -0.1, 1.2, 4, 4, 9 + g / 2, 0, 0.2),
    L(0, -5.5, 4, -1.3, 0.2, 5, 4, 8 + g / 2, 0.3, 0.2),
    S(2.6, -3.5, 3.8, -1.8, 0.5, 4, 3, 6, 0.3),
  ];
}
function wild2extra() {
  return [
    L(-1.2, -10.5, -1, -0.05, -0.15, 4, 3, 13, -0.15, 0.25),
    L(1.4, -10.5, 1.6, -0.5, 0.35, 4, 3, 14, -0.1, 0.3),
    L(-1.5, -10.5, 2, -0.6, -0.3, 4, 3, 13, -0.1, 0.35),
    S(4.2, -8.5, 2, -0.6, 1.0, 3, 3, 9, 0, -0.1),
  ];
}
const BANGS_W1 = [L(-2, -7.4, -4.5, PI - 0.2, -0.3, 2, 1, 4.5, -0.4), L(0, -7.4, -4.5, PI - 0.15, 0.05, 3, 1, 5, -0.4),
  L(2.2, -7.4, -4.5, PI - 0.25, 0.35, 2, 1, 4, -0.4)];
function wild3() {
  return [
    // crown: big locks thrown up and back
    L(0, -10, -2.6, 0.1, 0, 6, 4, 13, -0.9, 0.15, 1),
    S(2.5, -10, -2.4, 0.05, 0.45, 5, 4, 12, -0.8, 0.15, 1),
    L(0, -11, 0.5, -0.8, 0, 7, 5, 15, -0.35),
    S(2.8, -10.8, 0.8, -0.8, 0.7, 6, 5, 14, -0.3, 0.15),
    S(4.3, -8.5, 0, -0.6, 1.3, 5, 4, 12, -0.2, -0.2),
    L(0, -10.5, 3, -1.3, 0, 7, 5, 14, -0.15),
    // mane: long, wide, billowing back, the outer locks thrown out to the sides like flame
    L(0, -7, 4, PI + 0.5, 0, 7, 5, 30, -0.3, 0, 3),
    S(2.4, -7, 3.9, PI + 0.5, -0.4, 6, 5, 28, -0.3, -0.25, 3),
    S(4, -7, 2.6, PI + 0.4, -0.8, 6, 4, 25, -0.2, -0.35, 3),
    S(4.3, -7.5, 0.5, PI + 0.25, -1.1, 5, 4, 20, -0.1, -0.4, 2),
    S(4.3, -9, 1.5, -1.2, 1.5, 5, 4, 17, 0.2, 0.3),
    S(4.4, -6.5, -1.8, PI - 0.05, -0.35, 3, 3, 14, 0.1, -0.15, 2),
    L(0, -3, 4.6, PI + 0.75, 0, 7, 4, 24, -0.25, 0, 2),
    S(2.8, -3, 4.4, PI + 0.75, -0.5, 6, 4, 22, -0.2, -0.3, 2),
    L(0, -10, 3.5, -1.5, 0, 7, 5, 20, 0.35),
    S(2.6, -9.5, 3.4, -1.45, 0.55, 6, 4, 18, 0.3, 0.2),
  ];
}
const MASS_W3 = [[-4.5, -12.5, -2.5, 9, 5, 7.5, 0.5]];

const SW1 = spec('Swept 1', 1, COL[1], [...CAP, ...MASS], [...sweptTight(0, 1), ...BANGS1B]);
const SW2 = spec('Swept 2', 2, COL[2], [...CAP, ...MASS], [...sweptTight(3, 0.6), ...BANG2]);
const SW3 = spec('Swept 3', 3, COL[3], [...CAP3, ...MASS3], [...s3tight(), ...mane(0.6, 22)]);
const WI1 = spec('Wild 1', 1, COL[1], [...CAP, ...MASS], [...wild1(0, 1), ...BANGS_W1]);
const WI2 = spec('Wild 2', 2, COL[2], [...CAP, ...MASS], [...wild1(3, 0.55), ...wild2extra(), ...BANG2]);
const WI3 = spec('Wild 3', 3, COL[3], [...CAP3, ...MASS_W3], [...wild3()]);

console.log('hair loaded');
window.SPECS = [SW1, SW2, SW3, WI1, WI2, WI3];
