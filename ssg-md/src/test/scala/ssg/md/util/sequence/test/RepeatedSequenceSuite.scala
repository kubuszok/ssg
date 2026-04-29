/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package test

/*
 * Copyright (c) 2016-2016 Vladimir Schneider <vladimir.schneider@gmail.com>
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

final class RepeatedSequenceSuite extends munit.FunSuite {

  private def testCharAt(result: String, chars: CharSequence): Unit = {
    val iMax = result.length
    var i    = 0
    while (i < iMax) {
      assertEquals(String.valueOf(chars.charAt(i)), String.valueOf(result.charAt(i)))
      i += 1
    }
  }

  private def testSubSequence(result: String, chars: CharSequence): Unit = {
    val iMax = result.length
    var i    = 0
    while (i < iMax) {
      var j = iMax - i - 1
      while (j >= 0 && j >= i) {
        assertEquals(
          result.substring(i, j),
          chars.subSequence(i, j).toString,
          s"subSequence($i,$j)"
        )
        assertEquals(
          result.subSequence(i, j).hashCode(),
          chars.subSequence(i, j).hashCode(),
          s"subSequence($i,$j).hashCode()"
        )
        assertEquals(
          true,
          chars.subSequence(i, j).equals(result.subSequence(i, j)),
          s"subSequence($i,$j).equals()"
        )
        j -= 1
      }
      i += 1
    }
  }

  test("basic") {
    val orig = "abcdef"

    val test   = RepeatedSequence.repeatOf(orig, 2)
    val result = orig + orig
    assertEquals(test.toString, result)
    assertEquals(test.length(), result.length)
    assertEquals(test.hashCode(), result.hashCode())
    testSubSequence(result, test)
    testCharAt(result, test)
  }

  test("partial") {
    val orig = "abcdef"

    var test   = RepeatedSequence.repeatOf(orig, 3, orig.length + 3)
    var result = orig.substring(3) + orig.substring(0, 3)
    assertEquals(test.toString, result)
    assertEquals(test.length(), result.length)
    assertEquals(test.hashCode(), result.hashCode())
    testSubSequence(result, test)
    testCharAt(result, test)

    test = RepeatedSequence.repeatOf(orig, 3, orig.length + 5)
    result = orig.substring(3) + orig.substring(0, 5)
    assertEquals(test.toString, result)
    assertEquals(test.length(), result.length)
    assertEquals(test.hashCode(), result.hashCode())
    testSubSequence(result, test)
    testCharAt(result, test)
  }
}
