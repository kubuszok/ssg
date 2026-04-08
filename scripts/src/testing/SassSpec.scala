package ssgdev
package testing

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths => NioPaths }

/** Wraps the hardened SassSpecRunner via sbt invocation.
  *
  * Mode keys are passed through a one-shot file at
  * `ssg-sass/target/sass-spec-mode.tsv` (key=value per line) which the
  * runner reads on entry and deletes on exit. Earlier versions of this
  * wrapper used `set ThisBuild / Test / javaOptions := List(...)` to pass
  * mode flags as -D system properties; that approach was abandoned
  * because the persistent sbt server occasionally leaked stale snapshot
  * mode from a prior `runSnapshot` into the next `runRegression`,
  * silently overwriting the baseline.
  */
object SassSpec {

  final case class Outcome(ok: Boolean, details: List[String], passCount: Int, total: Int)

  /** Run the sass-spec runner with a subdir filter. */
  def runSubdir(subdir: String): Outcome =
    runSbt(Map("subdir" -> subdir))

  /** Run the sass-spec runner in default (regression) mode. */
  def runRegression(): Outcome =
    runSbt(Map.empty)

  /** Run the sass-spec runner in strict mode (leak categories fail). */
  def runStrict(): Outcome =
    runSbt(Map("strict" -> "1"))

  /** Run the sass-spec runner in snapshot mode (rewrites the baseline TSV). */
  def runSnapshot(): Outcome =
    runSbt(Map("snapshot" -> "1"))

  /** Shared driver: writes the mode file, runs the test, and lets the
    * runner clean the mode file up on exit. We never delete the mode
    * file from the wrapper because if the wrapper crashes between
    * write and delete, the next invocation would inherit stale state.
    * The runner cleans up in a `finally` block, which is the only
    * place that knows the mode file is no longer needed.
    */
  private def runSbt(props: Map[String, String]): Outcome = {
    writeModeFile(props)
    val cmd = "ssg-sass/testOnly ssg.sass.SassSpecRunner"
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

  /** Write the mode file consumed by SassSpecRunner. The runner deletes
    * it on exit so we don't need to clean up here. If the previous
    * runner crashed and left a stale mode file, this overwrite is the
    * only correct behavior — never accumulate.
    */
  private def writeModeFile(props: Map[String, String]): Unit = {
    val targetDir = new File(s"${Paths.projectRoot}/ssg-sass/target")
    targetDir.mkdirs()
    val path = NioPaths.get(targetDir.getAbsolutePath, "sass-spec-mode.tsv")
    val sb = new StringBuilder
    sb.append("# sass-spec runner mode (key=value, one per line)\n")
    props.toList.sortBy(_._1).foreach { case (k, v) =>
      sb.append(k).append('=').append(v).append('\n')
    }
    Files.write(path, sb.toString.getBytes(StandardCharsets.UTF_8))
  }
}
