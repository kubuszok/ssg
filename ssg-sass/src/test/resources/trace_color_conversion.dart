// Trace script: captures every intermediate double in HSL -> OKLCH conversion.
// Uses the EXACT constants and algorithms from dart-sass.

import 'dart:math' as math;

// --- Constants from dart-sass conversions.dart ---

final linearSrgbToLms = [
  00.41222146947076300, 00.53633253726173480, 00.05144599326750220,
  00.21190349581782520, 00.68069955064523420, 00.10739695353694050,
  00.08830245919005641, 00.28171883913612150, 00.62997870167382210,
];

final lmsToOklab = [
  00.21045426830931400, 00.79361777470230540, -0.00407204301161930,
  01.97799853243116840, -2.42859224204858000, 00.45059370961741100,
  00.02590404246554780, 00.78277171245752960, -0.80867575492307740,
];

// --- Algorithm from utils.dart ---

double hueToRgb(double m1, double m2, double hue) {
  if (hue < 0) hue += 1;
  if (hue > 1) hue -= 1;

  if (hue < 1 / 6) return m1 + (m2 - m1) * hue * 6;
  if (hue < 1 / 2) return m2;
  if (hue < 2 / 3) return m1 + (m2 - m1) * (2 / 3 - hue) * 6;
  return m1;
}

double srgbAndDisplayP3ToLinear(double channel) {
  var abs = channel.abs();
  return abs <= 0.04045
      ? channel / 12.92
      : channel.sign * math.pow((abs + 0.055) / 1.055, 2.4);
}

double cubeRootPreservingSign(double number) =>
    math.pow(number.abs(), 1 / 3) * number.sign;

