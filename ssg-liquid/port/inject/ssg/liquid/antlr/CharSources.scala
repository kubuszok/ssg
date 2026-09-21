/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the ANTLR runtime factory `org.antlr.v4.runtime.CharStreams`, which liqp
 *   names in `liqp/TemplateParser.java` and `liqp/antlr/CharStreamWithLocation.java`.
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: injected replacement for an ANTLR runtime class, which is JVM-only.
 *   Convention: ANTLR's own factory names and its `<unknown>` source-name sentinel, because
 *     `NameResolver.getLocationFromCharStream` compares against that exact string.
 *   Idiom: the whole input is read once into a String — a template is parsed in one pass and
 *     liqp measures its size before parsing it, so there is nothing to stream.
 */
package ssg.liquid.antlr

/** Builds a [[CharSource]] from the places liqp reads a template from. */
object CharSources {

  def fromString(input: String): CharSource =
    new CharSources.StringCharSource(input, CharSource.UNKNOWN_SOURCE_NAME)

  def fromString(input: String, sourceName: String): CharSource =
    new CharSources.StringCharSource(input, sourceName)

  /** Reads the file at `path`. There is no cross-platform filesystem: Scala.js has no
    * `java.nio.file`, so this — like liqp's own `LocalFSNameResolver` and `include` tag — is a
    * counted portability residue of the library rather than of this file.
    */
  def fromPath(path: java.nio.file.Path): CharSource =
    new CharSources.StringCharSource(
      new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8),
      path.toString
    )

  def fromStream(input: java.io.InputStream): CharSource =
    fromReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))

  def fromReader(reader: java.io.Reader): CharSource = {
    val builder = new java.lang.StringBuilder()
    val buffer  = new Array[Char](8192)
    var read    = reader.read(buffer)
    while (read >= 0) {
      builder.append(buffer, 0, read)
      read = reader.read(buffer)
    }
    new CharSources.StringCharSource(builder.toString, CharSource.UNKNOWN_SOURCE_NAME)
  }

  /** The one implementation: the whole text, a cursor, and the name it was read under. */
  final class StringCharSource(private val input: String, private val sourceName: String) extends CharSource {
    private var cursor: Int = 0

    override def text(): String = input

    def getText(interval: Interval): String = {
      val start = math.max(interval.a, 0)
      val end   = math.min(interval.b, input.length - 1)
      if (start > end) "" else input.substring(start, end + 1)
    }

    def consume(): Unit =
      if (cursor >= input.length) throw new IllegalStateException("cannot consume EOF")
      else cursor += 1

    def LA(i: Int): Int =
      if (i == 0) 0
      else {
        val at = if (i < 0) cursor + i else cursor + i - 1
        if (at < 0 || at >= input.length) -1 else input.charAt(at).toInt
      }

    def mark(): Int = -1

    def release(marker: Int): Unit = ()

    def index(): Int = cursor

    def seek(index: Int): Unit = cursor = index

    def size(): Int = input.length

    def getSourceName(): String = sourceName

    override def toString: String = input
  }
}
