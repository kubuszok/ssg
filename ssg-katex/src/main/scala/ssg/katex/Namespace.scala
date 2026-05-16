/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * A `Namespace` refers to a space of nameable things like macros or lengths,
 * which can be `set` either globally or local to a nested group, using an
 * undo stack similar to how TeX implements this functionality.
 * Performance-wise, `get` and local `set` take constant time, while global
 * `set` takes time proportional to the depth of group nesting.
 *
 * Original source: katex src/Namespace.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: Record<string, Value> -> mutable.Map[String, Value]
 *   Convention: JS delete -> map.remove
 *   Idiom: JS undefined/null checks -> Nullable
 */
package ssg
package katex

import scala.collection.mutable

import lowlevel.Nullable

/** A `Namespace` refers to a space of nameable things like macros or lengths, which can be `set` either globally or local to a nested group, using an undo stack similar to how TeX implements this
  * functionality. Performance-wise, `get` and local `set` take constant time, while global `set` takes time proportional to the depth of group nesting.
  */
class Namespace[Value](
  val builtins: mutable.Map[String, Value] = mutable.Map.empty[String, Value],
  globalMacros: mutable.Map[String, Value] = mutable.Map.empty[String, Value]
) {

  /** Both arguments are optional. The first argument is an object of built-in mappings which never change. The second argument is an object of initial (global-level) mappings, which will constantly
    * change according to any global/top-level `set`s done.
    */
  val current: mutable.Map[String, Value] = globalMacros

  val undefStack: mutable.ArrayBuffer[mutable.Map[String, Nullable[Value]]] =
    mutable.ArrayBuffer.empty

  /** Start a new nested group, affecting future local `set`s.
    */
  def beginGroup(): Unit =
    undefStack.addOne(mutable.Map.empty)

  /** End current nested group, restoring values before the group began.
    */
  def endGroup(): Unit = {
    if (undefStack.isEmpty) {
      throw new ParseError(
        "Unbalanced namespace destruction: attempt " +
          "to pop global namespace; please report this as a bug"
      )
    }
    val undefs = undefStack.remove(undefStack.length - 1)
    undefs.foreach { case (undef, value) =>
      if (value.isEmpty) {
        current.remove(undef)
      } else {
        current(undef) = value.get
      }
    }
  }

  /** Ends all currently nested groups (if any), restoring values before the groups began. Useful in case of an error in the middle of parsing.
    */
  def endGroups(): Unit =
    while (undefStack.nonEmpty)
      endGroup()

  /** Detect whether `name` has a definition. Equivalent to `get(name) != null`.
    */
  def has(name: String): Boolean =
    current.contains(name) || builtins.contains(name)

  /** Get the current value of a name, or `Nullable.Null` if there is no value.
    *
    * Note: Do not use `if (namespace.get(...).isDefined)` to detect whether a macro is defined, as the definition may be the empty string which evaluates to `false` in JavaScript. Use
    * `if (namespace.get(...).isDefined)` or `if (namespace.has(...))`.
    */
  def get(name: String): Nullable[Value] =
    if (current.contains(name)) {
      Nullable(current(name))
    } else if (builtins.contains(name)) {
      Nullable(builtins(name))
    } else {
      Nullable.Null
    }

  /** Set the current value of a name, and optionally set it globally too. Local set() sets the current value and (when appropriate) adds an undo operation to the undo stack. Global set() may change
    * the undo operation at every level, so takes time linear in their number. A value of Nullable.Null means to delete existing definitions.
    */
  def set(name: String, value: Nullable[Value], global: Boolean = false): Unit = {
    if (global) {
      // Global set is equivalent to setting in all groups.  Simulate this
      // by destroying any undos currently scheduled for this name,
      // and adding an undo with the *new* value (in case it later gets
      // locally reset within this environment).
      var i = 0
      while (i < undefStack.length) {
        undefStack(i).remove(name)
        i += 1
      }
      if (undefStack.nonEmpty) {
        undefStack(undefStack.length - 1)(name) = value
      }
    } else {
      // Undo this set at end of this group (possibly to `undefined`),
      // unless an undo is already in place, in which case that older
      // value is the correct one.
      if (undefStack.nonEmpty) {
        val top = undefStack(undefStack.length - 1)
        if (!top.contains(name)) {
          if (current.contains(name)) {
            top(name) = Nullable(current(name))
          } else {
            top(name) = Nullable.Null
          }
        }
      }
    }
    if (value.isEmpty) {
      current.remove(name)
    } else {
      current(name) = value.get
    }
  }
}
