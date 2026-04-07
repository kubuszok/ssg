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
  def runSubdir(subdir: String): Outcome = {
    val args = List(
      "--client",
      "set ThisBuild / javaOptions += \"-Dssg.sass.spec=1\"",
      s"set ThisBuild / javaOptions += \"-Dssg.sass.spec.subdir=$subdir\"",
      "ssg-sass/testOnly ssg.sass.SassSpecRunner"
    )
    runSbt(args)
  }

  /** Run the sass-spec runner in default (regression) mode. */
  def runRegression(): Outcome = {
    val args = List(
      "--client",
      "set ThisBuild / javaOptions += \"-Dssg.sass.spec=1\"",
      "ssg-sass/testOnly ssg.sass.SassSpecRunner"
    )
    runSbt(args)
  }

  /** Run the sass-spec runner in strict mode (leak categories fail). */
  def runStrict(): Outcome = {
    val args = List(
      "--client",
      "set ThisBuild / javaOptions += \"-Dssg.sass.spec=1\"",
      "set ThisBuild / javaOptions += \"-Dssg.sass.spec.strict=1\"",
      "ssg-sass/testOnly ssg.sass.SassSpecRunner"
    )
    runSbt(args)
  }

  /** Run the sass-spec runner in snapshot mode (rewrites the baseline TSV). */
  def runSnapshot(): Outcome = {
    val args = List(
      "--client",
      "set ThisBuild / javaOptions += \"-Dssg.sass.spec=1\"",
      "set ThisBuild / javaOptions += \"-Dssg.sass.spec.snapshot=1\"",
      "ssg-sass/testOnly ssg.sass.SassSpecRunner"
    )
    runSbt(args)
  }

  /** Shared driver: runs sbt --client with the given args, parses the
    * output for sass-spec summary lines, and returns an Outcome.
    */
  private def runSbt(args: List[String]): Outcome = {
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
