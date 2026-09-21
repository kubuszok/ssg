/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the ANTLR runtime types `org.antlr.v4.runtime.CharStream` and
 *   `org.antlr.v4.runtime.IntStream`, which liqp names in `liqp/antlr/FilterCharStream.java`,
 *   `liqp/antlr/CharStreamWithLocation.java`, `liqp/antlr/NameResolver.java`,
 *   `liqp/TemplateParser.java` and `liqp/Template.java`.
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: injected replacement for an ANTLR runtime interface, which is JVM-only.
 *   Convention: ANTLR's own member names and arity, so liqp's `FilterCharStream` and
 *     `CharStreamWithLocation` port mechanically on top of it.
 *   Idiom: `text` is the whole source, derived from the members java declares, because the
 *     hand-written lexer reads the template in one piece rather than one character at a time.
 */
package ssg.liquid.antlr

/** A template's characters plus the name of where they came from. */
trait CharSource {

  /** The characters in `interval`, both ends included. */
  def getText(interval: Interval): String

  def consume(): Unit

  /** The character `i` positions ahead of the cursor, or -1 past the end. */
  def LA(i: Int): Int

  def mark(): Int

  def release(marker: Int): Unit

  def index(): Int

  def seek(index: Int): Unit

  /** How many characters this source holds. */
  def size(): Int

  /** Where the characters came from, or [[CharSource.UNKNOWN_SOURCE_NAME]]. */
  def getSourceName(): String

  /** The whole source, read through the members above so a delegating source needs nothing new. */
  def text(): String = if (size() == 0) "" else getText(Interval.of(0, size() - 1))
}

object CharSource {

  /** ANTLR's own sentinel: `NameResolver.getLocationFromCharStream` compares against it to decide
    * that a template has no file behind it.
    */
  final val UNKNOWN_SOURCE_NAME: String = "<unknown>"
}
