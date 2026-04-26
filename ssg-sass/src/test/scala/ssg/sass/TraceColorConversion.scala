// Trace script: testing different trig implementations to match Dart/JS behavior.

import java.lang.foreign.{ FunctionDescriptor, Linker, ValueLayout }

object NativeC {
  private val linker = Linker.nativeLinker()
  private val lookup = linker.defaultLookup()

  private def bind1(name: String) = {
    val sym = lookup.find(name).orElseThrow(() => new RuntimeException(s"$name not found"))
    linker.downcallHandle(sym, FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE))
  }

  private def bind2(name: String) = {
    val sym = lookup.find(name).orElseThrow(() => new RuntimeException(s"$name not found"))
    linker.downcallHandle(sym, FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE))
  }

  private val cosH   = bind1("cos")
  private val sinH   = bind1("sin")
  private val atan2H = bind2("atan2")
  private val sqrtH  = bind1("sqrt")
  private val powH   = bind2("pow")

  def cos(x:   Double):            Double = cosH.invoke(x).asInstanceOf[Double]
  def sin(x:   Double):            Double = sinH.invoke(x).asInstanceOf[Double]
  def atan2(y: Double, x: Double): Double = atan2H.invoke(y, x).asInstanceOf[Double]
  def sqrt(x:  Double):            Double = sqrtH.invoke(x).asInstanceOf[Double]
  def pow(b:   Double, e: Double): Double = powH.invoke(b, e).asInstanceOf[Double]
}

def toBits(d: Double): String = f"0x${java.lang.Double.doubleToRawLongBits(d)}%016x"

val oklabToLms: Array[Double] = Array(
  1.00000000000000020, 0.39633777737617490, 0.21580375730991360, 0.99999999999999980, -0.10556134581565854, -0.06385417282581334, 0.99999999999999990, -0.08948417752981180, -1.29148554801940940
)

val lmsToXyzD65: Array[Double] = Array(
  1.22687987584592430, -0.55781499446021710, 0.28139104566596460, -0.04057574521480084, 1.11228680328031730, -0.07171105806551635, -0.07637293667466007, -0.42149333240224324, 1.58692401983678180
)

val linearSrgbToLms: Array[Double] = Array(
  0.41222146947076300, 0.53633253726173480, 0.05144599326750220, 0.21190349581782520, 0.68069955064523420, 0.10739695353694050, 0.08830245919005641, 0.28171883913612150, 0.62997870167382210
)

val lmsToOklab: Array[Double] = Array(
  0.21045426830931400, 0.79361777470230540, -0.00407204301161930, 1.97799853243116840, -2.42859224204858000, 0.45059370961741100, 0.02590404246554780, 0.78277171245752960, -0.80867575492307740
)

def hueToRgb(m1: Double, m2: Double, hue0: Double): Double = {
  var hue = hue0
  if (hue < 0) hue += 1
  if (hue > 1) hue -= 1
  if (hue < 1.0 / 6) m1 + (m2 - m1) * hue * 6
  else if (hue < 1.0 / 2) m2
  else if (hue < 2.0 / 3) m1 + (m2 - m1) * (2.0 / 3 - hue) * 6
  else m1
}

def srgbAndDisplayP3ToLinear(channel: Double): Double = {
  val abs = math.abs(channel)
  if (abs <= 0.04045) channel / 12.92
  else math.signum(channel) * NativeC.pow((abs + 0.055) / 1.055, 2.4)
}

def cubeRootPreservingSign(number: Double): Double =
  NativeC.pow(math.abs(number), 1.0 / 3) * math.signum(number)

