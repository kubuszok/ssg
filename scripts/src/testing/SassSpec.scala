package ssgdev
package testing

/** Wraps the hardened SassSpecRunner via sbt invocation.
  *
  * `runRegression` runs the default (baseline regression) mode.
  * `runSubdir` runs with `-Dssg.sass.spec.subdir=<prefix>` and requires 100%
  * strict pass within the filtered set.
  * `runStrict` runs with `-Dssg.sass.spec.strict=1` and fails on any leak
  * category.
  */
object SassSpec {

  final case class Outcome(ok: Boolean, details: List[String], passCount: Int, total: Int)

  /** Run the sass-spec runner with a subdir filter. */
  def runSubdir(subdir: String): Outcome =
    runSbt(Map("ssg.sass.spec.subdir" -> subdir))

  /** Run the sass-spec runner in default (regression) mode. */
  def runRegression(): Outcome =
    runSbt(Map.empty)

  /** Run the sass-spec runner in strict mode (leak categories fail). */
  def runStrict(): Outcome =
    runSbt(Map("ssg.sass.spec.strict" -> "1"))

  /** Run the sass-spec runner in snapshot mode (rewrites the baseline TSV). */
  def runSnapshot(): Outcome =
    runSbt(Map("ssg.sass.spec.snapshot" -> "1"))

  /** Shared driver: runs sbt --client with the runner. System properties
    * are passed via `set ThisBuild / Test / javaOptions := List(...)` so
    * each invocation REPLACES the prior set rather than accumulating it
    * (using `+=` caused state from one ssg-dev invocation to leak into
    * the next via the persistent sbt server). The ThisBuild scope only
    * overrides at the build level; project-level javaOptions from
    * build.sbt are unaffected.
    *
    * Multiple commands are joined with `;` so a single sbt --client call
    * sets the property and then runs the test.
    */
  private def runSbt(props: Map[String, String]): Outcome = {
    val propList = props.toList.map { case (k, v) => s"""\"-D$k=$v\"""" }.mkString(", ")
    val setCmd = s"set ThisBuild / Test / javaOptions := List($propList)"
    val testCmd = "ssg-sass/testOnly ssg.sass.SassSpecRunner"
    val cmd = s"$setCmd; $testCmd"
    val args = List("--client", cmd)
    val result = Proc.run("sbt", args, cwd = Some(Paths.projectRoot))
    val out = result.stdout + "\n" + result.stderr

    // Parse headline: `sass-spec: Total=N  Passing=M (P%)`
    val totalRegex = """sass-spec:\s+Total=(\d+)\s+Passing=(\d+)""".r
    val (total, pass) = totalRegex.findFirstMatchIn(out) match {
      case Some(m) => (m.group(1).toInt, m.group(2).toInt)
      case None    => (0, 0)
    }

    if (result.ok) {
      Outcome(ok = true, details = List(s"Total=$total Passing=$pass"), passCount = pass, total = total)
    } else {
      // On failure, extract the relevant lines from the output for details.
      val relevantLines = out.linesIterator.toList.filter { l =>
        l.contains("sass-spec:") || l.contains("FAIL") || l.contains("regressions") ||
          l.contains("leak") || l.contains("Strict-pass")
      }
      Outcome(
        ok = false,
        details = relevantLines.distinct.take(10),
        passCount = pass,
        total = total
      )
    }
  }
}
