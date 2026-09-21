/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/Template.java, whose time-limited render names
 *   java.util.concurrent.ExecutorService and Future
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the Scala.js row, whose java library ships neither type. These carry the members
 *     liqp calls, at java's names and arity, and nothing else.
 */
package ssg.liquid.concurrent

import java.util.concurrent.{ Callable, TimeUnit }

trait ExecutorService {
  def submit[T](task: Callable[T]): Future[T]
  def shutdown(): Unit
}

trait Future[T] {
  def get(timeout: Long, unit: TimeUnit): T
}
