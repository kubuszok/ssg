/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/Template.java, whose renderToObject runs a time-limited
 *   render on Executors.newSingleThreadExecutor()
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the Scala Native row. The JDK executor types exist there, but ssg builds Native
 *     without threads, so there is no second thread to render on.
 *   Difference: the render runs on the CALLING thread, when the caller asks for the result, and
 *     the limit is checked once it has finished. A render that overruns is still refused with
 *     java's TimeoutException, so Template raises java's own message; it is not interrupted.
 */
package ssg
package liquid

import java.util.concurrent.{ AbstractExecutorService, Callable, CancellationException, ExecutionException, ExecutorService, Future, TimeUnit, TimeoutException }

/** The executor a time-limited render runs on. */
object RenderExecutors {

  def single(): ExecutorService = new SameThread()

  private final class SameThread extends AbstractExecutorService {
    private var down: Boolean = false

    override def shutdown(): Unit = down = true
    override def shutdownNow(): java.util.List[Runnable] = {
      down = true
      new java.util.ArrayList[Runnable]()
    }
    override def isShutdown():                                   Boolean   = down
    override def isTerminated():                                 Boolean   = down
    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean  = down
    override def execute(command: Runnable):                     Unit      = command.run()
    override def submit[T](task: Callable[T]):                   Future[T] = new Deferred[T](task)
  }

  /** Runs its task the first time the result is asked for, and remembers how long it took. */
  private final class Deferred[T](task: Callable[T]) extends Future[T] {
    private var ran:          Boolean           = false
    private var cancelled:    Boolean           = false
    private var value:        T                 = scala.compiletime.uninitialized
    private var failure:      Option[Throwable] = None
    private var elapsedNanos: Long              = 0L

    private def run(): Unit =
      if (!ran) {
        ran = true
        val start = System.nanoTime()
        try value = task.call()
        catch { case t: Throwable => failure = Some(t) }
        elapsedNanos = System.nanoTime() - start
      }

    private def outcome(): T =
      failure match {
        case Some(t) => throw new ExecutionException(t)
        case None    => value
      }

    override def cancel(mayInterruptIfRunning: Boolean): Boolean =
      if (ran) false
      else {
        ran = true
        cancelled = true
        true
      }

    override def isCancelled(): Boolean = cancelled
    override def isDone():      Boolean = ran

    override def get(): T = {
      if (cancelled) throw new CancellationException()
      run()
      outcome()
    }

    override def get(timeout: Long, unit: TimeUnit): T = {
      if (cancelled) throw new CancellationException()
      run()
      if (elapsedNanos > unit.toNanos(timeout)) throw new TimeoutException()
      outcome()
    }
  }
}