@main def traceColorConversion(): Unit = {
  // Forward: HSL -> OKLCH
  val scaledHue   = (20.0 / 360) % 1
  val scaledSat   = 999999.0 / 100
  val scaledLight = 50.0 / 100
  val m2v         = scaledLight * (scaledSat + 1)
  val m1v         = scaledLight * 2 - m2v
  val r           = hueToRgb(m1v, m2v, scaledHue + 1.0 / 3)
  val g           = hueToRgb(m1v, m2v, scaledHue)
  val b           = hueToRgb(m1v, m2v, scaledHue - 1.0 / 3)

  val lr = srgbAndDisplayP3ToLinear(r)
  val lg = srgbAndDisplayP3ToLinear(g)
  val lb = srgbAndDisplayP3ToLinear(b)

  val lms_long  = linearSrgbToLms(0) * lr + linearSrgbToLms(1) * lg + linearSrgbToLms(2) * lb
  val lms_med   = linearSrgbToLms(3) * lr + linearSrgbToLms(4) * lg + linearSrgbToLms(5) * lb
  val lms_short = linearSrgbToLms(6) * lr + linearSrgbToLms(7) * lg + linearSrgbToLms(8) * lb

  val longS  = cubeRootPreservingSign(lms_long)
  val medS   = cubeRootPreservingSign(lms_med)
  val shortS = cubeRootPreservingSign(lms_short)

  val okL = lmsToOklab(0) * longS + lmsToOklab(1) * medS + lmsToOklab(2) * shortS
  val oka = lmsToOklab(3) * longS + lmsToOklab(4) * medS + lmsToOklab(5) * shortS
  val okb = lmsToOklab(6) * longS + lmsToOklab(7) * medS + lmsToOklab(8) * shortS

  println("=== Trig comparison ===")
  println(s"oka=$oka ${toBits(oka)}")
  println(s"okb=$okb ${toBits(okb)}")

  // Test 1: Java Math trig (fdlibm, guaranteed correctly-rounded)
  val chroma_java  = math.sqrt(oka * oka + okb * okb)
  val hue_rad_java = math.atan2(okb, oka)
  val hue_deg_java = hue_rad_java * 180 / math.Pi
  val hR_java      = hue_deg_java * math.Pi / 180
  val rev_a_java   = chroma_java * math.cos(hR_java)
  val rev_b_java   = chroma_java * math.sin(hR_java)
  println(s"\nJava Math:")
  println(s"  chroma=$chroma_java ${toBits(chroma_java)}")
  println(s"  hue_rad=$hue_rad_java ${toBits(hue_rad_java)}")
  println(s"  hue_deg=$hue_deg_java")
  println(s"  hR=$hR_java ${toBits(hR_java)}")
  println(s"  hR==hue_rad: ${hR_java == hue_rad_java}")
  println(s"  rev_a=$rev_a_java ${toBits(rev_a_java)} (orig ${toBits(oka)})")
  println(s"  rev_b=$rev_b_java ${toBits(rev_b_java)} (orig ${toBits(okb)})")

  // Test 2: Native C trig
  val chroma_c  = NativeC.sqrt(oka * oka + okb * okb)
  val hue_rad_c = NativeC.atan2(okb, oka)
  val hue_deg_c = hue_rad_c * 180 / math.Pi
  val hR_c      = hue_deg_c * math.Pi / 180
  val rev_a_c   = chroma_c * NativeC.cos(hR_c)
  val rev_b_c   = chroma_c * NativeC.sin(hR_c)
  println(s"\nNative C:")
  println(s"  chroma=$chroma_c ${toBits(chroma_c)}")
  println(s"  hue_rad=$hue_rad_c ${toBits(hue_rad_c)}")
  println(s"  hue_deg=$hue_deg_c")
  println(s"  hR=$hR_c ${toBits(hR_c)}")
  println(s"  hR==hue_rad: ${hR_c == hue_rad_c}")
  println(s"  rev_a=$rev_a_c ${toBits(rev_a_c)} (orig ${toBits(oka)})")
  println(s"  rev_b=$rev_b_c ${toBits(rev_b_c)} (orig ${toBits(okb)})")

  // Test 3: StrictMath (fdlibm, same as Java Math for trig)
  val chroma_sm  = StrictMath.sqrt(oka * oka + okb * okb)
  val hue_rad_sm = StrictMath.atan2(okb, oka)
  val hue_deg_sm = hue_rad_sm * 180 / StrictMath.PI
  val hR_sm      = hue_deg_sm * StrictMath.PI / 180
  val rev_a_sm   = chroma_sm * StrictMath.cos(hR_sm)
  val rev_b_sm   = chroma_sm * StrictMath.sin(hR_sm)
  println(s"\nStrictMath:")
  println(s"  chroma=$chroma_sm ${toBits(chroma_sm)}")
  println(s"  hue_rad=$hue_rad_sm ${toBits(hue_rad_sm)}")
  println(s"  hue_deg=$hue_deg_sm")
  println(s"  hR=$hR_sm ${toBits(hR_sm)}")
  println(s"  hR==hue_rad: ${hR_sm == hue_rad_sm}")
  println(s"  rev_a=$rev_a_sm ${toBits(rev_a_sm)} (orig ${toBits(oka)})")
  println(s"  rev_b=$rev_b_sm ${toBits(rev_b_sm)} (orig ${toBits(okb)})")

  // Now compute full XYZ chain with native C trig (which should match Dart)
  println("\n=== Full chain with native C trig ===")
  val m  = oklabToLms
  val c0 = m(0) * okL + m(1) * rev_a_c + m(2) * rev_b_c
  val c1 = m(3) * okL + m(4) * rev_a_c + m(5) * rev_b_c
  val c2 = m(6) * okL + m(7) * rev_a_c + m(8) * rev_b_c
  println(s"c0=$c0 ${toBits(c0)}")
  println(s"c1=$c1 ${toBits(c1)}")
  println(s"c2=$c2 ${toBits(c2)}")

  // Cube with pow (matching Dart's math.pow(c, 3))
  val rev_long  = NativeC.pow(c0, 3.0) + 0.0
  val rev_med   = NativeC.pow(c1, 3.0) + 0.0
  val rev_short = NativeC.pow(c2, 3.0) + 0.0
  println(s"rev_long=$rev_long ${toBits(rev_long)}")
  println(s"rev_med=$rev_med ${toBits(rev_med)}")
  println(s"rev_short=$rev_short ${toBits(rev_short)}")

  val xm    = lmsToXyzD65
  val xyz_x = xm(0) * rev_long + xm(1) * rev_med + xm(2) * rev_short
  val xyz_y = xm(3) * rev_long + xm(4) * rev_med + xm(5) * rev_short
  val xyz_z = xm(6) * rev_long + xm(7) * rev_med + xm(8) * rev_short
  println(s"\nFINAL XYZ (native C): x=$xyz_x y=$xyz_y z=$xyz_z")
  println(s"EXPECTED:             x=136956388.39988756 y=59264689.52803926 z=-623200798.6169877")

  // And with c*c*c
  val rev_long2  = c0 * c0 * c0
  val rev_med2   = c1 * c1 * c1
  val rev_short2 = c2 * c2 * c2
  val xyz_x2     = xm(0) * rev_long2 + xm(1) * rev_med2 + xm(2) * rev_short2
  val xyz_y2     = xm(3) * rev_long2 + xm(4) * rev_med2 + xm(5) * rev_short2
  val xyz_z2     = xm(6) * rev_long2 + xm(7) * rev_med2 + xm(8) * rev_short2
  println(s"FINAL XYZ (c*c*c):   x=$xyz_x2 y=$xyz_y2 z=$xyz_z2")

  // Full chain with Java Math trig (what our code currently uses)
  println("\n=== Full chain with Java Math trig ===")
  val c0j         = m(0) * okL + m(1) * rev_a_java + m(2) * rev_b_java
  val c1j         = m(3) * okL + m(4) * rev_a_java + m(5) * rev_b_java
  val c2j         = m(6) * okL + m(7) * rev_a_java + m(8) * rev_b_java
  val rev_long_j  = c0j * c0j * c0j
  val rev_med_j   = c1j * c1j * c1j
  val rev_short_j = c2j * c2j * c2j
  val xyz_xj      = xm(0) * rev_long_j + xm(1) * rev_med_j + xm(2) * rev_short_j
  val xyz_yj      = xm(3) * rev_long_j + xm(4) * rev_med_j + xm(5) * rev_short_j
  val xyz_zj      = xm(6) * rev_long_j + xm(7) * rev_med_j + xm(8) * rev_short_j
  println(s"FINAL XYZ (java):    x=$xyz_xj y=$xyz_yj z=$xyz_zj")
}