void main() {
  // Input: hsl(20deg 999999% 50%)
  double hue = 20;
  double saturation = 999999;
  double lightness = 50;

  print("=== HSL -> sRGB ===");
  print("hsl_input: hue=$hue saturation=$saturation lightness=$lightness");

  // Step 1: HSL -> sRGB
  var scaledHue = ((hue) / 360) % 1;
  var scaledSaturation = (saturation) / 100;
  var scaledLightness = (lightness) / 100;

  print("hsl_scaled: hue=$scaledHue sat=$scaledSaturation light=$scaledLightness");

  var m2 = scaledLightness <= 0.5
      ? scaledLightness * (scaledSaturation + 1)
      : scaledLightness + scaledSaturation - scaledLightness * scaledSaturation;
  var m1 = scaledLightness * 2 - m2;

  print("hsl_m: m1=$m1 m2=$m2");

  var r = hueToRgb(m1, m2, scaledHue + 1 / 3);
  var g = hueToRgb(m1, m2, scaledHue);
  var b = hueToRgb(m1, m2, scaledHue - 1 / 3);

  print("hsl_to_rgb: r=$r g=$g b=$b");

  // Step 2: sRGB -> linear sRGB
  print("\n=== sRGB -> linear sRGB ===");
  var lr = srgbAndDisplayP3ToLinear(r);
  var lg = srgbAndDisplayP3ToLinear(g);
  var lb = srgbAndDisplayP3ToLinear(b);

  // Print intermediate details for each channel
  print("linear_r_input: abs=${r.abs()} threshold_check=${r.abs() <= 0.04045}");
  if (r.abs() > 0.04045) {
    var base_r = (r.abs() + 0.055) / 1.055;
    print("linear_r_base: (${r.abs()} + 0.055) / 1.055 = $base_r");
    var pow_r = math.pow(base_r, 2.4);
    print("linear_r_pow: pow($base_r, 2.4) = $pow_r");
  }
  print("linear_g_input: abs=${g.abs()} threshold_check=${g.abs() <= 0.04045}");
  if (g.abs() > 0.04045) {
    var base_g = (g.abs() + 0.055) / 1.055;
    print("linear_g_base: (${g.abs()} + 0.055) / 1.055 = $base_g");
    var pow_g = math.pow(base_g, 2.4);
    print("linear_g_pow: pow($base_g, 2.4) = $pow_g");
  }
  print("linear_b_input: abs=${b.abs()} threshold_check=${b.abs() <= 0.04045}");
  if (b.abs() > 0.04045) {
    var base_b = (b.abs() + 0.055) / 1.055;
    print("linear_b_base: (${b.abs()} + 0.055) / 1.055 = $base_b");
    var pow_b = math.pow(base_b, 2.4);
    print("linear_b_pow: pow($base_b, 2.4) = $pow_b");
  }
  print("linear: r=$lr g=$lg b=$lb");

  // Step 3: linear sRGB -> LMS (matrix multiply)
  print("\n=== linear sRGB -> LMS ===");
  var matrix = linearSrgbToLms;

  // Long channel
  var long_term0 = matrix[0] * lr;
  var long_term1 = matrix[1] * lg;
  var long_term2 = matrix[2] * lb;
  var long_sum01 = long_term0 + long_term1;
  var long_val = long_sum01 + long_term2;
  print("lms_long: term0=${long_term0} term1=${long_term1} term2=${long_term2} sum01=${long_sum01} val=${long_val}");

  // Medium channel
  var med_term0 = matrix[3] * lr;
  var med_term1 = matrix[4] * lg;
  var med_term2 = matrix[5] * lb;
  var med_sum01 = med_term0 + med_term1;
  var med_val = med_sum01 + med_term2;
  print("lms_medium: term0=${med_term0} term1=${med_term1} term2=${med_term2} sum01=${med_sum01} val=${med_val}");

  // Short channel
  var short_term0 = matrix[6] * lr;
  var short_term1 = matrix[7] * lg;
  var short_term2 = matrix[8] * lb;
  var short_sum01 = short_term0 + short_term1;
  var short_val = short_sum01 + short_term2;
  print("lms_short: term0=${short_term0} term1=${short_term1} term2=${short_term2} sum01=${short_sum01} val=${short_val}");

  // Step 4: cube root preserving sign
  print("\n=== LMS -> cube root ===");
  var longScaled = cubeRootPreservingSign(long_val);
  var mediumScaled = cubeRootPreservingSign(med_val);
  var shortScaled = cubeRootPreservingSign(short_val);

  print("cbrt_long: abs=${long_val.abs()} pow(abs, 1/3)=${math.pow(long_val.abs(), 1/3)} sign=${long_val.sign} result=$longScaled");
  print("cbrt_medium: abs=${med_val.abs()} pow(abs, 1/3)=${math.pow(med_val.abs(), 1/3)} sign=${med_val.sign} result=$mediumScaled");
  print("cbrt_short: abs=${short_val.abs()} pow(abs, 1/3)=${math.pow(short_val.abs(), 1/3)} sign=${short_val.sign} result=$shortScaled");

  // Step 5: LMS (cube-rooted) -> OKLab (matrix multiply)
  print("\n=== cube-rooted LMS -> OKLab ===");
  var m = lmsToOklab;

  var L_term0 = m[0] * longScaled;
  var L_term1 = m[1] * mediumScaled;
  var L_term2 = m[2] * shortScaled;
  var L_sum01 = L_term0 + L_term1;
  var L = L_sum01 + L_term2;
  print("oklab_L: term0=${L_term0} term1=${L_term1} term2=${L_term2} sum01=${L_sum01} val=${L}");

  var a_term0 = m[3] * longScaled;
  var a_term1 = m[4] * mediumScaled;
  var a_term2 = m[5] * shortScaled;
  var a_sum01 = a_term0 + a_term1;
  var a = a_sum01 + a_term2;
  print("oklab_a: term0=${a_term0} term1=${a_term1} term2=${a_term2} sum01=${a_sum01} val=${a}");

  var b_term0 = m[6] * longScaled;
  var b_term1 = m[7] * mediumScaled;
  var b_term2 = m[8] * shortScaled;
  var b_sum01 = b_term0 + b_term1;
  var b_val = b_sum01 + b_term2;
  print("oklab_b: term0=${b_term0} term1=${b_term1} term2=${b_term2} sum01=${b_sum01} val=${b_val}");

  // Step 6: OKLab -> OKLCH
  print("\n=== OKLab -> OKLCH ===");
  var chroma = math.sqrt(a * a + b_val * b_val);
  var hue_rad = math.atan2(b_val, a);
  var hue_deg = hue_rad * 180 / math.pi;
  if (hue_deg < 0) hue_deg += 360;

  print("oklch_chroma_sq: a*a=${a*a} b*b=${b_val*b_val} sum=${a*a + b_val*b_val}");
  print("oklch: L=$L C=$chroma H=$hue_deg");
}
