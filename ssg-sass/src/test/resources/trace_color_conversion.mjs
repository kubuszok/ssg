// Trace script: captures every intermediate double in HSL -> OKLCH -> XYZ conversion.
// Focus on the exact point of divergence between Dart/JS and JVM.

const oklabToLms = [
  1.00000000000000020, 0.39633777737617490, 0.21580375730991360,
  0.99999999999999980, -0.10556134581565854, -0.06385417282581334,
  0.99999999999999990, -0.08948417752981180, -1.29148554801940940,
];

const lmsToXyzD65 = [
  1.22687987584592430, -0.55781499446021710, 0.28139104566596460,
  -0.04057574521480084, 1.11228680328031730, -0.07171105806551635,
  -0.07637293667466007, -0.42149333240224324, 1.58692401983678180,
];

const linearSrgbToLms = [
  0.41222146947076300, 0.53633253726173480, 0.05144599326750220,
  0.21190349581782520, 0.68069955064523420, 0.10739695353694050,
  0.08830245919005641, 0.28171883913612150, 0.62997870167382210,
];

const lmsToOklab = [
  0.21045426830931400, 0.79361777470230540, -0.00407204301161930,
  1.97799853243116840, -2.42859224204858000, 0.45059370961741100,
  0.02590404246554780, 0.78277171245752960, -0.80867575492307740,
];

function hueToRgb(m1, m2, hue) {
  if (hue < 0) hue += 1;
  if (hue > 1) hue -= 1;
  if (hue < 1 / 6) return m1 + (m2 - m1) * hue * 6;
  if (hue < 1 / 2) return m2;
  if (hue < 2 / 3) return m1 + (m2 - m1) * (2 / 3 - hue) * 6;
  return m1;
}

function srgbAndDisplayP3ToLinear(channel) {
  const abs = Math.abs(channel);
  return abs <= 0.04045
    ? channel / 12.92
    : Math.sign(channel) * Math.pow((abs + 0.055) / 1.055, 2.4);
}

function cubeRootPreservingSign(number) {
  return Math.pow(Math.abs(number), 1 / 3) * Math.sign(number);
}

// Use Float64Array to get bit-exact representations
function toBits(d) {
  const buf = new Float64Array(1);
  buf[0] = d;
  const view = new DataView(buf.buffer);
  return '0x' + view.getBigUint64(0).toString(16).padStart(16, '0');
}

// Forward: HSL -> OKLCH
const scaledHue = (20 / 360) % 1;
const scaledSat = 999999 / 100;
const scaledLight = 50 / 100;
const m2v = scaledLight * (scaledSat + 1);
const m1v = scaledLight * 2 - m2v;
const r = hueToRgb(m1v, m2v, scaledHue + 1/3);
const g = hueToRgb(m1v, m2v, scaledHue);
const b = hueToRgb(m1v, m2v, scaledHue - 1/3);

const lr = srgbAndDisplayP3ToLinear(r);
const lg = srgbAndDisplayP3ToLinear(g);
const lb = srgbAndDisplayP3ToLinear(b);

const lms_long  = linearSrgbToLms[0]*lr + linearSrgbToLms[1]*lg + linearSrgbToLms[2]*lb;
const lms_med   = linearSrgbToLms[3]*lr + linearSrgbToLms[4]*lg + linearSrgbToLms[5]*lb;
const lms_short = linearSrgbToLms[6]*lr + linearSrgbToLms[7]*lg + linearSrgbToLms[8]*lb;

const longS  = cubeRootPreservingSign(lms_long);
const medS   = cubeRootPreservingSign(lms_med);
const shortS = cubeRootPreservingSign(lms_short);

const okL = lmsToOklab[0]*longS + lmsToOklab[1]*medS + lmsToOklab[2]*shortS;
const oka = lmsToOklab[3]*longS + lmsToOklab[4]*medS + lmsToOklab[5]*shortS;
const okb = lmsToOklab[6]*longS + lmsToOklab[7]*medS + lmsToOklab[8]*shortS;

const chroma = Math.sqrt(oka*oka + okb*okb);
const hue_rad = Math.atan2(okb, oka);
let hue_deg = hue_rad * 180 / Math.PI;
if (hue_deg < 0) hue_deg += 360;

console.log("=== Forward OKLab ===");
console.log(`okL=${okL} ${toBits(okL)}`);
console.log(`oka=${oka} ${toBits(oka)}`);
console.log(`okb=${okb} ${toBits(okb)}`);
console.log(`chroma=${chroma} ${toBits(chroma)}`);
console.log(`hue_deg=${hue_deg} ${toBits(hue_deg)}`);

