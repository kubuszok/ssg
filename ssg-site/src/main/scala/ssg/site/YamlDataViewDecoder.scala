/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * YAML-to-DataView decoder for the site pipeline.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md sections 4 and 5 for design.
 *
 * Mirrors the proven decoder in ssg-mermaid (YamlDataViewDecoder.scala) — the
 * same kindlings-yaml node-tree walking logic, placed in the ssg-site package
 * so that ssg-site does not depend on ssg-mermaid.
 */
package ssg
package site

import lowlevel.Nullable

import ssg.data.DataView

import hearth.kindlings.yamlderivation.KindlingsYamlDecoder
import hearth.kindlings.yamlderivation.YamlConfig

import org.virtuslab.yaml.ConstructError
import org.virtuslab.yaml.LoadSettings
import org.virtuslab.yaml.Node
import org.virtuslab.yaml.Tag

import scala.collection.immutable.VectorMap

/** Interop boundary that decodes a YAML body into an untyped [[ssg.data.DataView]].
  *
  * Mirrors `ssg.mermaid.YamlDataViewDecoder`: a hand-written [[hearth.kindlings.yamlderivation.KindlingsYamlDecoder]] walks the YAML node tree into DataView. Scalar tags map to typed values (null /
  * boolean / integer / float / string), mappings to `VectorMap`, sequences to `Vector`.
  */
object YamlDataViewDecoder {

  /** The empty/default kindlings YAML config (no member-name remapping). */
  private given YamlConfig = YamlConfig.default

  /** Decodes a YAML node tree into the untyped [[ssg.data.DataView]] ADT.
    *
    * Scalar tags map to typed values (null / boolean / integer / float / string), mappings to a `VectorMap`, and sequences to a `Vector`.
    */
  private given dataViewDecoder: KindlingsYamlDecoder[DataView] = new KindlingsYamlDecoder[DataView] {
    def construct(node: Node)(implicit settings: LoadSettings): Either[ConstructError, DataView] =
      node match {
        case scalar: Node.ScalarNode =>
          scalar.tag match {
            case Tag.nullTag =>
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
          val decoded = sequence.nodes.iterator.map(child => construct(child)(using settings))
          foldEither(decoded).map(items => DataView.from(items.toVector))
        case mapping: Node.MappingNode =>
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

  /** Collapses an iterator of `Either` into a single `Either` of a list, short-circuiting on the first `Left`.
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

  /** Parses a YAML string into an untyped [[ssg.data.DataView]].
    *
    * A successful parse yields the decoded DataView; a parse failure degrades to [[lowlevel.Nullable.empty]].
    *
    * @param body
    *   the raw YAML text to parse
    * @return
    *   the parsed DataView, or empty on failure
    */
  def parse(body: String): Nullable[DataView] =
    KindlingsYamlDecoder.fromYamlString[DataView](body) match {
      case Right(dv) => Nullable(dv)
      case Left(_)   => Nullable.empty
    }
}
