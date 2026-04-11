/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Global constant substitution for the compressor.
 *
 * Walks the AST and replaces references to global names with their constant
 * values as specified in the compressor's `global_defs` option. For example,
 * `global_defs: { DEBUG: false }` replaces all `DEBUG` references with `false`.
 *
 * Original source: terser lib/compress/global-defs.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: resolve_defines -> resolveDefs, _find_defs -> findDefs,
 *     to_node -> toNode
 *   Convention: Method dispatch via pattern matching in TreeTransformer
 *   Idiom: boundary/break instead of return, Scala Map instead of JS object
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.compress.Common.makeNodeFromConstant

/** Global definitions resolver.
  *
  * Substitutes references to global names with constant values from the `global_defs` compressor option. Supports dotted paths like `process.env.NODE_ENV` and `import.meta` prefixes.
  */
object GlobalDefs {

  /** Create a deep clone of an AST node by walking and reconstructing.
    *
    * Uses TreeTransformer to recursively visit and reconstruct the AST. Since we use the identity transform (just return null to continue), the transformer naturally creates a new tree with shared
    * leaf data but independent container structure.
    *
    * For global_defs, the values are typically simple constant trees where sharing is safe. But for complex AstNode values (functions, expressions), this ensures each insertion site gets its own
    * copy.
    */
  private def deepClone(node: AstNode): AstNode = {
    // For constant nodes, sharing is safe
    if (node.isInstanceOf[AstConstant]) return node // @nowarn

    // Use a TreeTransformer that visits every node. The transform() method
    // on each node calls transformDescend() which recursively transforms
    // children. Since we don't modify anything, this effectively makes
    // a structural copy where container nodes may get new instances while
    // leaf values are shared.
    val cloner = new TreeTransformer(
      before = (n, _) => null // continue to children
    )
    node.transform(cloner)
  }

  /** Convert a Scala value into an AST node, preserving source position from `orig`.
    *
    * Handles:
    *   - AstNode values (cloned if not constant, to avoid sharing)
    *   - Seq/Array values (converted to AstArray)
    *   - Map values (converted to AstObject with AstObjectKeyVal entries)
    *   - Primitive values (delegated to makeNodeFromConstant)
    */
  def toNode(value: Any, orig: AstNode): AstNode =
    value match {
      case node: AstNode =>
        // Non-constant nodes should not be shared in different places. Clone
        // the subtree so that each insertion site gets its own copy, avoiding
        // incorrect information during the compression phase.
        val result = if (node.isInstanceOf[AstConstant]) node else deepClone(node)
        result.start = orig.start
        result.end = orig.end
        result

      case seq: Seq[?] =>
        val arr = new AstArray
        arr.start = orig.start
        arr.end = orig.end
        arr.elements = ArrayBuffer.from(seq.map(v => toNode(v, orig)))
        arr

      case map: Map[?, ?] =>
        val props = ArrayBuffer.empty[AstNode]
        map.foreach { (k, v) =>
          val kv = new AstObjectKeyVal
          kv.start = orig.start
          kv.end = orig.end
          kv.key = k.toString
          kv.value = toNode(v, orig)
          props.addOne(kv)
        }
        val obj = new AstObject
        obj.start = orig.start
        obj.end = orig.end
        obj.properties = props
        obj

      case _ =>
        makeNodeFromConstant(value, orig)
    }

