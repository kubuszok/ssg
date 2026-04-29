/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package test

import ssg.md.util.sequence.mappers.{ ChangeCase, NullEncoder, SpaceMapper }

final class MappedRichSequenceSuite extends munit.FunSuite {

  test("nullEncoding") {
    val input        = "\u0000\n123456789\u0000\nabcdefghij\n\u0000"
    val encodedInput = "\uFFFD\n123456789\uFFFD\nabcdefghij\n\uFFFD"

    val sequence:       IRichSequence[?] = RichSequence.of(input, 0, input.length)
    val mapEncoded:     IRichSequence[?] = sequence.toMapped(NullEncoder.encodeNull)
    val mapDecoded:     IRichSequence[?] = sequence.toMapped(NullEncoder.decodeNull)
    val encoded:        IRichSequence[?] = RichSequence.of(encodedInput, 0, encodedInput.length)
    val encodedDecoded: IRichSequence[?] = encoded.toMapped(NullEncoder.decodeNull)

    assertEquals(sequence.toString, encodedInput) // sequences encoded by default
    assertEquals(mapEncoded.toString, encodedInput)
    assertEquals(mapDecoded.toString, input)
    assertEquals(encoded.toString, encodedInput)
    assertEquals(encodedDecoded.toString, input)
  }

  test("spaceMapping") {
    val input        = "\u0020\n123456789\u0020\nabcdefghij\n\u0020"
    val encodedInput = "\u00A0\n123456789\u00A0\nabcdefghij\n\u00A0"

    val sequence:       IRichSequence[?] = RichSequence.of(input, 0, input.length)
    val mapEncoded:     IRichSequence[?] = sequence.toMapped(SpaceMapper.toNonBreakSpace)
    val mapDecoded:     IRichSequence[?] = sequence.toMapped(SpaceMapper.fromNonBreakSpace)
    val encoded:        IRichSequence[?] = RichSequence.of(encodedInput, 0, encodedInput.length)
    val encodedDecoded: IRichSequence[?] = encoded.toMapped(SpaceMapper.fromNonBreakSpace)

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
    assertEquals(mapDecoded.toString, input)
    assertEquals(encoded.toString, encodedInput)
    assertEquals(encodedDecoded.toString, input)
  }

  test("toLowerCase") {
    val input        = "This Is Mixed\n"
    val encodedInput = "this is mixed\n"

    val sequence:   IRichSequence[?] = RichSequence.of(input, 0, input.length)
    val mapEncoded: IRichSequence[?] = sequence.toMapped(ChangeCase.toLowerCase)

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
  }

  test("toUpperCase") {
    val input        = "This Is Mixed\n"
    val encodedInput = "THIS IS MIXED\n"

    val sequence:   IRichSequence[?] = RichSequence.of(input, 0, input.length)
    val mapEncoded: IRichSequence[?] = sequence.toMapped(ChangeCase.toUpperCase)

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
  }

  test("chainMapper") {
    val input        = "This Is Mixed\n"
    val encodedInput = "THIS\u00A0IS\u00A0MIXED\n"

    val sequence:   IRichSequence[?]   = RichSequence.of(input, 0, input.length)
    val mapEncoded: MappedRichSequence =
      sequence.toMapped(ChangeCase.toUpperCase.andThen(SpaceMapper.toNonBreakSpace)).asInstanceOf[MappedRichSequence]

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
    assert(mapEncoded.getBaseSequence eq sequence)
  }

  test("chainToMapped") {
    val input        = "This Is Mixed\n"
    val encodedInput = "THIS\u00A0IS\u00A0MIXED\n"

    val sequence:   IRichSequence[?]   = RichSequence.of(input, 0, input.length)
    val mapEncoded: MappedRichSequence =
      sequence.toMapped(ChangeCase.toUpperCase).toMapped(SpaceMapper.toNonBreakSpace).asInstanceOf[MappedRichSequence]

    assertEquals(sequence.toString, input)
    assertEquals(mapEncoded.toString, encodedInput)
    assert(mapEncoded.getBaseSequence eq sequence)
  }
}
