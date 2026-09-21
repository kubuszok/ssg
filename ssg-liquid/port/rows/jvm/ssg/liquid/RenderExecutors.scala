/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/Template.java, whose renderToObject runs a time-limited
 *   render on Executors.newSingleThreadExecutor()
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the JVM row. There are threads here, so this is java's own executor.
 */
package ssg
package liquid

import java.util.concurrent.{ ExecutorService, Executors }

/** The executor a time-limited render runs on. */
object RenderExecutors {

  def single(): ExecutorService = Executors.newSingleThreadExecutor()
}