  /** Find the global definition for a node, walking up dotted property chains.
    *
    * @param node
    *   the AST node to look up
    * @param globalDefs
    *   map of dotted name -> replacement value
    * @return
    *   the replacement AST node, or null if no match
    */
  def findDefs(node: AstNode, globalDefs: Map[String, Any]): AstNode | Null =
    boundary[AstNode | Null] {
      // Build the full dotted name by walking property chains
      val suffix = node match {
        case chain: AstChain =>
          // Pass through to expression, preserving suffix (empty string at this level)
          chain.expression match {
            case null => break(null)
            case expr => break(findDefsWithSuffix(expr.nn, "", globalDefs))
          }

        case dot: AstDot =>
          val propName = dot.property match {
            case s: String => s
            case _ => break(null)
          }
          dot.expression match {
            case null => break(null)
            case expr =>
              // Recurse to build the prefix, appending ".property"
              break(findDefsWithSuffix(expr.nn, "." + propName, globalDefs))
          }

        case _: AstSymbolDeclaration =>
          // Don't substitute declarations
          break(null)

        case sr: AstSymbolRef =>
          // Check if this is a global reference using the symbol's isGlobal method.
          // A symbol is global when it was not declared in any enclosing scope.
          if (!sr.isGlobal) break(null)
          val name = sr.name
          if (globalDefs.contains(name))
            break(toNode(globalDefs(name), sr))
          else
            break(null)

        case im: AstImportMeta =>
          val name = "import.meta"
          if (globalDefs.contains(name))
            break(toNode(globalDefs(name), im))
          else
            break(null)

        case _ =>
          break(null)
      }
      suffix
    }

  /** Helper that builds dotted names with an accumulated suffix. */
  private def findDefsWithSuffix(
    node:       AstNode,
    suffix:     String,
    globalDefs: Map[String, Any]
  ): AstNode | Null =
    node match {
      case chain: AstChain =>
        chain.expression match {
          case null => null
          case expr => findDefsWithSuffix(expr.nn, suffix, globalDefs)
        }

      case dot: AstDot =>
        val propName = dot.property match {
          case s: String => s
          case _ => null // @nowarn -- non-string property
        }
        if (propName == null) null // @nowarn -- computed property
        else {
          dot.expression match {
            case null => null
            case expr => findDefsWithSuffix(expr.nn, "." + propName + suffix, globalDefs)
          }
        }

      case sr: AstSymbolRef =>
        val name = sr.name + suffix
        if (globalDefs.contains(name)) toNode(globalDefs(name), sr)
        else null

      case im: AstImportMeta =>
        val name = "import.meta" + suffix
        if (globalDefs.contains(name)) toNode(globalDefs(name), im)
        else null

      case _ => null
    }

  /** Resolve global definitions in a toplevel AST.
    *
    * Walks the entire tree and replaces global references matching entries in `globalDefs` with their constant values. Does not substitute when the reference is on the left-hand side of an
    * assignment.
    *
    * @param toplevel
    *   the AST to transform
    * @param globalDefs
    *   map of dotted name paths to replacement values
    * @return
    *   the transformed AST (same instance, mutated in place)
    */
  def resolveDefs(toplevel: AstToplevel, globalDefs: Map[String, Any]): AstToplevel =
    if (globalDefs.isEmpty) toplevel
    else {
      // Run scope analysis to determine which symbols are global
      ssg.js.scope.ScopeAnalysis.figureOutScope(toplevel)
      // Walk and replace matching global references
      var transformer: TreeTransformer = null // @nowarn -- initialized before use
      transformer = new TreeTransformer(
        before = (node, _) => {
          val replacement = findDefs(node, globalDefs)
          if (replacement == null) {
            null // continue walking
          } else {
            // Walk up the parent chain to find the outermost PropAccess
            var child: AstNode = node
            var level     = 0
            var keepGoing = true
            while (keepGoing) {
              val p: AstNode | Null = transformer.parent(level)
              p match {
                case pa: AstPropAccess if pa.expression != null && (pa.expression.nn eq child) =>
                  child = pa
                  level += 1
                case _ =>
                  keepGoing = false
              }
            }
            // Don't replace if on the left-hand side of an assignment
            val parent: AstNode | Null = transformer.parent(level)
            parent match {
              case assign: AstAssign if assign.left != null && (assign.left.nn eq child) =>
                null // skip: this is an assignment target
              case _ =>
                replacement
            }
          }
        }
      )
      // Transform the toplevel — TreeTransformer.before returns replacement nodes
      toplevel.transform(transformer).asInstanceOf[AstToplevel]
    }
}
