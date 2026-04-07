package ssgdev

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter, RandomAccessFile}
import java.nio.file.{Files, Paths => JPaths, StandardCopyOption}
import scala.collection.mutable.ListBuffer

/** TSV reader/writer with typed headers and comment support. */
object Tsv {

  final case class Table(
    headers: List[String],
    rows: List[Map[String, String]],
    comments: List[String] = Nil
  ) {
    def filter(pred: Map[String, String] => Boolean): Table = copy(rows = rows.filter(pred))
    def find(pred: Map[String, String] => Boolean): Option[Map[String, String]] = rows.find(pred)
    def sortBy(key: String): Table = copy(rows = rows.sortBy(_.getOrElse(key, "")))
    def size: Int = rows.size

    def paginate(limit: Option[Int], offset: Option[Int]): Table = {
      var r = rows
      offset.foreach(o => r = r.drop(o))
      limit.foreach(l => r = r.take(l))
      copy(rows = r)
    }

    def addRow(row: Map[String, String]): Table = copy(rows = rows :+ row)

    def updateRow(pred: Map[String, String] => Boolean, updates: Map[String, String]): Table = {
      copy(rows = rows.map { row =>
        if (pred(row)) row ++ updates else row
      })
    }

    def stats(key: String): Map[String, Int] = {
      rows.groupBy(_.getOrElse(key, "(empty)")).map { case (k, v) => k -> v.size }
    }
  }

  def read(path: String): Table = {
    val reader = new BufferedReader(new FileReader(path))
    try {
      val comments = ListBuffer.empty[String]
      val dataRows = ListBuffer.empty[List[String]]
      var headers: List[String] = Nil
      var headerFound = false
      var line = reader.readLine()
      while (line != null) {
        if (!headerFound && line.startsWith("# ") && line.contains("\t")) {
          // Header comment line: "# col1\tcol2\t..."
          headers = splitFields(line.drop(2))
          headerFound = true
        } else if (line.startsWith("#")) {
          comments += line
        } else if (line.nonEmpty) {
          if (!headerFound) {
            // Strict: data before any "# col\tcol" header is an error.
            throw new RuntimeException(s"TSV $path: data row before header line: $line")
          }
          dataRows += splitFields(line)
        }
        line = reader.readLine()
      }
      val rows = dataRows.toList.map { fields =>
        headers.zip(fields.padTo(headers.size, "")).toMap
      }
      Table(headers, rows, comments.toList)
    } finally {
      reader.close()
    }
  }

  /** CSV-aware tab splitter: respects "..." wrapping that contains tabs/newlines/quotes. */
  private[ssgdev] def splitFields(line: String): List[String] = {
    val out = ListBuffer.empty[String]
    val cur = new StringBuilder
    var i = 0
    var inQuotes = false
    val n = line.length
    while (i < n) {
      val c = line.charAt(i)
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < n && line.charAt(i + 1) == '"') {
            cur.append('"'); i += 2
          } else {
            inQuotes = false; i += 1
          }
        } else {
          cur.append(c); i += 1
        }
      } else {
        if (c == '"' && cur.isEmpty) {
          inQuotes = true; i += 1
        } else if (c == '\t') {
          out += cur.toString; cur.clear(); i += 1
        } else {
          cur.append(c); i += 1
        }
      }
    }
    out += cur.toString
    out.toList
  }

  /** Atomic write: write to <path>.tmp then atomically move into place. */
  def write(path: String, table: Table): Unit = {
    val tmp = path + ".tmp"
    val writer = new BufferedWriter(new FileWriter(tmp))
    try {
      table.comments.foreach { c =>
        writer.write(c)
        writer.newLine()
      }
      writer.write("# " + table.headers.mkString("\t"))
      writer.newLine()
      table.rows.foreach { row =>
        val line = table.headers.map(h => quote(row.getOrElse(h, ""))).mkString("\t")
        writer.write(line)
        writer.newLine()
      }
      writer.flush()
    } finally {
      writer.close()
    }
    try {
      Files.move(JPaths.get(tmp), JPaths.get(path), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch {
      case _: Throwable =>
        // ATOMIC_MOVE may not be supported on all FS; fall back to plain replace.
        Files.move(JPaths.get(tmp), JPaths.get(path), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  /** Cross-process locked load+mutate+save. Acquires an exclusive file lock on
    * `<path>.lock`, runs `fn` on the loaded table, writes the result atomically.
    * Retries lock acquisition with backoff for up to ~10s.
    */
  def modify(path: String)(fn: Table => Table): Table = {
    val lockPath = path + ".lock"
    // Ensure lock file exists.
    val lockFile = new java.io.File(lockPath)
    if (!lockFile.exists()) lockFile.createNewFile()

    val raf = new RandomAccessFile(lockFile, "rw")
    val channel = raf.getChannel
    try {
      var lock: java.nio.channels.FileLock = null
      var attempts = 0
      val maxAttempts = 600 // ~30s @ 50ms backoff
      while (lock == null && attempts < maxAttempts) {
        try {
          lock = channel.tryLock()
        } catch {
          // Scala Native throws IOException when held; JVM returns null. Treat both as "retry".
          case _: java.nio.channels.OverlappingFileLockException => // same JVM
          case _: java.io.IOException                            => // SN: lock held by another process
        }
        if (lock == null) {
          Thread.sleep(50)
          attempts += 1
        }
      }
      if (lock == null) {
        throw new RuntimeException(s"Tsv.modify: could not acquire lock on $lockPath after $maxAttempts attempts")
      }
      try {
        val table = if (new java.io.File(path).exists()) read(path) else Table(Nil, Nil)
        val updated = fn(table)
        write(path, updated)
        updated
      } finally {
        lock.release()
      }
    } finally {
      channel.close()
      raf.close()
    }
  }

  private def quote(value: String): String = {
    if (value.contains("\t") || value.contains("\n") || value.contains("\"")) {
      "\"" + value.replace("\"", "\"\"") + "\""
    } else {
      value
    }
  }
}
