/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Declaration and property hoisting passes for the JavaScript compressor.
 *
 * Provides two scope-level transformations:
 *   - hoistDeclarations: hoists `var` declarations to function top (when hoist_vars
 *     is enabled) and hoists function declarations (when hoist_funs is enabled)
 *   - hoistProperties: splits `obj.a`/`obj.b` assignments into individual vars when
 *     safe (when hoist_props is enabled)
 *
 * Original source: terser lib/compress/index.js (lines 739-941)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: hoist_declarations -> hoistDeclarations, hoist_properties -> hoistProperties,
 *     to_assignments -> toAssignments, args_as_names -> argsAsNames
 *   Convention: Object with methods taking AstScope as first parameter instead of
 *     DEFMETHOD on AST_Scope
 *   Idiom: boundary/break instead of return, mutable.Map instead of JS Map
 *
 * Covenant: full-port
 * Covenant-js-reference: terser lib/compress/index.js (lines 739-941)
 * Covenant-verified: 2026-04-26
 */
package ssg
package js
package compress

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import ssg.js.ast.*
import ssg.js.compress.Common.{ getSimpleKey, makeSequence }
import ssg.js.scope.{ ScopeAnalysis, SymbolDef }

/** Hoisting utilities for the JavaScript compressor. */
object Hoisting {

  // -------------------------------------------------------------------------
  // Helper: to_assignments for AstDefinitions
  // -------------------------------------------------------------------------

  /** Convert variable definitions to a sequence of assignments.
    *
    * Each definition with a value becomes `name = value`. Definitions without values are skipped. Returns null if no assignments are generated.
    *
    * This updates the SymbolDef's `eliminated` and `replaced` counters and sets `fixed = false` when `reduce_vars` is enabled.
    */
  def toAssignments(defs: AstDefinitions, compressor: CompressorLike): AstNode | Null = {
    val reduceVars  = compressor.optionBool("reduce_vars")
    val assignments = ArrayBuffer.empty[AstNode]

    var i = 0
    while (i < defs.definitions.size) {
      val d = defs.definitions(i).asInstanceOf[AstVarDef]
      if (d.value != null && d.name != null) {
        val nameNode = d.name.nn
        nameNode match {
          case sym: AstSymbol =>
            // Create AstSymbolRef pointing to the same definition
            val ref = new AstSymbolRef
            ref.name = sym.name
            ref.scope = sym.scope
            ref.thedef = sym.thedef
            ref.start = sym.start
            ref.end = sym.end

            // Create the assignment
            val assign = new AstAssign
            assign.operator = "="
            assign.left = ref
            assign.right = d.value.nn
            assign.start = d.start
            assign.end = d.end
            assignments.addOne(assign)

            // Update SymbolDef counters
            if (reduceVars && sym.thedef != null) {
              sym.thedef.asInstanceOf[SymbolDef].fixed = false
            }
            val theDef = sym.definition()
            if (theDef != null) {
              theDef.eliminated += 1
              theDef.replaced -= 1
            }
          case dest: AstDestructuring =>
            // Handle destructuring patterns: { a, b } = value or [a, b] = value
            // Create assignment with the destructuring pattern as the LHS
            val assign = new AstAssign
            assign.operator = "="
            assign.left = dest
            assign.right = d.value.nn
            assign.start = d.start
            assign.end = d.end
            assignments.addOne(assign)

            // Update SymbolDef counters for all symbols in the destructuring pattern
            if (reduceVars) {
              allSymbols(dest).foreach { sym =>
                if (sym.thedef != null) {
                  sym.thedef.asInstanceOf[SymbolDef].fixed = false
                }
              }
            }
            allSymbols(dest).foreach { sym =>
              val theDef = sym.definition()
              if (theDef != null) {
                theDef.eliminated += 1
                theDef.replaced -= 1
              }
            }
          case _ =>
          // Other name types — skip
        }
      } else if (d.name != null) {
        // No value — still update eliminated counter
        d.name.nn match {
          case sym: AstSymbol =>
            val theDef = sym.definition()
            if (theDef != null) {
              theDef.eliminated += 1
              theDef.replaced -= 1
            }
          case _ =>
        }
      }
      i += 1
    }

    if (assignments.isEmpty) null
    else makeSequence(defs, assignments)
  }