// Reverse: OKLCH -> OKLab
const hR = hue_deg * Math.PI / 180;
console.log(`\nhR=${hR} ${toBits(hR)}`);
console.log(`hue_rad_orig=${hue_rad} ${toBits(hue_rad)}`);
console.log(`hR == hue_rad: ${hR === hue_rad}`);
console.log(`hR bits diff: ${hR === hue_rad ? 'SAME' : 'DIFFERENT'}`);

const rev_a = chroma * Math.cos(hR);
const rev_b = chroma * Math.sin(hR);
console.log(`\nrev_a=${rev_a} ${toBits(rev_a)}`);
console.log(`orig_a=${oka} ${toBits(oka)}`);
console.log(`rev_a == orig_a: ${rev_a === oka}`);

console.log(`\nrev_b=${rev_b} ${toBits(rev_b)}`);
console.log(`orig_b=${okb} ${toBits(okb)}`);
console.log(`rev_b == orig_b: ${rev_b === okb}`);

// Now trace the OKLab -> LMS cube step with the EXACT values
console.log("\n=== OKLab -> LMS (with rev values) ===");
const m = oklabToLms;
const c0 = m[0]*okL + m[1]*rev_a + m[2]*rev_b;
const c1 = m[3]*okL + m[4]*rev_a + m[5]*rev_b;
const c2 = m[6]*okL + m[7]*rev_a + m[8]*rev_b;
console.log(`c0=${c0} ${toBits(c0)}`);
console.log(`c1=${c1} ${toBits(c1)}`);
console.log(`c2=${c2} ${toBits(c2)}`);

// Also compute with original a,b (no trig round-trip)
const c0_orig = m[0]*okL + m[1]*oka + m[2]*okb;
const c1_orig = m[3]*okL + m[4]*oka + m[5]*okb;
const c2_orig = m[6]*okL + m[7]*oka + m[8]*okb;
console.log(`\nc0_orig=${c0_orig} ${toBits(c0_orig)}`);
console.log(`c1_orig=${c1_orig} ${toBits(c1_orig)}`);
console.log(`c2_orig=${c2_orig} ${toBits(c2_orig)}`);

console.log(`\nc0 diff: ${c0 - c0_orig}`);
console.log(`c1 diff: ${c1 - c1_orig}`);
console.log(`c2 diff: ${c2 - c2_orig}`);

// Cube
const rev_long  = Math.pow(c0, 3) + 0.0;
const rev_med   = Math.pow(c1, 3) + 0.0;
const rev_short = Math.pow(c2, 3) + 0.0;
console.log(`\nrev_long=${rev_long} ${toBits(rev_long)}`);
console.log(`rev_med=${rev_med} ${toBits(rev_med)}`);
console.log(`rev_short=${rev_short} ${toBits(rev_short)}`);

// LMS -> XYZ
const xm = lmsToXyzD65;
const xyz_x = xm[0]*rev_long + xm[1]*rev_med + xm[2]*rev_short;
const xyz_y = xm[3]*rev_long + xm[4]*rev_med + xm[5]*rev_short;
const xyz_z = xm[6]*rev_long + xm[7]*rev_med + xm[8]*rev_short;

console.log(`\nFINAL XYZ (with trig round-trip): x=${xyz_x} y=${xyz_y} z=${xyz_z}`);

// Also with c*c*c instead of pow(c,3)
const rev_long2  = c0*c0*c0;
const rev_med2   = c1*c1*c1;
const rev_short2 = c2*c2*c2;
const xyz_x2 = xm[0]*rev_long2 + xm[1]*rev_med2 + xm[2]*rev_short2;
const xyz_y2 = xm[3]*rev_long2 + xm[4]*rev_med2 + xm[5]*rev_short2;
const xyz_z2 = xm[6]*rev_long2 + xm[7]*rev_med2 + xm[8]*rev_short2;
console.log(`FINAL XYZ (c*c*c):              x=${xyz_x2} y=${xyz_y2} z=${xyz_z2}`);

// Now the key check: what does dart-sass actually do?
// It uses math.pow(c, 3) not c*c*c. The Dart VM's pow(x, 3) for integer
// exponents uses repeated multiply (x*x*x), same as JS's Math.pow(x, 3).
// But our Scala code uses c*c*c directly.
// The difference between pow(c,3) and c*c*c might diverge on some platforms.

console.log(`\npow(c0,3) vs c0*c0*c0: diff=${Math.pow(c0,3) - c0*c0*c0}`);
console.log(`pow(c1,3) vs c1*c1*c1: diff=${Math.pow(c1,3) - c1*c1*c1}`);
console.log(`pow(c2,3) vs c2*c2*c2: diff=${Math.pow(c2,3) - c2*c2*c2}`);

console.log(`\nEXPECTED:  x=136956388.39988756 y=59264689.52803926 z=-623200798.6169877`);
