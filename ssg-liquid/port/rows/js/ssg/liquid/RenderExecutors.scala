/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/Template.java, whose renderToObject runs a time-limited
 *   render on Executors.newSingleThreadExecutor()
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the Scala.js row, which has one thread and no java.util.concurrent executors.
 *   Difference: the render runs on the CALLING thread, when the caller asks for the result, and
 *     the limit is checked once it has finished. A render that overruns is still refused with
 *     java's TimeoutException, so Template raises java's own message; it is not interrupted.
 */
package ssg
package liquid

import ssg.liquid.concurrent.{ ExecutorService, Future }

import java.util.concurrent.{ Callable, ExecutionException, TimeUnit, TimeoutException }

/** The executor a time-limited render runs on. */
object RenderExecutors {

  def single(): ExecutorService = new SameThread()

  private final class SameThread extends ExecutorService {
    override def submit[T](task: Callable[T]): Future[T] = new Deferred[T](task)
    override def shutdown():                   Unit      = ()
  }

  /** Runs its task when the result is asked for, and measures how long it took. */
  private final class Deferred[T](task: Callable[T]) extends Future[T] {

    override def get(timeout: Long, unit: TimeUnit): T = {
      val start = System.nanoTime()
      val value =
        try task.call()
        catch { case t: Throwable => throw new ExecutionException(t) }
      if (System.nanoTime() - start > unit.toNanos(timeout)) throw new TimeoutException()
      value
    }
  }
}
