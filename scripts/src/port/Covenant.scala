package ssgdev
package port

import java.io.{ BufferedReader, File, FileReader }

/** Covenant header parser + verifier.
  *
  * A covenanted file carries a header block of the form:
  *
  * {{{
  *  /*
  *   * ... standard license header ...
  *   *
  *   * Covenant: full-port
  *   * Covenant-baseline-spec-pass: 4157
  *   * Covenant-baseline-loc: 1153
  *   * Covenant-baseline-methods: name1,name2,name3,...
  *   * Covenant-dart-reference: lib/src/environment.dart
  *   * Covenant-verified: 2026-04-08
  *   */
  * }}}
  *
  * `verify` re-extracts the file's current method set and compares it
  * against `baseline-methods`. A removed method is a hard fail (catches
  * silent reverts). Also re-runs `Shortcuts.scanFile` — any hit in a
  * covenanted file is a hard fail.
  */
object Covenant {

  final case class Header(
    covenant:        String, // "full-port" or "partial-port" or other
    baselineSpecPass: Int,
    baselineLoc:     Int,
    baselineMethods: Set[String],
    dartReference:   String,
    verified:        String
  )

  def parse(filePath: String): Option[Header] = {
    val f = new File(filePath)
    if (!f.exists()) return None
    val reader = new BufferedReader(new FileReader(f))
    try {
      var covenant: Option[String] = None
      var baseLine: Option[Int] = None
      var baseLoc: Option[Int] = None
      var baseMethods: Option[Set[String]] = None
      var dartRef: Option[String] = None
      var verified: Option[String] = None
      var line = reader.readLine()
      var linesRead = 0
      while (line != null && linesRead < 80) {
        val t = line.trim.stripPrefix("*").stripPrefix("/*").stripPrefix("//").trim
        if (t.startsWith("Covenant:"))
          covenant = Some(t.substring("Covenant:".length).trim)
        else if (t.startsWith("Covenant-baseline-spec-pass:"))
          baseLine = t.substring("Covenant-baseline-spec-pass:".length).trim.toIntOption
        else if (t.startsWith("Covenant-baseline-loc:"))
          baseLoc = t.substring("Covenant-baseline-loc:".length).trim.toIntOption
        else if (t.startsWith("Covenant-baseline-methods:"))
          baseMethods = Some(
            t.substring("Covenant-baseline-methods:".length).trim.split(",").map(_.trim).filter(_.nonEmpty).toSet
          )
        else if (t.startsWith("Covenant-dart-reference:"))
          dartRef = Some(t.substring("Covenant-dart-reference:".length).trim)
        else if (t.startsWith("Covenant-verified:"))
          verified = Some(t.substring("Covenant-verified:".length).trim)
        line = reader.readLine()
        linesRead += 1
      }
      covenant.map { c =>
        Header(
          covenant = c,
          baselineSpecPass = baseLine.getOrElse(0),
          baselineLoc = baseLoc.getOrElse(0),
          baselineMethods = baseMethods.getOrElse(Set.empty),
          dartReference = dartRef.getOrElse(""),
          verified = verified.getOrElse("")
        )
      }
    } finally reader.close()
  }

  /** Verify a covenanted file. Returns Right(()) on pass, Left(reason) on fail.
    *
    * Fails on any of:
    *   - No covenant header present.
    *   - Current method set is missing names from `baseline-methods`.
    *   - `Shortcuts.scanFile` returns any hit.
    */
  def verify(filePath: String): Either[String, Unit] = {
    val header = parse(filePath) match {
      case Some(h) => h
      case None    => return Left("no covenant header")
    }
    if (header.covenant != "full-port") {
      return Right(()) // only full-port covenants are enforced
    }
    // Normalize both sides through `Methods.normalizeName` so the
    // dart-private-underscore convention and the Scala `Fn` suffix
    // align with the baseline (which is also stored in normalized
    // form).
    val currentMethods = compare.Methods.extractScalaMethods(filePath).iterator
      .map(compare.Methods.normalizeName)
      .toSet
    val baselineNormalized = header.baselineMethods.iterator
      .map(compare.Methods.normalizeName)
      .toSet
    val missing = baselineNormalized -- currentMethods
    if (missing.nonEmpty)
      return Left(s"methods removed since baseline: ${missing.toList.sorted.take(5).mkString(", ")}")
    val hits = quality.Shortcuts.scanFile(filePath)
    if (hits.nonEmpty)
      return Left(s"shortcuts introduced: ${hits.size} hit(s), e.g. ${hits.head.pattern} at line ${hits.head.line}")
    Right(())
  }
}
