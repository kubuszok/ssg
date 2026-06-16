/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagram-api/frontmatter.ts
 *   (the `yaml.load(matches[1], { schema: yaml.JSON_SCHEMA })` untyped load)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: js-yaml's untyped `yaml.load` returns plain `any`. This object
 *     is the Scala interop boundary that reproduces that behaviour: it asks
 *     kindlings-yaml-derivation to decode an OPEN-ENDED frontmatter body into
 *     the SSG-native untyped [[ssg.data.DataView]] ADT. The decoder is a
 *     hand-written [[hearth.kindlings.yamlderivation.KindlingsYamlDecoder]]
 *     that walks the underlying YAML node tree; the engine's node type appears
 *     ONLY inside this decoder (the analogue of js-yaml's internal
 *     representation) and never leaks into the frontmatter model. Mapping of
 *     scalar nodes to typed DataView values (Boolean / Long / Double / String)
 *     mirrors js-yaml's `JSON_SCHEMA` typing under the untyped load.
 *   Idiom: parse failures degrade to Nullable.empty, mirroring `yaml.load(..) ?? {}`.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-06-17
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid

import lowlevel.Nullable

import ssg.data.DataView

import hearth.kindlings.yamlderivation.KindlingsYamlDecoder
import hearth.kindlings.yamlderivation.YamlConfig

import org.virtuslab.yaml.ConstructError
import org.virtuslab.yaml.LoadSettings
import org.virtuslab.yaml.Node
import org.virtuslab.yaml.Tag

import scala.collection.immutable.VectorMap

/** Interop boundary that decodes a YAML frontmatter body into an untyped [[ssg.data.DataView]].
  *
  * Reproduces js-yaml's untyped `yaml.load` (`frontmatter.ts:34-38`) by delegating to kindlings-yaml-derivation with a hand-written [[hearth.kindlings.yamlderivation.KindlingsYamlDecoder]] that walks
  * the YAML node tree into DataView.
  */
object YamlDataViewDecoder {

  /** The empty/default kindlings YAML config (no member-name remapping). */
  private given YamlConfig = YamlConfig.default

  /** Decodes a YAML node tree into the untyped [[ssg.data.DataView]] ADT.
    *
    * Mirrors `YamlDecoder.forAny`: scalar tags map to typed values (null / boolean / integer / float / string), mappings to a `VectorMap`, and sequences to a `Vector`. Unknown tags degrade to the
    * scalar's textual value, matching the untyped, schema-tolerant behaviour of js-yaml's `yaml.load`.
    */
  private given dataViewDecoder: KindlingsYamlDecoder[DataView] = new KindlingsYamlDecoder[DataView] {
    def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, DataView] =
      node match {
        // Scalars carry a Tag identifying their scalar type; mirror
        // YamlDecoder.forAny by mapping each tag to a typed DataView value
        // (the Tag branch is nested so the match stays exhaustive over the
        // three Node subtypes).
        case scalar: Node.ScalarNode =>
          scalar.tag match {
            case Tag.nullTag =>
              // null → an absent/null DataView (DataView.nil renders as "")
              Right(DataView.nil)
            case Tag.boolean =>
              summon[org.virtuslab.yaml.YamlDecoder[Boolean]].construct(scalar)(using settings).map(DataView.from)
            case Tag.int =>
              summon[org.virtuslab.yaml.YamlDecoder[Long]].construct(scalar)(using settings).map(DataView.from)
            case Tag.float =>
              summon[org.virtuslab.yaml.YamlDecoder[Double]].construct(scalar)(using settings).map(DataView.from)
            case _ =>
              Right(DataView.from(scalar.value))
          }
        case sequence: Node.SequenceNode =>
          // Decode each element; the first failure short-circuits.
          val decoded = sequence.nodes.iterator.map(child => construct(child)(using settings))
          foldEither(decoded).map(items => DataView.from(items.toVector))
        case mapping: Node.MappingNode =>
          // Decode each value, keying by the entry's scalar key text. A
          // non-scalar key is rendered via its node text (mirrors the textual
          // key coercion of an untyped object load).
          val decoded = mapping.mappings.iterator.map { case (k, v) =>
            val key = k match {
              case Node.ScalarNode(s, _) => s
              case other                 => other.toString
            }
            construct(v)(using settings).map(dv => key -> dv)
          }
          foldEither(decoded).map(entries => DataView.from(VectorMap.from(entries)))
      }
  }

  /** Collapses an iterator of `Either` into a single `Either` of a list, short-circuiting on the first `Left` (mirrors the engine's element-wise decode behaviour).
    */
  private def foldEither[A](
    eithers: Iterator[Either[ConstructError, A]]
  ): Either[ConstructError, List[A]] = {
    val builder = List.newBuilder[A]
    var failure: Nullable[ConstructError] = Nullable.empty
    while (failure.isEmpty && eithers.hasNext)
      eithers.next() match {
        case Right(a) => builder += a
        case Left(e)  => failure = Nullable(e)
      }
    failure.fold[Either[ConstructError, List[A]]](Right(builder.result()))(Left(_))
  }

  /** Parses a YAML frontmatter body into an untyped [[ssg.data.DataView]].
    *
    * Mirrors `yaml.load(matches[1], { schema: yaml.JSON_SCHEMA }) ?? {}` (`frontmatter.ts:34-38`): a successful parse yields the decoded DataView, while a parse failure degrades to
    * [[lowlevel.Nullable.empty]] (the `?? {}` fallback — an absent mapping, from which no keys can be read).
    *
    * @param body
    *   the raw YAML text between the frontmatter delimiters
    * @return
    *   the parsed DataView, or empty on failure
    */
  def parse(body: String): Nullable[DataView] =
    KindlingsYamlDecoder.fromYamlString[DataView](body) match {
      case Right(dv) => Nullable(dv)
      case Left(_)   => Nullable.empty
    }
}