  // -------------------------------------------------------------------------
  // Helper: args_as_names for AstLambda
  // -------------------------------------------------------------------------

  /** Get all parameter names from a lambda, flattening destructuring patterns.
    *
    * For simple parameters, returns the symbol itself. For destructuring patterns, returns all symbols contained within.
    */
  def argsAsNames(lambda: AstLambda): ArrayBuffer[AstSymbol] = {
    val out = ArrayBuffer.empty[AstSymbol]
    var i   = 0
    while (i < lambda.argnames.size) {
      lambda.argnames(i) match {
        case dest: AstDestructuring =>
          // Collect all symbols from the destructuring pattern
          out.addAll(allSymbols(dest))
        case sym: AstSymbol =>
          out.addOne(sym)
        case _ =>
        // Skip non-symbol, non-destructuring (e.g., expansion without symbol)
      }
      i += 1
    }
    out
  }

  /** Collect all AstSymbolDeclaration nodes from a destructuring pattern. */
  private def allSymbols(node: AstNode): ArrayBuffer[AstSymbol] = {
    val out = ArrayBuffer.empty[AstSymbol]
    walk(
      node,
      (n, _) =>
        n match {
          case sd: AstSymbolDeclaration =>
            out.addOne(sd)
            null
          case _: AstLambda =>
            true // Don't descend into nested lambdas
          case _ =>
            null
        }
    )
    out
  }

  // -------------------------------------------------------------------------
  // hoist_declarations
  // -------------------------------------------------------------------------

