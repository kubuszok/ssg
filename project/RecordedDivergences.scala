import balticporter.frontend.ts.{ NonJavaBodies, RecordedDivergence }

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

/** The reference members each non-Java port keeps because ssg diverged from upstream on purpose, read from ONE table, `.rescale/data/recorded-divergences.tsv`. Every row must cite a tracker issue
  * recording a deliberate divergence, and must still name a reference member after the derive; either failure stops the generation, so a decision can be neither invented nor silently lost.
  */
object RecordedDivergences {

  val table: String = ".rescale/data/recorded-divergences.tsv"

  /** Tracker categories that record a deliberate divergence from upstream. */
  private val divergenceCategories: Set[String] = Set("divergence", "pit-of-success")

  private def rows(path: Path): List[Array[String]] =
    Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toList.filter(l => l.trim.nonEmpty && !l.startsWith("#")).map(_.split("\t", -1))

  /** The records of `module`; refuses a malformed row and a row whose issue the tracker does not hold as a divergence. */
  def forModule(ssgRoot: Path, module: String): List[RecordedDivergence] = {
    val categories = rows(ssgRoot.resolve(".rescale/data/issues.tsv")).collect { case cols if cols.length > 2 => cols(0) -> cols(2) }.toMap
    rows(ssgRoot.resolve(table)).filter(_.headOption.contains(module)).map { cols =>
      if (cols.length < 5) sys.error(s"[Baltic Porter] $table: a row needs module, file, member, params and issue: ${cols.mkString("\t")}")
      val issue = cols(4).trim
      categories.get(issue) match {
        case Some(c) if divergenceCategories(c) => RecordedDivergence.parse(cols(1), cols(2), cols(3), issue)
        case Some(c)                            => sys.error(s"[Baltic Porter] $table: $issue is a '$c' issue, not a recorded divergence")
        case None                               => sys.error(s"[Baltic Porter] $table: $issue is not in the tracker")
      }
    }
  }

  /** Stops the generation when a record of `module` named no reference member: the reference moved and the decision must be re-recorded. */
  def requireMatched(module: String, run: NonJavaBodies.Run): Unit =
    if (run.unmatchedRecorded.nonEmpty)
      sys.error(s"[Baltic Porter] $module: recorded divergences that name no reference member: ${run.unmatchedRecorded.map(_.describe).mkString(", ")}")

  /** The bytes a generated tree depends on: the table and this reader. */
  def fingerprintInputs(ssgRoot: Path): List[Array[Byte]] =
    List(ssgRoot.resolve(table), ssgRoot.resolve("project/RecordedDivergences.scala")).map(p => Files.readString(p).replace("\r", "").getBytes("UTF-8"))
}
