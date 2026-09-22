/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native: a boxed `0..9` is a `java.lang.Integer` here, as in java, so `ClassificationBagTest`'s two
 * tests keyed on `getClass` run on this row (see ClassificationBagByClassTests). */
package ssg
package md
package util
package collection

final class ClassificationBagByClassTest extends munit.FunSuite with ClassificationBagByClassTests