  /** Hoist `var` declarations and function declarations to the top of a scope.
    *
    * When `hoist_vars` is enabled and there are multiple `var` statements, all variable declarations are collected, moved to the top, and their initializers are converted to assignments in-place.
    *
    * When `hoist_funs` is enabled, function declarations are moved to the top of the scope (after directives but before other statements).
    *
    * Returns the transformed scope (may be the same instance, mutated).
    */
  def hoistDeclarations(self: AstScope, compressor: CompressorLike): AstScope = {
    if (compressor.hasDirective("use asm") != null) return self // @nowarn

    val hoistFuns = compressor.optionBool("hoist_funs")
    var hoistVars = compressor.optionBool("hoist_vars")

    if (!hoistFuns && !hoistVars) return self // @nowarn

    val dirs      = ArrayBuffer.empty[AstNode]
    val hoisted   = ArrayBuffer.empty[AstNode]
    val vars      = mutable.Map.empty[String, AstVarDef]
    var varsFound = 0
    var varDecl   = 0

    // Count var declarations first — don't hoist if there's only one
    walk(
      self,
      (node, _) =>
        node match {
          case scope: AstScope if !(scope eq self) =>
            true // Don't descend into nested scopes
          case _: AstVar =>
            varDecl += 1
            true
          case _ =>
            null
        }
    )

    hoistVars = hoistVars && varDecl > 1

    // Transform pass
    var tt: TreeTransformer = null.asInstanceOf[TreeTransformer] // @nowarn — forward reference
    tt = new TreeTransformer(
      before = (node, _) => {
        if (node eq self) {
          null // Continue into self
        } else {
          node match {
            case dir: AstDirective =>
              dirs.addOne(dir)
              val empty = new AstEmptyStatement
              empty.start = dir.start
              empty.end = dir.end
              empty

            case defun: AstDefun if hoistFuns =>
              // Only hoist if parent is self (not inside export)
              val parent         = tt.parent(0)
              val parentIsExport = parent != null && parent.nn.isInstanceOf[AstExport]
              val parentIsSelf   = parent != null && (parent.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef])
              if (!parentIsExport && parentIsSelf) {
                hoisted.addOne(defun)
                val empty = new AstEmptyStatement
                empty.start = defun.start
                empty.end = defun.end
                empty
              } else {
                node
              }

            case varNode: AstVar if hoistVars =>
              // Skip if any definition uses destructuring
              val hasDestructuring = varNode.definitions.exists {
                case vd: AstVarDef => vd.name != null && vd.name.nn.isInstanceOf[AstDestructuring]
                case _ => false
              }
              if (hasDestructuring) {
                node
              } else {
                // Collect all definitions
                var j = 0
                while (j < varNode.definitions.size) {
                  val d = varNode.definitions(j).asInstanceOf[AstVarDef]
                  if (d.name != null) {
                    d.name.nn match {
                      case sym: AstSymbol =>
                        vars(sym.name) = d
                        varsFound += 1
                      case _ =>
                    }
                  }
                  j += 1
                }

                // Convert to assignments
                val seq = toAssignments(varNode, compressor)

                // Check parent context for proper replacement
                val parent = tt.parent(0)
                parent match {
                  case forIn: AstForIn if forIn.init != null && (forIn.init.nn eq node) =>
                    // for-in/for-of init position
                    if (seq == null) {
                      // Return a bare SymbolRef for the first definition
                      val d = varNode.definitions(0).asInstanceOf[AstVarDef]
                      d.name.nn match {
                        case sym: AstSymbol =>
                          val ref = new AstSymbolRef
                          ref.name = sym.name
                          ref.scope = sym.scope
                          ref.thedef = sym.thedef
                          ref.start = sym.start
                          ref.end = sym.end
                          ref
                        case other => other
                      }
                    } else {
                      seq.nn
                    }

                  case forNode: AstFor if forNode.init != null && (forNode.init.nn eq node) =>
                    // for loop init position — seq can be null
                    seq

                  case _ =>
                    // Statement position — wrap in SimpleStatement or EmptyStatement
                    if (seq == null) {
                      val empty = new AstEmptyStatement
                      empty.start = varNode.start
                      empty.end = varNode.end
                      empty
                    } else {
                      val stmt = new AstSimpleStatement
                      stmt.body = seq.nn
                      stmt.start = varNode.start
                      stmt.end = varNode.end
                      stmt
                    }
                }
              }

            case _: AstScope =>
              node // Don't descend into nested scopes

            case _ =>
              null // Continue normal traversal
          }
        }
      }
    )

    val transformed = self.transform(tt).asInstanceOf[AstScope]

