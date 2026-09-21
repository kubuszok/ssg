/*
 * Ported from Liqp - https://github.com/bkiers/Liqp
 * Original source: src/main/java/liqp/Template.java, whose time-limited render names
 *   java.util.concurrent.ExecutorService and Future
 * Original license: MIT, Copyright (c) 2010-2013 by Bart Kiers
 *
 * Migration notes:
 *   Origin: the JVM and Scala Native rows, where the JDK types exist. These are ALIASES, so
 *     `Template.render(variables, executorService, shutdown)` still takes any JDK executor.
 */
package ssg.liquid.concurrent

type ExecutorService = java.util.concurrent.ExecutorService

type Future[T] = java.util.concurrent.Future[T]
