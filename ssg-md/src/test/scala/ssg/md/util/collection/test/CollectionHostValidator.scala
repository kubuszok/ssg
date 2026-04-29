/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package collection
package test

import ssg.md.Nullable
import ssg.md.util.misc.{ Pair, Paired }

import java.util.ArrayList

/** Validation helper for CollectionHost callbacks during ordered collection tests. Ported from flexmark-util/src/test/java/com/vladsch/flexmark/util/collection/CollectionHostValidator.java
  */
class CollectionHostValidator[T] {
  private val expectedCallBacks:  ArrayList[Paired[Nullable[String], Nullable[Array[Object]]]] = new ArrayList()
  private var nextCallbackIndex:  Int                                                          = 0
  private var _modificationCount: Int                                                          = 0

  private var _trace: Boolean = false

  private var _id: String = ""

  private var _conditional: Boolean = true
  private var _repeat:      Int     = 1

  reset()

  def start(): CollectionHostValidator[T] = {
    nextCallbackIndex = 0
    _modificationCount = 0
    this._id = ""
    hadExpect()
    this
  }

  def repeat(repeat: Int): CollectionHostValidator[T] = {
    this._repeat = repeat
    this
  }

  def notrace(): CollectionHostValidator[T] = trace(false)

  def trace(): CollectionHostValidator[T] = trace(true)

  def trace(trace: Boolean): CollectionHostValidator[T] = {
    this._trace = trace
    this
  }

  def reset(): CollectionHostValidator[T] = {
    start()
    expectedCallBacks.clear()
    this
  }

  private def hadExpect(): Unit = {
    _conditional = true
    _repeat = 1
  }

  def id(id: String): CollectionHostValidator[T] = {
    this._id = id
    this
  }

  def id(id: Int): CollectionHostValidator[T] = {
    this._id = String.valueOf(id)
    this
  }

  def id(id: Object): CollectionHostValidator[T] = {
    this._id = String.valueOf(id)
    this
  }

  def onCond(conditional: Boolean): CollectionHostValidator[T] = {
    this._conditional = conditional
    this
  }

  private def expect(callBack: String, params: Object*): Unit = {
    if (_conditional) {
      var i = 0
      while (i < _repeat) {
        expectedCallBacks.add(new Pair[String, Array[Object]](Nullable(callBack), Nullable(params.toArray)))
        i += 1
      }
    }
    hadExpect()
  }

  def test(testFn: Runnable): CollectionHostValidator[T] = {
    if (_trace) {
      System.out.println(expectations())
    }

    testFn.run()
    validate()
    start()
    this
  }

  private def hostName(host: String): String =
    if (host.trim.isEmpty) "" else "" + host + "."

  private def idStr(): String =
    if (_id.trim.isEmpty) "" else "[" + _id + "] "

  def expectAdding(index: Int, s: T, v: Object): CollectionHostValidator[T] =
    expectAddingFrom("", index, s, v)

  def expectAddingNull(index: Int): CollectionHostValidator[T] =
    expectAddingNullFrom("", index)

  def expectRemoving(index: Int, s: T): CollectionHostValidator[T] =
    expectRemovingFrom("", index, s)

  def expectClearing(): CollectionHostValidator[T] =
    expectClearingFrom("")

  def expectAddingFrom(host: String, index: Int, s: T, v: Object): CollectionHostValidator[T] = {
    expect(hostName(host) + "adding", Integer.valueOf(index), s.asInstanceOf[Object], v)
    this
  }

  def expectAddingNullFrom(host: String, index: Int): CollectionHostValidator[T] = {
    expect(hostName(host) + "addingNull", Integer.valueOf(index))
    this
  }

  def expectRemovingFrom(host: String, index: Int, s: T): CollectionHostValidator[T] = {
    expect(hostName(host) + "removing", Integer.valueOf(index), s.asInstanceOf[Object])
    this
  }

  def expectClearingFrom(host: String): CollectionHostValidator[T] = {
    expect(hostName(host) + "clearing")
    this
  }

  def expectations(): String = {
    val out = new StringBuilder()

    out.append("\n").append(idStr()).append("Expected callbacks").append(":\n")
    for (i <- 0 until expectedCallBacks.size()) {
      val pair     = expectedCallBacks.get(i)
      val expected = prepareMessage(pair.first.get, pair.second.get*)
      out.append("    [").append(i).append("]:").append(expected).append("\n")
    }
    out.toString
  }

  def validate(): CollectionHostValidator[T] = {
    if (nextCallbackIndex < expectedCallBacks.size()) {
      val out = new StringBuilder()

      out.append("\n").append(idStr()).append("Missing callbacks").append(":\n")
      for (i <- nextCallbackIndex until expectedCallBacks.size()) {
        val pair     = expectedCallBacks.get(i)
        val expected = prepareMessage(pair.first.get, pair.second.get*)
        out.append("    [").append(i).append("]:").append(expected).append("\n")
      }

      assert(false, out.toString)
    }
    start()
    this
  }

  def getHost: CollectionHost[T] = getHost("")

  def getHost(host: String): CollectionHost[T] = {
    val self = this
    new CollectionHost[T] {
      override def adding(index: Int, s: Nullable[T], v: Nullable[Object]): Unit = {
        self.validateCallback(hostName(host) + "adding", Integer.valueOf(index), s.asInstanceOf[Object], v.asInstanceOf[Object])
        self._modificationCount += 1
      }

      override def removing(index: Int, s: Nullable[T]): Nullable[Object] = {
        self.validateCallback(hostName(host) + "removing", Integer.valueOf(index), s.asInstanceOf[Object])
        self._modificationCount += 1
        Nullable(null)
      }

      override def clearing(): Unit = {
        self.validateCallback(hostName(host) + "clearing")
        self._modificationCount += 1
      }

      override def addingNulls(index: Int): Unit = {
        self.validateCallback(hostName(host) + "addingNull", Integer.valueOf(index))
        self._modificationCount += 1
      }

      override def skipHostUpdate(): Boolean = false

      override def getIteratorModificationCount: Int = self._modificationCount
    }
  }

  private def isNullableEmpty(param: Object): Boolean =
    // Nullable.empty is represented as NestedNone at runtime
    param != null && param.getClass.getName.contains("NestedNone")

  private def prepareMessage(callBack: String, params: Object*): String = {
    val out = new StringBuilder()
    out.append(' ').append(callBack).append('(')
    var first = true
    for (param <- params) {
      if (first) first = false
      else out.append(", ")
      if (param == null || isNullableEmpty(param)) out.append("null") // @nowarn — mirrors Java original null handling and Nullable.empty
      else {
        val className = param.getClass.getName
        val pkgName   = param.getClass.getPackage.getName
        out.append(className.substring(pkgName.length + 1)).append(' ').append(param)
      }
    }
    out.append(')')
    out.toString
  }

  private def validateCallback(callBack: String, params: Object*): Unit = {
    val actual = prepareMessage(callBack, params*)
    val index  = nextCallbackIndex

    if (_trace) {
      System.out.println(idStr() + "actual callback[" + index + "] " + actual)
    }

    if (nextCallbackIndex >= expectedCallBacks.size()) {
      nextCallbackIndex += 1
      throw new IllegalStateException(idStr() + "un-expected callback[" + (nextCallbackIndex - 1) + "]" + actual)
    }

    val pair = expectedCallBacks.get(nextCallbackIndex)
    nextCallbackIndex += 1
    val expected = prepareMessage(pair.first.get, pair.second.get*)

    assert(expected == actual, idStr() + "callback[" + index + "] mismatch, expected:" + expected + " actual:" + actual)
  }
}
