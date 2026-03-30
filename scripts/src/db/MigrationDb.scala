package ssgdev
package db

import java.io.File
import java.time.LocalDate

/** Migration status database operations.
  * Tracks porting progress for each source file across all libraries. */
object MigrationDb {

  private val headers = List(
    "source_lib", "source_path", "ssg_path", "status", "module",
    "last_updated", "notes", "source_sync_commit", "last_sync_date"
  )

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        println("""Usage: ssg-dev db migration <command>
                  |
                  |Commands:
                  |  list [--status S] [--lib L] [--module M] [--package P] [--limit N] [--offset N]
                  |  get <source_path>
                  |  set <source_path> --status S [--notes TEXT] [--ssg-path P]
                  |  batch-set --pattern PAT --status S [--notes TEXT]    Update all rows matching pattern
                  |  sync       Scan original source trees and populate entries
                  |  stats      Summary counts""".stripMargin)
      case "list" :: rest => list(Cli.parse(rest))
      case "get" :: rest => get(Cli.parse(rest))
      case "set" :: rest => set(Cli.parse(rest))
      case "batch-set" :: rest => batchSet(Cli.parse(rest))
      case "sync" :: rest => sync(Cli.parse(rest))
      case "stats" :: _ => stats()
      case other :: _ =>
        Term.err(s"Unknown migration command: $other")
        sys.exit(1)
    }
  }

  def list(args: Cli.Args): Unit = {
    var table = load()
    args.flag("status").foreach(s => table = table.filter(_.getOrElse("status", "") == s))
    args.flag("lib").foreach(l => table = table.filter(_.getOrElse("source_lib", "") == l))
    args.flag("module").foreach(m => table = table.filter(_.getOrElse("module", "") == m))
    args.flag("package").foreach(p => table = table.filter(_.getOrElse("source_path", "").contains(s"/$p/")))
    table = table.paginate(
      args.flag("limit").map(_.toInt),
      args.flag("offset").map(_.toInt)
    )
    printTable(table)
  }

  def get(args: Cli.Args): Unit = {
    val path = args.requirePositional(0, "source_path")
    val table = load()
    table.find(r => r.getOrElse("source_path", "").contains(path)) match {
      case Some(row) =>
        headers.foreach(h => println(s"  $h: ${row.getOrElse(h, "")}"))
      case None =>
        Term.err(s"Not found: $path")
        sys.exit(1)
    }
  }

  def set(args: Cli.Args): Unit = {
    val path = args.requirePositional(0, "source_path")
    val updates = scala.collection.mutable.Map.empty[String, String]
    args.flag("status").foreach(s => updates("status") = s)
    args.flag("notes").foreach(n => updates("notes") = n)
    args.flag("ssg-path").foreach(p => updates("ssg_path") = p)
    updates("last_updated") = LocalDate.now().toString

    var table = load()
    val found = table.rows.exists(_.getOrElse("source_path", "") == path)
    if (found) {
      table = table.updateRow(
        _.getOrElse("source_path", "") == path,
        updates.toMap
      )
    } else {
      // Upsert: create new entry
      val row = Map(
        "source_lib" -> updates.getOrElse("source_lib", "flexmark"),
        "source_path" -> path,
        "ssg_path" -> updates.getOrElse("ssg_path", ""),
        "status" -> updates.getOrElse("status", "ported"),
        "module" -> updates.getOrElse("module", "ssg-md"),
        "last_updated" -> updates.getOrElse("last_updated", LocalDate.now().toString),
        "notes" -> updates.getOrElse("notes", ""),
        "source_sync_commit" -> "",
        "last_sync_date" -> ""
      )
      table = table.addRow(row)
    }
    save(table)
    Term.ok(s"Updated: $path")
  }

  /** Bulk update rows matching a source_path pattern. */
  def batchSet(args: Cli.Args): Unit = {
    val pattern = args.flag("pattern").getOrElse {
      Term.err("Missing required --pattern flag")
      sys.exit(1)
    }
    val updates = scala.collection.mutable.Map.empty[String, String]
    args.flag("status").foreach(s => updates("status") = s)
    args.flag("notes").foreach(n => updates("notes") = n)
    args.flag("ssg-path").foreach(p => updates("ssg_path") = p)
    updates("last_updated") = LocalDate.now().toString

    var table = load()
    val predicate: Map[String, String] => Boolean =
      _.getOrElse("source_path", "").contains(pattern)

    val matchCount = table.rows.count(predicate)
    if (matchCount == 0) {
      Term.err(s"No rows match pattern: $pattern")
      sys.exit(1)
    }

    table = table.updateRow(predicate, updates.toMap)
    save(table)
    Term.ok(s"Updated $matchCount rows matching '$pattern'")
  }

  /** Scan original source trees and populate the migration database. */
  def sync(args: Cli.Args): Unit = {
    val allRows = scala.collection.mutable.ListBuffer.empty[Map[String, String]]
    val today = LocalDate.now().toString

    // Scan each library
    val libs = List(
      ("flexmark", Paths.flexmarkSrc, "ssg-md", List(".java")),
      ("liqp", Paths.liqpSrc, "ssg-liquid", List(".java")),
      ("dart-sass", Paths.dartSassSrc, "ssg-sass", List(".dart")),
      ("jekyll-minifier", Paths.jekyllMinifierSrc, "ssg-html", List(".rb"))
    )

    for ((lib, srcRoot, module, extensions) <- libs) {
      val srcDir = new File(srcRoot)
      if (srcDir.exists() && srcDir.isDirectory) {
        val files = findSourceFiles(srcDir, extensions)
        val rows = files.map { file =>
          val relativePath = file.getAbsolutePath.stripPrefix(srcDir.getAbsolutePath + "/")
          Map(
            "source_lib" -> lib,
            "source_path" -> relativePath,
            "ssg_path" -> "",
            "status" -> "not_started",
            "module" -> module,
            "last_updated" -> today,
            "notes" -> "",
            "source_sync_commit" -> "",
            "last_sync_date" -> today
          )
        }
        allRows ++= rows
        Term.info(s"Synced ${rows.size} files from $lib")
      } else {
        Term.warn(s"Source directory not found: $srcRoot (run 'git submodule update --init')")
      }
    }

    // Merge with existing data (preserve status for known files)
    val existing = load()
    val existingByKey = existing.rows.groupBy(r =>
      r.getOrElse("source_lib", "") + ":" + r.getOrElse("source_path", "")
    ).map { case (k, vs) => k -> vs.head }

    val merged = allRows.toList.map { row =>
      val key = row("source_lib") + ":" + row("source_path")
      existingByKey.get(key) match {
        case Some(existing) =>
          // Preserve existing status and notes
          row ++ Map(
            "status" -> existing.getOrElse("status", "not_started"),
            "ssg_path" -> existing.getOrElse("ssg_path", ""),
            "notes" -> existing.getOrElse("notes", "")
          )
        case None => row
      }
    }

    val table = Tsv.Table(headers, merged, List("# SSG Migration Database"))
    save(table)
    Term.ok(s"Synced ${merged.size} total entries")
  }

  def stats(): Unit = {
    val table = load()
    println("=== Migration Status ===")
    val byStatus = table.stats("status").toList.sortBy(-_._2)
    byStatus.foreach { case (status, count) =>
      println(f"  $status%-20s $count%d")
    }
    println(f"  ${"Total"}%-20s ${table.size}%d")
    println()
    println("=== By Library ===")
    val byLib = table.stats("source_lib").toList.sortBy(-_._2)
    byLib.foreach { case (lib, count) =>
      println(f"  $lib%-25s $count%d")
    }
    println()
    println("=== By Module ===")
    val byMod = table.stats("module").toList.sortBy(-_._2)
    byMod.foreach { case (mod, count) =>
      println(f"  $mod%-25s $count%d")
    }
  }

  private def findSourceFiles(dir: File, extensions: List[String]): List[File] = {
    val files = scala.collection.mutable.ListBuffer.empty[File]
    def walk(d: File): Unit = {
      if (d.isDirectory) {
        val children = d.listFiles()
        if (children != null) children.foreach(walk)
      } else if (extensions.exists(ext => d.getName.endsWith(ext))) {
        // Skip test directories and build artifacts
        val path = d.getAbsolutePath
        if (!path.contains("/test/") && !path.contains("/build/") &&
            !path.contains("/target/") && !path.contains("/.") &&
            !path.contains("/node_modules/")) {
          files += d
        }
      }
    }
    walk(dir)
    files.toList.sortBy(_.getAbsolutePath)
  }

  private def load(): Tsv.Table = {
    val path = Paths.migrationTsv
    if (new File(path).exists()) Tsv.read(path)
    else Tsv.Table(headers, Nil, List("# SSG Migration Database"))
  }

  private def save(table: Tsv.Table): Unit = {
    Tsv.write(Paths.migrationTsv, table)
  }

  private def printTable(table: Tsv.Table): Unit = {
    if (table.rows.isEmpty) { println("(no results)"); return }
    val display = List("source_lib", "source_path", "status", "module", "notes")
    println(Term.table(display, table.rows.map(r => display.map(h => r.getOrElse(h, "")))))
  }
}