    if (varsFound > 0) {
      // Collect vars which don't appear in self's arguments list (if lambda)
      val defs     = ArrayBuffer.empty[AstVarDef]
      val isLambda = transformed.isInstanceOf[AstLambda]
      val argNames: Set[String] =
        if (isLambda) argsAsNames(transformed.asInstanceOf[AstLambda]).map(_.name).toSet
        else Set.empty

      vars.foreach { case (name, d) =>
        if (isLambda && argNames.contains(d.name.nn.asInstanceOf[AstSymbol].name)) {
          vars.remove(name)
        } else {
          // Clone the def with value = null
          val newDef = new AstVarDef
          newDef.name = d.name
          newDef.value = null
          newDef.start = d.start
          newDef.end = d.end
          defs.addOne(newDef)
          vars(name) = newDef
        }
      }

      if (defs.nonEmpty) {
        // Try to merge initial assignments back into the var declarations
        var i = 0
        while (i < transformed.body.size)
          transformed.body(i) match {
            case ss: AstSimpleStatement =>
              ss.body match {
                case assign: AstAssign if assign.operator == "=" && assign.left != null =>
                  assign.left.nn match {
                    case sym: AstSymbol if vars.contains(sym.name) =>
                      val d = vars(sym.name)
                      if (d.value != null) {
                        i = transformed.body.size // break
                      } else {
                        d.value = assign.right
                        Common.removeFromArrayBuffer(defs, d)
                        defs.addOne(d)
                        transformed.body.remove(i)
                        // Don't increment i — continue at same position
                      }
                    case _ =>
                      i = transformed.body.size // break
                  }

                case seq: AstSequence if seq.expressions.nonEmpty =>
                  seq.expressions(0) match {
                    case assign: AstAssign if assign.operator == "=" && assign.left != null =>
                      assign.left.nn match {
                        case sym: AstSymbol if vars.contains(sym.name) =>
                          val d = vars(sym.name)
                          if (d.value != null) {
                            i = transformed.body.size // break
                          } else {
                            d.value = assign.right
                            Common.removeFromArrayBuffer(defs, d)
                            defs.addOne(d)
                            // Replace sequence with remaining expressions
                            val remaining = seq.expressions.tail
                            if (remaining.nonEmpty) {
                              ss.body = makeSequence(seq, ArrayBuffer.from(remaining))
                            } else {
                              // Shouldn't happen for sequence, but handle it
                              transformed.body.remove(i)
                            }
                            // Don't increment i
                          }
                        case _ =>
                          i = transformed.body.size // break
                      }
                    case _ =>
                      i = transformed.body.size // break
                  }

                case _ =>
                  i = transformed.body.size // break
              }

            case _: AstEmptyStatement =>
              transformed.body.remove(i)
            // Don't increment i

            case block: AstBlockStatement =>
              // Splice block's body in place
              transformed.body.remove(i)
              var j = 0
              while (j < block.body.size) {
                transformed.body.insert(i + j, block.body(j))
                j += 1
              }
            // Don't increment i

            case _ =>
              i = transformed.body.size // break
          }

        // Create the combined var statement
        val varStmt = new AstVar
        varStmt.definitions = defs.asInstanceOf[ArrayBuffer[AstNode]]
        varStmt.start = transformed.start
        varStmt.end = transformed.end
        hoisted.addOne(varStmt)
      }
    }

