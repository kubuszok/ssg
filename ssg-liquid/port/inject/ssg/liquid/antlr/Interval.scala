/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: the ANTLR runtime type `org.antlr.v4.runtime.misc.Interval`, which
 *   liqp's own `liqp/antlr/FilterCharStream.java` names in one signature.
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: injected replacement for an ANTLR runtime type, which is JVM-only.
 *   Convention: a closed range of character positions, ANTLR's own inclusive-inclusive
 *     convention, because that is what the one call site reads it as.
 */
package ssg.liquid.antlr

/** An inclusive range of character positions in a [[CharSource]]. */
final class Interval(val a: Int, val b: Int) {

  override def toString: String = s"$a..$b"
}

object Interval {

  def of(a: Int, b: Int): Interval = new Interval(a, b)
}