    // Rebuild body: dirs, hoisted, remaining
    transformed.body = dirs ++ hoisted ++ transformed.body
    transformed
  }

  // -------------------------------------------------------------------------
  // hoist_properties
  // -------------------------------------------------------------------------

  /** Hoist properties from constant object literals into individual variables.
    *
    * When a `var` holds an object literal that:
    *   - Is not escaped (not passed to functions or assigned elsewhere)
    *   - Has no computed keys or spread properties
    *   - Is not retained at top-level
    *
    * Then each property access `obj.prop` is replaced with a new variable `obj_prop`, and the original object is replaced with individual var declarations for each property.
    *
    * Returns the transformed scope (may be the same instance, mutated).
    */
  def hoistProperties(self: AstScope, compressor: CompressorLike): AstScope = {
    if (!compressor.optionBool("hoist_props")) return self // @nowarn
    if (compressor.hasDirective("use asm") != null) return self // @nowarn

    // Get top_retain checker if this is a toplevel
    val topRetainFn: SymbolDef => Boolean =
      if (self.isInstanceOf[AstToplevel]) { d =>
        compressor.topRetain(d)
      } else { _ =>
        false
      }

    // Map from SymbolDef.id to Map[propertyKey, SymbolDef]
    val defsById = mutable.Map.empty[Int, mutable.Map[String, SymbolDef]]

    // First pass: identify eligible VarDefs with object literals
    var hoister: TreeTransformer = null.asInstanceOf[TreeTransformer] // @nowarn — forward ref
    hoister = new TreeTransformer(
      before = (node, descend) => {
        node match {
          case varDef: AstVarDef if varDef.name != null =>
            val sym = varDef.name.nn
            sym match {
              case symbol: AstSymbol
                  if !symbol.isInstanceOf[AstSymbolUsing]
                    && symbol.scope != null
                    && (symbol.scope.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef]) =>

                // Get symbol definition and check eligibility
                val d = symbol.definition()
                if (d != null) {
                  val theDef = d
                  // Check eligibility:
                  // - escaped != 1 (not escaping)
                  // - no assignments
                  // - no direct_access
                  // - not single_use
                  // - not exposed
                  // - not retained at top level
                  // - value is object literal without computed/spread
                  val eligible = theDef.escaped != 1 &&
                    theDef.assignments == 0 &&
                    !theDef.directAccess &&
                    theDef.singleUse == false &&
                    !compressor.exposed(theDef) &&
                    !topRetainFn(theDef)

                  if (eligible) {
                    // Check if fixed_value matches node.value and is an object literal
                    val fixedVal = symbol.fixedValue()
                    if (
                      fixedVal != null && varDef.value != null &&
                      (fixedVal.nn.asInstanceOf[AnyRef] eq varDef.value.nn.asInstanceOf[AnyRef])
                    ) {
                      fixedVal.nn match {
                        case obj: AstObject =>
                          // Check no spread or computed keys
                          val hasIneligibleProp = obj.properties.exists {
                            case _:    AstExpansion      => true
                            case prop: AstObjectProperty =>
                              prop.key match {
                                case _: AstNode =>
                                  // Computed key check
                                  prop match {
                                    case kv: AstObjectKeyVal =>
                                      kv.key.isInstanceOf[AstNode] && !kv.key.isInstanceOf[String] && {
                                        // Check if key is a simple constant
                                        kv.key match {
                                          case _: AstString                     => false // String literal — OK
                                          case _: AstNumber                     => false // Number literal — OK
                                          case _: AstSymbol if kv.quote == null => false // Identifier key — OK
                                          case _ => true // Computed
                                        }
                                      }
                                    case _ => true // Method, getter, setter
                                  }
                                case _ => false
                              }
                            case _ => false
                          }

                          if (!hasIneligibleProp) {
                            // Descend into children first
                            descend()

                            // Create new variable definitions for each property
                            val defs       = mutable.Map.empty[String, SymbolDef]
                            val newVarDefs = ArrayBuffer.empty[AstVarDef]

                            var i = 0
                            while (i < obj.properties.size) {
                              obj.properties(i) match {
                                case prop: AstObjectKeyVal =>
                                  val key = prop.key match {
                                    case s:     String    => s
                                    case sym:   AstSymbol => sym.name
                                    case s:     AstString => s.value
                                    case n:     AstNumber => n.value.toString
                                    case other: AstNode   =>
                                      getSimpleKey(other) match {
                                        case s: String           => s
                                        case d: java.lang.Double => d.toString
                                        case _ => null.asInstanceOf[String] // Skip
                                      }
                                  }
                                  if (key != null) {
                                    // Create a new symbol for this property
                                    val tentativeName = symbol.name + "_" + key
                                    val newSymName    = makeUniqueName(self, tentativeName, symbol)

                                    val newSym = symbol match {
                                      case _: AstSymbolVar =>
                                        val s = new AstSymbolVar
                                        s.name = newSymName
                                        s.scope = symbol.scope
                                        s.start = symbol.start
                                        s.end = symbol.end
                                        s
                                      case _: AstSymbolConst =>
                                        val s = new AstSymbolConst
                                        s.name = newSymName
                                        s.scope = symbol.scope
                                        s.start = symbol.start
                                        s.end = symbol.end
                                        s
                                      case _ =>
                                        val s = new AstSymbolVar
                                        s.name = newSymName
                                        s.scope = symbol.scope
                                        s.start = symbol.start
                                        s.end = symbol.end
                                        s
                                    }

                                    // Define the variable
                                    val newDef = ScopeAnalysis.defVariable(self, newSym, prop.value)
                                    ScopeAnalysis.markEnclosed(newSym)

                                    defs(key) = newDef

                                    // Create VarDef for this property
                                    val vd = new AstVarDef
                                    vd.name = newSym
                                    vd.value = prop.value
                                    vd.start = varDef.start
                                    vd.end = varDef.end
                                    newVarDefs.addOne(vd)
                                  }
                                case _ =>
                                  () // Skip non-key-val properties
                              }
                              i += 1
                            }

                            if (newVarDefs.nonEmpty) {
                              defsById(theDef.id) = defs
                              // Return the splice result — we need to replace this VarDef with multiple
                              // Since TreeTransformer doesn't support splice, we'll return null to remove
                              // this node and handle the insertion separately
                              SpliceMarker(newVarDefs)
                            } else {
                              null
                            }
                          } else {
                            null // hasIneligibleProp is true
                          }
                        case _ =>
                          null // Not an object literal
                      }
                    } else {
                      null // fixedVal or value conditions not met
                    }
                  } else {
                    null // not eligible
                  }
                } else {
                  null // theDef is null
                }
              case _ =>
                null // Not a simple symbol
            }

          case propAccess: AstPropAccess
              if propAccess.expression != null
                && propAccess.expression.nn.isInstanceOf[AstSymbolRef] =>
            val symRef = propAccess.expression.nn.asInstanceOf[AstSymbolRef]
            val theDef = symRef.definition()
            if (theDef != null && defsById.contains(theDef.id)) {
              val defs   = defsById(theDef.id)
              val keyStr = propAccess.property match {
                case s:    String    => s
                case sym:  AstSymbol => sym.name
                case node: AstNode   =>
                  getSimpleKey(node) match {
                    case s: String           => s
                    case d: java.lang.Double => d.toString
                    case _ => null.asInstanceOf[String]
                  }
              }
              if (keyStr != null && defs.contains(keyStr)) {
                val propDef = defs(keyStr)
                val newRef  = new AstSymbolRef
                newRef.name = propDef.name
                newRef.scope = symRef.scope
                newRef.thedef = propDef
                newRef.start = propAccess.start
                newRef.end = propAccess.end
                propDef.references.addOne(newRef)
                ScopeAnalysis.markEnclosed(newRef)
                newRef
              } else {
                null
              }
            } else {
              null
            }

          case _ =>
            null
        }
      }
    )

    // We need a custom transform that handles the splice markers
    transformWithSplice(self, hoister)
  }

  /** Marker class for splice operations during transform. */
  final private case class SpliceMarker(nodes: ArrayBuffer[AstVarDef]) extends AstNode {
    def nodeType: String = "SpliceMarker"
  }

  /** Transform a scope, handling SpliceMarker nodes by expanding them into the parent. */
  private def transformWithSplice(scope: AstScope, hoister: TreeTransformer): AstScope = {
    // Transform the body, handling splice markers
    val newBody = ArrayBuffer.empty[AstNode]

    var i = 0
    while (i < scope.body.size) {
      val stmt = scope.body(i)
      stmt match {
        case varNode: AstVar =>
          // Transform definitions, handling splices
          val newDefs = ArrayBuffer.empty[AstNode]
          var j       = 0
          while (j < varNode.definitions.size) {
            val d           = varNode.definitions(j)
            val transformed = d.transform(hoister)
            transformed match {
              case SpliceMarker(nodes) =>
                newDefs.addAll(nodes)
              case null =>
              // Removed
              case other =>
                newDefs.addOne(other)
            }
            j += 1
          }
          if (newDefs.nonEmpty) {
            varNode.definitions = newDefs
            newBody.addOne(varNode)
          }

        case other =>
          val transformed = other.transform(hoister)
          if (transformed != null && !transformed.isInstanceOf[SpliceMarker]) {
            newBody.addOne(transformed)
          }
      }
      i += 1
    }

    scope.body = newBody
    scope
  }

  /** Generate a unique name that doesn't conflict with existing definitions. */
  private def makeUniqueName(scope: AstScope, tentativeName: String, source: AstSymbol): String = {
    // Clean the name to be a valid identifier
    // First char must be [a-zA-Z_$], rest must be [a-zA-Z0-9_$]
    val name = tentativeName.replaceAll("^[^a-zA-Z_$]", "_").replaceAll("[^a-zA-Z0-9_$]", "_")

    // Check for conflicts in the scope chain
    var candidate = name
    var counter   = 0
    while (
      ScopeAnalysis.conflictingDef(scope, candidate) ||
      scope.variables.contains(candidate)
    ) {
      counter += 1
      candidate = name + "$" + counter
    }
    candidate
  }
}
