/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Main Compressor class: the orchestrator for all JS optimization passes.
 *
 * Implements the multi-pass optimization loop that walks the AST and
 * dispatches to per-node-type optimizers. Each node type has a dedicated
 * optimization function registered via the OPT pattern (Terser's
 * `def_optimize` macro).
 *
 * The compressor extends TreeWalker (not TreeTransformer) — the `before`
 * callback performs descent + optimization, returning the optimized node
 * to replace the original in the parent.
 *
 * Ported from: terser lib/compress/index.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: Compressor -> Compressor (same), def_optimize -> optimizeNode
 *     pattern match, OPT() macro -> optimizeNode() method dispatch,
 *     option() -> option(), compress() -> compress(), before() -> before()
 *   Convention: Class with CompressorLike trait, pattern matching dispatch
 *   Idiom: boundary/break instead of return, match/case instead of
 *     DEFMETHOD + instanceof chains
 *   Gap: Multi-pass convergence loop stubbed — TerserSuite compression tests
 *     are disabled because the loop hangs. Single-pass orchestration only.
 *     Pure-call elision and global hoisting gated on SymbolDef integration.
 *     See ISS-031, ISS-032. docs/architecture/terser-port.md.
 *   Hoisting: hoist_declarations (ISS-129) and hoist_properties (ISS-129)
 *     ported in Hoisting.scala, wired into before() method.
 *   ISS-142: optimizeUnaryPrefix unsafe_undefined_ref for void 0,
 *     optimizeNew RegExp/Function/Error/Array→Call conversion.
 *   ISS-143: optimizeSequence first_in_statement/trim_right_for_undefined/
 *     maintain_this_binding, optimizeTemplateString PrefixedTemplateString guard/
 *     length check/template-in-template flatten/3-segment folds, optimizeFunction
 *     uses_arguments guard, isNullishCheck ===null||===undefined compound form,
 *     optimizeDestructuring export check Const/Let.
 *   ISS-144: optimizeBlockStatement canExtractFromIfBlock AstUsing check.
 *   Audited: 2026-04-12 (minor_issues)
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.compress.Common.*
import ssg.js.compress.Inference.*
import ssg.js.compress.TightenBody.{ extractFromUnreachableCode, tightenBody }
import ssg.js.compress.Inline.inlineIntoSymbolRef
import ssg.js.ast.AstSize
import ssg.js.scope.{ ScopeAnalysis, SymbolDef }

/** The main JavaScript compressor.
  *
  * Performs multi-pass AST transformation to minimize JavaScript code size. Each pass walks the entire AST, applying per-node optimizations in a bottom-up fashion (children are optimized before
  * parents).
  *
  * @param options
  *   the compressor configuration
  */
class Compressor(val options: CompressorOptions) extends TreeWalker(null) with CompressorLike {

  // -----------------------------------------------------------------------
  // CompressorLike implementation
  //
  // parent(), findParent(), and hasDirective() are inherited from TreeWalker
  // which provides compatible implementations.
  // -----------------------------------------------------------------------

  override def option(name: String): Any = options.get(name)

  override def inBooleanContext(): Boolean =
    if (!optionBool("booleans")) false
    else {
      boundary[Boolean] {
        var current: AstNode = try { this.self() } catch { case _: IndexOutOfBoundsException => break(false) }
        var i = 0
        var p: AstNode | Null = parent(i)
        while (p != null) {
          val pn = p.nn
          pn match {
            case _:       AstSimpleStatement                                                                => break(true)
            case cond:    AstConditional if cond.condition.nn eq current                                    => break(true)
            case dw:      AstDWLoop if dw.condition.nn eq current                                           => break(true)
            case forNode: AstFor if forNode.condition != null && (forNode.condition.nn eq current)          => break(true)
            case ifNode:  AstIf if ifNode.condition.nn eq current                                           => break(true)
            case up:      AstUnaryPrefix if up.operator == "!" && (up.expression.nn eq current)             => break(true)
            case bin:     AstBinary if bin.operator == "&&" || bin.operator == "||" || bin.operator == "??" =>
              current = pn
            case _: AstConditional =>
              current = pn
            case _ =>
              break(false)
          }
          i += 1
          p = parent(i)
        }
        false
      }
    }

  override def in32BitContext(): Boolean =
    if (!optionBool("evaluate")) false
    else {
      boundary[Boolean] {
        var level = 0
        var node: AstNode | Null = self()
        var p: AstNode | Null = parent(level)
        while (p != null) {
          p.nn match {
            case bin: AstBinary if bitwiseBinop.contains(bin.operator) => break(true)
            case up: AstUnaryPrefix if up.operator == "~" => break(true)
            // Walk through && / || / ?? right side
            case bin: AstBinary if (bin.operator == "&&" || bin.operator == "||" || bin.operator == "??") && bin.right != null && (node.nn.asInstanceOf[AnyRef] eq bin.right.nn.asInstanceOf[AnyRef]) =>
            // Walk through ternary non-condition branches
            case cond: AstConditional if cond.condition != null && !(node.nn.asInstanceOf[AnyRef] eq cond.condition.nn.asInstanceOf[AnyRef]) =>
            // Walk through sequence tail
            case seq: AstSequence if seq.expressions.nonEmpty && (node.nn.asInstanceOf[AnyRef] eq seq.expressions.last.asInstanceOf[AnyRef]) =>
            case _ => break(false)
          }
          node = p
          level += 1
          p = parent(level)
        }
        false
      }
    }

  override def exposed(theDef: Any): Boolean = {
    val d = theDef.asInstanceOf[ssg.js.scope.SymbolDef]
    d.exportFlag != 0 ||
      (d.undeclared && d.global) ||
      (d.global && {
        val dropsFuncs = toplevel.funcs
        val dropsVars  = toplevel.vars
        if (d.orig.nonEmpty && d.orig(0).isInstanceOf[AstSymbolDefun]) !dropsFuncs
        else if (d.orig.nonEmpty && d.orig(0).isInstanceOf[AstSymbolVar]) !dropsVars
        else !dropsFuncs || !dropsVars
      })
  }

  override def pureFuncs(call: AstCall): Boolean =
    // Returns true if the call is NOT pure (i.e., has side effects)
    options.pureFuncs match {
      case Nil => false // no pure_funcs specified — all calls may have side effects
      case funcs =>
        if (call.expression == null) true
        else {
          val printed = ssg.js.output.OutputStream.printToString(call.expression.nn)
          !funcs.contains(printed)
        }
    }

  // -----------------------------------------------------------------------
  // State
  //
  // `directives` is inherited from TreeWalker (Map[String, AstNode]).
  // -----------------------------------------------------------------------

  /** The current toplevel node being compressed. */
  private var toplevelNode: Option[AstToplevel] = None

  /** Sequences limit for the sequenceize pass. */
  val sequencesLimit: Int = {
    val seq = options.sequencesLimit
    if (seq == 1) 800 else seq
  }

  /** Bitwise binary operators. */
  private val bitwiseBinop: Set[String] = Set("&", "|", "^", "<<", ">>", ">>>")

  // Initialize module mode — set "use strict" directive
  if (options.module) {
    // TreeWalker.directives maps String -> AstNode, but we need a
    // sentinel node here. Use a synthetic directive.
    val strictDirective = new AstDirective
    strictDirective.value = "use strict"
    directives("use strict") = strictDirective
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Get the current top-level AST being compressed. */
  def getToplevel: AstToplevel | Null = toplevelNode.getOrElse(null)

  /** Compress the given top-level AST.
    *
    * Runs multiple optimization passes over the AST. Each pass:
    *   1. Resolves global definitions (constant substitution)
    *   2. Figures out scope (variable resolution)
    *   3. Resets per-pass optimization flags
    *   4. Transforms the AST via the `before` callback
    *   5. Counts nodes to detect convergence (for multi-pass)
    *
    * @param ast
    *   the top-level AST to compress
    * @return
    *   the compressed AST
    */
  def compress(ast: AstToplevel): AstToplevel = {
    var toplevel = ast
    toplevelNode = Some(toplevel)

    // Resolve global definitions (e.g., DEBUG: false → replace all DEBUG refs)
    toplevel = GlobalDefs.resolveDefs(toplevel, options.globalDefs)

    // For bookmarklet mode: wrap simple statements in returns
    if (optionBool("expression")) {
      processExpression(toplevel, insert = true)
    }

    val passes   = options.passes.max(1)
    var minCount = Int.MaxValue
    var stopping = false

    // Create a TreeTransformer that delegates to the Compressor's before() callback
    val compressor = this
    val transformer = new TreeTransformer(
      before = (node, descend) => {
        compressor.before(node, (n, _) => descend())
      }
    )

    var pass = 0
    while (pass < passes) {
      // Run scope analysis before each pass
      ssg.js.scope.ScopeAnalysis.figureOutScope(toplevel)

      if (pass == 0 && options.dropConsole != DropConsoleConfig.Disabled) {
        // must be run before reduce_vars and compress pass
        toplevel = dropConsole(toplevel)
      }

      // Reset per-pass flags and run data-flow analysis
      resetOptFlags(toplevel)
      if (optionBool("reduce_vars")) {
        ReduceVars.reduceVars(toplevel, this)
      }

      // Transform pass — walks the AST, optimizing each node
      toplevelNode = Some(toplevel)
      toplevel = toplevel.transform(transformer).asInstanceOf[AstToplevel]

      // Multi-pass convergence: count AST nodes, stop when size stops shrinking
      if (passes > 1) {
        var count = 0
        ssg.js.ast.walk(toplevel, (_, _) => { count += 1; null })
        if (count < minCount) {
          minCount = count
          stopping = false
        } else if (stopping) {
          pass = passes // break
        } else {
          stopping = true
        }
      }

      pass += 1
    }

    // Unwrap returns back to simple statements for bookmarklet mode
    if (optionBool("expression")) {
      processExpression(toplevel, insert = false)
    }

    toplevelNode = None
    toplevel
  }

  /** process_expression: convert SimpleStatement↔Return for bookmarklet mode. */
  private def processExpression(scope: AstScope, insert: Boolean): Unit = {
    val self = scope
    var tt: TreeTransformer = null.asInstanceOf[TreeTransformer] // @nowarn — forward ref
    tt = new TreeTransformer(before = (node, _) => {
      if (insert && node.isInstanceOf[AstSimpleStatement]) {
        val ss = node.asInstanceOf[AstSimpleStatement]
        val ret = new AstReturn
        ret.start = ss.start
        ret.end = ss.end
        ret.value = ss.body
        ret
      } else if (!insert && node.isInstanceOf[AstReturn]) {
        val ret = node.asInstanceOf[AstReturn]
        val ss = new AstSimpleStatement
        ss.start = ret.start
        ss.end = ret.end
        ss.body = if (ret.value != null) ret.value.nn else makeVoid0(ret)
        ss
      } else if (node.isInstanceOf[AstClass] || (node.isInstanceOf[AstLambda] && !(node eq self))) {
        node // don't descend into classes/lambdas
      } else if (node.isInstanceOf[AstBlock]) {
        val block = node.asInstanceOf[AstBlock]
        val idx = block.body.size - 1
        if (idx >= 0) {
          block.body(idx) = block.body(idx).transform(tt)
        }
        node
      } else if (node.isInstanceOf[AstIf]) {
        val ifNode = node.asInstanceOf[AstIf]
        if (ifNode.body != null) ifNode.body = ifNode.body.nn.transform(tt)
        if (ifNode.alternative != null) ifNode.alternative = ifNode.alternative.nn.transform(tt)
        node
      } else {
        null // continue
      }
    })
    scope.walk(tt)
  }

  /** Optimize each element of an ArrayBuffer in place. */
  private def optimizeList(list: ArrayBuffer[AstNode]): Unit = {
    var i = 0
    while (i < list.size) {
      val child = list(i)
      val opt   = optimizeTree(child)
      if (!(opt eq child)) list(i) = opt
      i += 1
    }
  }

  /** Walk the AST bottom-up, applying optimizations to each node. Returns the optimized tree.
    */
  private def optimizeTree(node: AstNode): AstNode = {
    // Bottom-up: optimize children first, then the node itself
    node match {
      case scope: AstScope if scope.body.nonEmpty =>
        var i = 0
        while (i < scope.body.size) {
          val child = scope.body(i)
          val opt   = optimizeTree(child)
          if (!(opt eq child)) {
            scope.body(i) = opt
          }
          i += 1
        }
        // Tighten the body (dead code elimination, var joining, etc.)
        if (optionBool("dead_code") || optionBool("join_vars")) {
          tightenBody(scope.body, this)
        }
      case block: AstBlock if block.body.nonEmpty =>
        var i = 0
        while (i < block.body.size) {
          val child = block.body(i)
          val opt   = optimizeTree(child)
          if (!(opt eq child)) {
            block.body(i) = opt
          }
          i += 1
        }
      case simple: AstSimpleStatement =>
        if (simple.body != null) simple.body = optimizeTree(simple.body.nn)
      case ifNode: AstIf =>
        if (ifNode.condition != null) ifNode.condition = optimizeTree(ifNode.condition.nn)
        if (ifNode.body != null) ifNode.body = optimizeTree(ifNode.body.nn)
        if (ifNode.alternative != null) ifNode.alternative = optimizeTree(ifNode.alternative.nn)

      // Loops
      case forNode: AstFor =>
        if (forNode.init != null) forNode.init = optimizeTree(forNode.init.nn)
        if (forNode.condition != null) forNode.condition = optimizeTree(forNode.condition.nn)
        if (forNode.step != null) forNode.step = optimizeTree(forNode.step.nn)
        if (forNode.body != null) forNode.body = optimizeTree(forNode.body.nn)
      case forIn: AstForIn =>
        if (forIn.init != null) forIn.init = optimizeTree(forIn.init.nn)
        if (forIn.obj != null) forIn.obj = optimizeTree(forIn.obj.nn)
        if (forIn.body != null) forIn.body = optimizeTree(forIn.body.nn)
      case whileNode: AstWhile =>
        if (whileNode.condition != null) whileNode.condition = optimizeTree(whileNode.condition.nn)
        if (whileNode.body != null) whileNode.body = optimizeTree(whileNode.body.nn)
      case doNode: AstDo =>
        if (doNode.body != null) doNode.body = optimizeTree(doNode.body.nn)
        if (doNode.condition != null) doNode.condition = optimizeTree(doNode.condition.nn)

      // Switch
      case switchNode: AstSwitch =>
        if (switchNode.expression != null) switchNode.expression = optimizeTree(switchNode.expression.nn)
        optimizeList(switchNode.body)
      case caseNode: AstCase =>
        if (caseNode.expression != null) caseNode.expression = optimizeTree(caseNode.expression.nn)
        optimizeList(caseNode.body)
      case defaultNode: AstDefault =>
        optimizeList(defaultNode.body)

      // Try/Catch/Finally
      case tryNode: AstTry =>
        if (tryNode.body != null) tryNode.body = optimizeTree(tryNode.body.nn).asInstanceOf[AstTryBlock]
        if (tryNode.bcatch != null) tryNode.bcatch = optimizeTree(tryNode.bcatch.nn).asInstanceOf[AstCatch]
        if (tryNode.bfinally != null) tryNode.bfinally = optimizeTree(tryNode.bfinally.nn).asInstanceOf[AstFinally]
      case tryBlock: AstTryBlock =>
        optimizeList(tryBlock.body)
      case catchNode: AstCatch =>
        if (catchNode.argname != null) catchNode.argname = optimizeTree(catchNode.argname.nn)
        optimizeList(catchNode.body)
      case finallyNode: AstFinally =>
        optimizeList(finallyNode.body)

      // Exit statements
      case ret: AstReturn if ret.value != null =>
        ret.value = optimizeTree(ret.value.nn)
      case throwNode: AstThrow if throwNode.value != null =>
        throwNode.value = optimizeTree(throwNode.value.nn)

      // Labeled statement
      case labeled: AstLabeledStatement =>
        if (labeled.body != null) labeled.body = optimizeTree(labeled.body.nn)

      // With statement
      case withNode: AstWith =>
        if (withNode.expression != null) withNode.expression = optimizeTree(withNode.expression.nn)
        if (withNode.body != null) withNode.body = optimizeTree(withNode.body.nn)

      // Expressions
      case assign: AstAssign =>
        if (assign.left != null) assign.left = optimizeTree(assign.left.nn)
        if (assign.right != null) assign.right = optimizeTree(assign.right.nn)
      case binary: AstBinary =>
        if (binary.left != null) binary.left = optimizeTree(binary.left.nn)
        if (binary.right != null) binary.right = optimizeTree(binary.right.nn)
      case unary: AstUnary =>
        if (unary.expression != null) unary.expression = optimizeTree(unary.expression.nn)
      case call: AstCall =>
        if (call.expression != null) call.expression = optimizeTree(call.expression.nn)
        optimizeList(call.args)
      case cond: AstConditional =>
        if (cond.condition != null) cond.condition = optimizeTree(cond.condition.nn)
        if (cond.consequent != null) cond.consequent = optimizeTree(cond.consequent.nn)
        if (cond.alternative != null) cond.alternative = optimizeTree(cond.alternative.nn)
      case seq: AstSequence if seq.expressions.nonEmpty =>
        optimizeList(seq.expressions)

      // Classes
      case cls: AstClass =>
        if (cls.name != null) cls.name = optimizeTree(cls.name.nn)
        if (cls.superClass != null) cls.superClass = optimizeTree(cls.superClass.nn)
        optimizeList(cls.properties)
      case staticBlock: AstClassStaticBlock =>
        optimizeList(staticBlock.body)

      // Object/Array literals
      case arr: AstArray =>
        optimizeList(arr.elements)
      case obj: AstObject =>
        optimizeList(obj.properties)
      case prop: AstObjectProperty =>
        prop.key match { case k: AstNode => prop.key = optimizeTree(k); case _ => }
        if (prop.value != null) prop.value = optimizeTree(prop.value.nn)

      // Variable definitions
      case defs: AstDefinitionsLike =>
        optimizeList(defs.definitions)
      case varDef: AstVarDef =>
        if (varDef.name != null) varDef.name = optimizeTree(varDef.name.nn)
        if (varDef.value != null) varDef.value = optimizeTree(varDef.value.nn)

      // Lambda (function/arrow/accessor)
      case lambda: AstLambda =>
        if (lambda.name != null) lambda.name = optimizeTree(lambda.name.nn)
        optimizeList(lambda.argnames)
        optimizeList(lambda.body)

      // Destructuring
      case dest: AstDestructuring =>
        optimizeList(dest.names)

      // Expansion (spread)
      case exp: AstExpansion =>
        if (exp.expression != null) exp.expression = optimizeTree(exp.expression.nn)

      // Template strings
      case tmpl: AstTemplateString =>
        optimizeList(tmpl.segments)
      case ptmpl: AstPrefixedTemplateString =>
        if (ptmpl.prefix != null) ptmpl.prefix = optimizeTree(ptmpl.prefix.nn)
        if (ptmpl.templateString != null) ptmpl.templateString = optimizeTree(ptmpl.templateString.nn).asInstanceOf[AstTemplateString]

      // PropAccess
      case sub: AstSub =>
        if (sub.expression != null) sub.expression = optimizeTree(sub.expression.nn)
        sub.property match { case p: AstNode => sub.property = optimizeTree(p); case _ => }
      case dot: AstDot =>
        if (dot.expression != null) dot.expression = optimizeTree(dot.expression.nn)
      case chain: AstChain =>
        if (chain.expression != null) chain.expression = optimizeTree(chain.expression.nn)

      // Await/Yield
      case aw: AstAwait =>
        if (aw.expression != null) aw.expression = optimizeTree(aw.expression.nn)
      case yld: AstYield =>
        if (yld.expression != null) yld.expression = optimizeTree(yld.expression.nn)

      // PrivateIn
      case pi: AstPrivateIn =>
        if (pi.key != null) pi.key = optimizeTree(pi.key.nn)
        if (pi.value != null) pi.value = optimizeTree(pi.value.nn)

      // Import/Export
      case imp: AstImport =>
        if (imp.importedName != null) imp.importedName = optimizeTree(imp.importedName.nn)
        if (imp.moduleName != null) imp.moduleName = optimizeTree(imp.moduleName.nn)
      case exp: AstExport =>
        if (exp.exportedDefinition != null) exp.exportedDefinition = optimizeTree(exp.exportedDefinition.nn)
        if (exp.exportedValue != null) exp.exportedValue = optimizeTree(exp.exportedValue.nn)
        if (exp.moduleName != null) exp.moduleName = optimizeTree(exp.moduleName.nn)

      case _ => // leaf node — no children to recurse into
    }

    // Now optimize this node
    optimizeNode(node)
  }

  // -----------------------------------------------------------------------
  // TreeWalker before() callback — the core of the compressor
  // -----------------------------------------------------------------------

  /** Called before descending into each node during tree transformation.
    *
    *   1. Skip already-squeezed nodes
    *   2. For scope nodes: hoist declarations/properties
    *   3. Descend twice (matches Terser's behavior for convergence)
    *   4. Run the per-node optimizer
    *   5. For scope nodes: drop unused, descend again
    *
    * Returns the optimized node to replace the original.
    */
  def before(node: AstNode, descend: (AstNode, TreeWalker) => Unit): AstNode =
    if (hasFlag(node, SQUEEZED)) {
      node
    } else {
      var current  = node
      val wasScope = current.isInstanceOf[AstScope]

      // Hoisting passes for scope nodes (hoist_funs, hoist_vars, hoist_props)
      if (wasScope) {
        current = Hoisting.hoistProperties(current.asInstanceOf[AstScope], this)
        current = Hoisting.hoistDeclarations(current.asInstanceOf[AstScope], this)
      }

      // Descend twice for convergence (matches Terser behavior)
      descend(current, this)
      descend(current, this)

      // Per-node optimization dispatch
      val opt = optimizeNode(current)

      if (wasScope && opt.isInstanceOf[AstScope]) {
        DropUnused.dropUnused(opt.asInstanceOf[AstScope], this)
        descend(opt, this)
      }

      if (opt eq current) {
        setFlag(opt, SQUEEZED)
      }
      opt
    }

  // -----------------------------------------------------------------------
  // Per-node optimization dispatch (replaces Terser's def_optimize / OPT)
  // -----------------------------------------------------------------------

  /** Dispatch to the appropriate optimizer for the given node type.
    *
    * This is the Scala equivalent of Terser's `def_optimize` macro and the `AST_Node.optimize()` method. Each node type gets a dedicated optimization branch.
    *
    * @param node
    *   the node to optimize
    * @return
    *   the optimized node (may be same instance, a replacement, or a simplified form)
    */
  private def optimizeNode(node: AstNode): AstNode = {
    if (hasFlag(node, OPTIMIZED)) {
      node
    } else if (hasDirective("use asm") != null) {
      setFlag(node, OPTIMIZED)
      node
    } else {
      val opt = node match {
        // ---- Debugger ----
        case self: AstDebugger => optimizeDebugger(self)

        // ---- Directives ----
        case self: AstDirective => optimizeDirective(self)

        // ---- Debugger ----
        // (already matched above)

        // ---- Labeled statements ----
        case self: AstLabeledStatement => optimizeLabeledStatement(self)

        // ---- Simple statements ----
        case self: AstSimpleStatement => optimizeSimpleStatement(self)

        // ---- Loops ----
        case self: AstWhile => optimizeWhile(self)
        case self: AstDo    => optimizeDo(self)
        case self: AstFor   => optimizeFor(self)

        // ---- Conditionals ----
        case self: AstIf => optimizeIf(self)

        // ---- Try/Catch ----
        case self: AstTry => optimizeTry(self)

        // ---- Return ----
        case self: AstReturn => optimizeReturn(self)

        // ---- Import ----
        case self: AstImport => self

        // ---- Yield ----
        case self: AstYield => optimizeYield(self)

        // ---- Calls (New before Call since AstNew extends AstCall) ----
        case self: AstNew  => optimizeNew(self)
        case self: AstCall => optimizeCall(self)

        // ---- Sequence ----
        case self: AstSequence => optimizeSequence(self)

        // ---- Unary (Prefix before Postfix) ----
        case self: AstUnaryPrefix  => optimizeUnaryPrefix(self)
        case self: AstUnaryPostfix => optimizeUnaryPostfix(self)

        // ---- Definitions (VarDef before Definitions) ----
        case self: AstVarDef                                  => optimizeVarDef(self)
        case self: AstDefinitions if self.definitions.isEmpty =>
          val empty = new AstEmptyStatement
          empty.start = self.start
          empty.end = self.end
          empty

        // ---- Assignment / DefaultAssign before Binary ----
        // (AstAssign extends AstBinary, AstDefaultAssign extends AstBinary)
        case self: AstDefaultAssign => optimizeDefaultAssign(self)
        case self: AstAssign        => optimizeAssign(self)

        // ---- Binary operations ----
        case self: AstBinary => optimizeBinary(self)

        // ---- Conditional expression ----
        case self: AstConditional => optimizeConditional(self)

        // ---- Property access ----
        case self: AstDot => optimizeDot(self)
        case self: AstSub => optimizeSub(self)

        // ---- Chain ----
        case self: AstChain => optimizeChain(self)

        // ---- Symbol references (Export before Ref) ----
        // (AstSymbolExport extends AstSymbolRef)
        case self: AstSymbolExport => self
        case self: AstSymbolRef    => optimizeSymbolRef(self)

        // ---- Constants and special values ----
        case self: AstUndefined => optimizeUndefined(self)
        case self: AstInfinity  => optimizeInfinity(self)
        case self: AstNaN       => optimizeNaN(self)
        case self: AstBoolean   => optimizeBoolean(self)

        // ---- Literals ----
        case self: AstArray  => optimizeArray(self)
        case self: AstObject => optimizeObject(self)
        case self: AstRegExp => literalsInBooleanContext(self)

        // ---- Object properties (lift computed keys) ----
        case self: AstConciseMethod => optimizeConciseMethod(self)
        case self: AstObjectKeyVal  => optimizeObjectKeyVal(self)
        case self: AstObjectProperty => liftKey(self)

        // ---- Destructuring (prune unused properties) ----
        case self: AstDestructuring => optimizeDestructuring(self)

        // ---- Class (before Scope/Block since AstClass extends AstScope) ----
        case self: AstClassStaticBlock =>
          tightenBody(self.body, this)
          self
        case self: AstClass => optimizeClass(self)

        // ---- Template strings ----
        case self: AstPrefixedTemplateString => self
        case self: AstTemplateString         => optimizeTemplateString(self)

        // ---- Lambdas (Function/Arrow before Lambda) ----
        // (AstFunction extends AstLambda, AstArrow extends AstLambda)
        case self: AstFunction => optimizeFunction(self)
        case self: AstArrow    => optimizeLambda(self)
        case self: AstLambda   => optimizeLambda(self)

        // ---- Switch (before Block since AstSwitch extends AstBlock) ----
        case self: AstSwitch => optimizeSwitch(self)

        // ---- Blocks (BlockStatement before Block) ----
        case self: AstBlockStatement => optimizeBlockStatement(self)
        case self: AstBlock          => optimizeBlock(self)

        // ---- Catch-all: no optimization ----
        case self => self
      }
      setFlag(opt, OPTIMIZED)
      opt
    }
  }

  // -----------------------------------------------------------------------
  // Individual optimizers
  // -----------------------------------------------------------------------

  /** `debugger;` -> `` (empty) when drop_debugger is enabled. */
  private def optimizeDebugger(self: AstDebugger): AstNode =
    if (optionBool("drop_debugger")) {
      val empty = new AstEmptyStatement
      empty.start = self.start
      empty.end = self.end
      empty
    } else {
      self
    }

  /** Remove redundant or non-standard directives. */
  private def optimizeDirective(self: AstDirective): AstNode = {
    val validDirectives = Set("use asm", "use strict")
    if (
      optionBool("directives")
      && (!validDirectives.contains(self.value) || {
        val found = hasDirective(self.value)
        found != null && (found.nn.asInstanceOf[AnyRef] ne self.asInstanceOf[AnyRef])
      })
    ) {
      val empty = new AstEmptyStatement
      empty.start = self.start
      empty.end = self.end
      empty
    } else {
      self
    }
  }

  /** Optimize labeled statements — remove if label is unused or body is break. */
  private def optimizeLabeledStatement(self: AstLabeledStatement): AstNode = {
    // If label has no references, remove the label wrapper
    if (self.label != null) {
      self.label.nn match {
        case lbl: AstLabel if lbl.references.isEmpty =>
          // Label is unreferenced — just return the body
          return self.body // @nowarn
        case _ =>
      }
    }
    self
  }

  /** Tighten a generic block. */
  private def optimizeBlock(self: AstBlock): AstNode = {
    tightenBody(self.body, this)
    self
  }

  /** Tighten a block statement, and try to unwrap single-statement blocks. */
  private def optimizeBlockStatement(self: AstBlockStatement): AstNode = {
    tightenBody(self.body, this)
    self.body.size match {
      case 1 =>
        val stmt = self.body(0)
        // Can extract from if-block if not a let/const/using/class
        // ISS-144: Add AstUsing check
        val canExtractFromIfBlock = !stmt.isInstanceOf[AstConst] && !stmt.isInstanceOf[AstLet] &&
          !stmt.isInstanceOf[AstUsing] && !stmt.isInstanceOf[AstClass]
        val parentIsIf = try { parent(0).isInstanceOf[AstIf] } catch { case _: IndexOutOfBoundsException => false }
        if ((hasDirective("use strict") == null && parentIsIf && canExtractFromIfBlock) || canBeEvictedFromBlock(stmt)) {
          stmt
        } else {
          self
        }
      case 0 =>
        val empty = new AstEmptyStatement
        empty.start = self.start
        empty.end = self.end
        empty
      case _ =>
        self
    }
  }

  /** Optimize a lambda body — tighten body and remove "use strict" if sole statement. */
  private def optimizeLambda(self: AstLambda): AstNode = {
    tightenBody(self.body, this)
    if (optionBool("side_effects") && self.body.size == 1) {
      val directive = hasDirective("use strict")
      if (directive != null && (self.body(0) eq directive.nn)) {
        self.body.clear()
      }
    }
    self
  }

  /** Optimize a function expression — try to convert to arrow when safe.
    *
    * ISS-143 fix: Use fn.usesArguments property directly (set during scope analysis)
    * instead of walking the tree, matching original Terser behavior.
    */
  private def optimizeFunction(self: AstFunction): AstNode = {
    val base = optimizeLambda(self)
    // unsafe_arrows: convert function(){} to ()=>{} when safe
    if (optionBool("unsafe_arrows") && options.ecma >= 2015) {
      base match {
        // ISS-143: Check usesArguments property directly (matches original line 3875)
        case fn: AstFunction if !fn.isGenerator && fn.name == null && !fn.usesArguments && !fn.pinned =>
          // Check that function body doesn't use `this`
          var usesThis = false
          val tw = new TreeWalker((node, _) => {
            node match {
              case _: AstThis => usesThis = true; true
              case _: AstScope => true // don't descend into nested scopes
              case _ => null
            }
          })
          fn.walk(tw)
          if (!usesThis) {
            val arrow = new AstArrow
            arrow.start = fn.start
            arrow.end = fn.end
            arrow.body = fn.body
            arrow.argnames = fn.argnames
            arrow.isAsync = fn.isAsync
            arrow.isGenerator = fn.isGenerator
            arrow
          } else {
            base
          }
        case _ => base
      }
    } else {
      base
    }
  }

  /** Optimize simple statement — drop side-effect-free expressions. */
  private def optimizeSimpleStatement(self: AstSimpleStatement): AstNode = {
    if (optionBool("side_effects") && self.body != null) {
      val node = DropSideEffectFree.dropSideEffectFree(self.body.nn, this, firstInStatement = true)
      if (node == null) {
        val empty = new AstEmptyStatement
        empty.start = self.start
        empty.end = self.end
        return empty // @nowarn
      }
      if (!(node.nn eq self.body.nn)) {
        self.body = node.nn
      }
    }
    self
  }

  /** `while (x) { ... }` -> `for (; x; ) { ... }` when loops optimization is on. */
  private def optimizeWhile(self: AstWhile): AstNode =
    if (optionBool("loops")) {
      val forNode = new AstFor
      forNode.start = self.start
      forNode.end = self.end
      forNode.condition = self.condition
      forNode.body = self.body
      forNode.init = null
      forNode.step = null
      optimizeFor(forNode)
    } else {
      self
    }

  /** Optimize do-while loops.
    *
    * ISS-114 fix: When condition is truthy, wrap body + condition statement in
    * a for-block to preserve condition side effects (matching original).
    */
  private def optimizeDo(self: AstDo): AstNode =
    if (!optionBool("loops")) self
    else {
      // Evaluate condition (use tail_node like original)
      if (self.condition != null) {
        val condTail = self.condition.nn match {
          case seq: AstSequence if seq.expressions.nonEmpty => seq.expressions.last
          case other => other
        }
        val ev = Evaluate.evaluate(condTail, this)
        if (ev != null && (ev.asInstanceOf[AnyRef] ne condTail.asInstanceOf[AnyRef])) {
          ev match {
            case false | 0 | 0.0 | "" | null =>
              // Condition is always false — body runs exactly once
              // But only if body has no break/continue targeting this loop
              if (!Common.hasBreakOrContinue(self.asInstanceOf[AstNode & AstIterationStatement], try { parent(0) } catch { case _: IndexOutOfBoundsException => null })) {
                // Return block with body + condition for side effects
                val condStmt = new AstSimpleStatement
                condStmt.body = self.condition
                condStmt.start = self.condition.nn.start
                condStmt.end = self.condition.nn.end
                val block = new AstBlockStatement
                block.start = self.body.nn.start
                block.end = self.body.nn.end
                block.body = self.body.nn match {
                  case b: AstBlock => ArrayBuffer.from(b.body) :+ condStmt
                  case s           => ArrayBuffer(s, condStmt)
                }
                return optimizeBlockStatement(block) // @nowarn
              }
            case _ =>
              // Condition is always truthy — convert to for(;;) { body; condition; }
              // Preserve condition side effects by adding as trailing statement
              val condStmt = new AstSimpleStatement
              condStmt.body = self.condition
              condStmt.start = self.condition.nn.start
              condStmt.end = self.condition.nn.end
              val innerBlock = new AstBlockStatement
              innerBlock.start = self.body.nn.start
              innerBlock.end = self.body.nn.end
              innerBlock.body = self.body.nn match {
                case b: AstBlock => ArrayBuffer.from(b.body) :+ condStmt
                case s           => ArrayBuffer(s, condStmt)
              }
              val forNode = new AstFor
              forNode.start = self.start
              forNode.end = self.end
              forNode.body = innerBlock
              forNode.init = null
              forNode.step = null
              forNode.condition = null
              return optimizeFor(forNode) // @nowarn
          }
        }
      }
      self
    }

  /** Optimize for loops — evaluate constant conditions, dead code in body. */
  private def optimizeFor(self: AstFor): AstNode =
    if (!optionBool("loops")) self
    else {
      // Drop side-effect-free init
      if (optionBool("side_effects") && self.init != null) {
        self.init = DropSideEffectFree.dropSideEffectFree(self.init.nn, this)
      }

      // Evaluate constant condition
      if (self.condition != null) {
        val ev = Evaluate.evaluate(self.condition.nn, this)
        if (ev != null && (ev.asInstanceOf[AnyRef] ne self.condition.nn.asInstanceOf[AnyRef])) {
          ev match {
            case false | 0 | 0.0 | "" | null =>
              // Condition is always false — loop never executes
              // Keep init for side effects, drop the rest
              val empty = new AstEmptyStatement
              empty.start = self.start
              empty.end = self.end
              if (self.init != null) {
                val ss = new AstSimpleStatement
                ss.start = self.start
                ss.end = self.end
                ss.body = self.init
                return optimizeSimpleStatement(ss) // @nowarn
              }
              return empty // @nowarn
            case _ =>
              // Condition is always truthy — remove it (infinite loop)
              self.condition = null
          }
        }
      }

      // if_break_in_loop: optimize for(){ if(c) break; ... } patterns
      ifBreakInLoop(self)
    }

  /** Optimize if-statements: dead branch elimination, ternary conversion, etc. */
  private def optimizeIf(self: AstIf): AstNode = {
    // Remove empty alternative
    if (self.alternative != null && isEmpty(self.alternative)) {
      self.alternative = null
    }

    if (!optionBool("conditionals")) return self // @nowarn

    // Evaluate condition for dead branch elimination
    var cond: Any = if (self.condition != null) Evaluate.evaluate(self.condition.nn, this) else self.condition

    // If not dead_code, still replace the condition with a simpler constant form
    if (!optionBool("dead_code") && cond != null && !cond.isInstanceOf[AstNode]) {
      val orig = self.condition.nn
      self.condition = makeNodeFromConstant(cond, orig)
      self.condition = bestOfExpression(self.condition.nn, orig)
    }

    if (optionBool("dead_code")) {
      if (cond != null && cond.isInstanceOf[AstNode]) {
        cond = Evaluate.evaluate(self.condition.nn, this)
      }
      val isFalsy = cond match {
        case false | 0 | 0.0 | "" => true
        case null                  => true
        case _: AstNode            => false // could not evaluate
        case _                     => false // truthy
      }
      if (isFalsy && !cond.isInstanceOf[AstNode]) {
        // Condition is always false
        val body = ArrayBuffer.empty[AstNode]
        extractFromUnreachableCode(this, self.body.nn, body)
        val condStmt = new AstSimpleStatement; condStmt.body = self.condition; condStmt.start = self.condition.nn.start; condStmt.end = self.condition.nn.end
        body.addOne(condStmt)
        if (self.alternative != null) body.addOne(self.alternative.nn)
        val block = new AstBlockStatement; block.body = body; block.start = self.start; block.end = self.end
        return optimizeBlockStatement(block) // @nowarn
      } else if (!cond.isInstanceOf[AstNode]) {
        // Condition is always truthy
        val body = ArrayBuffer.empty[AstNode]
        val condStmt = new AstSimpleStatement; condStmt.body = self.condition; condStmt.start = self.condition.nn.start; condStmt.end = self.condition.nn.end
        body.addOne(condStmt)
        body.addOne(self.body.nn)
        if (self.alternative != null) {
          extractFromUnreachableCode(this, self.alternative.nn, body)
        }
        val block = new AstBlockStatement; block.body = body; block.start = self.start; block.end = self.end
        return optimizeBlockStatement(block) // @nowarn
      }
    }

    // Compute negation and compare sizes
    val negated = negate(self.condition.nn, false)
    val selfConditionLength = AstSize.size(self.condition.nn)
    val negatedLength = AstSize.size(negated)
    var negatedIsBest = negatedLength < selfConditionLength

    // If there's an alternative and negation is shorter, swap body and alternative
    if (self.alternative != null && negatedIsBest) {
      negatedIsBest = false // because we already do the swap
      self.condition = negated
      val tmp = self.body
      self.body = if (self.alternative != null) self.alternative.nn else { val e = new AstEmptyStatement; e.start = self.start; e.end = self.end; e }
      self.alternative = tmp
    }

    // Empty body + empty alternative → just the condition as statement
    if (isEmpty(self.body) && isEmpty(self.alternative)) {
      val ss = new AstSimpleStatement; ss.body = self.condition; ss.start = self.start; ss.end = self.end
      return optimizeSimpleStatement(ss) // @nowarn
    }

    // Both branches are simple statements → ternary
    if (self.body.isInstanceOf[AstSimpleStatement] && self.alternative != null && self.alternative.nn.isInstanceOf[AstSimpleStatement]) {
      val cond1 = new AstConditional; cond1.start = self.start; cond1.end = self.end
      cond1.condition = self.condition.nn
      cond1.consequent = self.body.asInstanceOf[AstSimpleStatement].body.nn
      cond1.alternative = self.alternative.nn.asInstanceOf[AstSimpleStatement].body.nn
      val ss = new AstSimpleStatement; ss.body = cond1; ss.start = self.start; ss.end = self.end
      return optimizeSimpleStatement(ss) // @nowarn
    }

    // Empty alternative + simple body → cond && body or !cond || body
    if (isEmpty(self.alternative) && self.body.isInstanceOf[AstSimpleStatement]) {
      if (selfConditionLength == negatedLength && !negatedIsBest &&
        self.condition.nn.isInstanceOf[AstBinary] && self.condition.nn.asInstanceOf[AstBinary].operator == "||") {
        // negated does not require additional surrounding parentheses
        negatedIsBest = true
      }
      if (negatedIsBest) {
        val bin = new AstBinary; bin.operator = "||"; bin.left = negated; bin.right = self.body.asInstanceOf[AstSimpleStatement].body.nn; bin.start = self.start; bin.end = self.end
        val ss = new AstSimpleStatement; ss.body = bin; ss.start = self.start; ss.end = self.end
        return optimizeSimpleStatement(ss) // @nowarn
      }
      val bin = new AstBinary; bin.operator = "&&"; bin.left = self.condition.nn; bin.right = self.body.asInstanceOf[AstSimpleStatement].body.nn; bin.start = self.start; bin.end = self.end
      val ss = new AstSimpleStatement; ss.body = bin; ss.start = self.start; ss.end = self.end
      return optimizeSimpleStatement(ss) // @nowarn
    }

    // Empty body + simple alternative → cond || alt.body
    if (self.body.isInstanceOf[AstEmptyStatement] && self.alternative != null && self.alternative.nn.isInstanceOf[AstSimpleStatement]) {
      val bin = new AstBinary; bin.operator = "||"; bin.left = self.condition.nn; bin.right = self.alternative.nn.asInstanceOf[AstSimpleStatement].body.nn; bin.start = self.start; bin.end = self.end
      val ss = new AstSimpleStatement; ss.body = bin; ss.start = self.start; ss.end = self.end
      return optimizeSimpleStatement(ss) // @nowarn
    }

    // Both branches are return/throw of same type → merge into single exit with ternary
    if (self.body != null && self.alternative != null) {
      (self.body.nn, self.alternative.nn) match {
        case (ret1: AstReturn, ret2: AstReturn) =>
          val tern = new AstConditional; tern.condition = self.condition.nn
          tern.consequent = if (ret1.value != null) ret1.value.nn else makeVoid0(ret1)
          tern.alternative = if (ret2.value != null) ret2.value.nn else makeVoid0(ret2)
          tern.start = self.start; tern.end = self.end
          val ret = new AstReturn; ret.value = tern; ret.start = self.start; ret.end = self.end
          return ret // @nowarn
        case (thr1: AstThrow, thr2: AstThrow) if thr1.value != null && thr2.value != null =>
          val tern = new AstConditional; tern.condition = self.condition.nn
          tern.consequent = thr1.value.nn; tern.alternative = thr2.value.nn
          tern.start = self.start; tern.end = self.end
          val thr = new AstThrow; thr.value = tern; thr.start = self.start; thr.end = self.end
          return thr // @nowarn
        case _ =>
      }
    }

    // Merge nested if: if(a) if(b) x → if(a&&b) x (when no else on either)
    if (self.body.isInstanceOf[AstIf] && !self.body.asInstanceOf[AstIf].alternative.isInstanceOf[AstNode] && self.alternative == null) {
      val innerIf = self.body.asInstanceOf[AstIf]
      val merged = new AstBinary; merged.operator = "&&"; merged.left = self.condition.nn; merged.right = innerIf.condition.nn; merged.start = self.start; merged.end = self.end
      self.condition = merged
      self.body = innerIf.body
    }

    // If body always aborts (return/throw/continue/break), hoist alternative after the if
    if (aborts(self.body) != null) {
      if (self.alternative != null) {
        val alt = self.alternative.nn
        self.alternative = null
        val block = new AstBlockStatement; block.body = ArrayBuffer(self, alt); block.start = self.start; block.end = self.end
        return optimizeBlockStatement(block) // @nowarn
      }
    }

    // If alternative always aborts, swap body/alternative, negate condition, hoist body after
    if (aborts(self.alternative) != null) {
      val bodyNode = self.body.nn
      self.body = self.alternative
      self.condition = if (negatedIsBest) negated else negate(self.condition.nn, false)
      self.alternative = null
      val block = new AstBlockStatement; block.body = ArrayBuffer(self, bodyNode); block.start = self.start; block.end = self.end
      return optimizeBlockStatement(block) // @nowarn
    }

    self
  }

  /** Optimize conditional expressions (ternary operator). */
  private def optimizeConditional(self: AstConditional): AstNode =
    if (!optionBool("conditionals")) self
    else {
      // Lift sequences from condition (This looks like lift_sequences(), should probably be under "sequences")
      self.condition.nn match {
        case seq: AstSequence =>
          val exprs    = ArrayBuffer.from(seq.expressions)
          val lastExpr = exprs.remove(exprs.size - 1)
          self.condition = lastExpr
          exprs.addOne(self)
          return makeSequence(self, exprs)
        case _ =>
      }

      // Evaluate constant condition
      val cond = self.condition.nn
      val condEv = Evaluate.evaluate(cond, this)
      if (condEv != null && (condEv.asInstanceOf[AnyRef] ne cond.asInstanceOf[AnyRef])) {
        val p = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
        condEv match {
          case false | 0 | 0.0 | "" | null =>
            // Condition is falsy — return alternative with this-binding maintenance
            return maintainThisBinding(if (p != null) p.nn else self, self, self.alternative.nn) // @nowarn
          case _ =>
            // Condition is truthy — return consequent with this-binding maintenance
            return maintainThisBinding(if (p != null) p.nn else self, self, self.consequent.nn) // @nowarn
        }
      }

      // Negate-if-shorter: if negating the condition and swapping branches is shorter, do it
      val negated = negate(cond, firstInStatement(this))
      if (bestOf(this, cond, negated) eq negated) {
        val newCond = new AstConditional
        newCond.condition = negated
        newCond.consequent = self.alternative
        newCond.alternative = self.consequent
        newCond.start = self.start
        newCond.end = self.end
        // Update self in place for subsequent optimizations
        self.condition = negated
        val tmp = self.consequent
        self.consequent = self.alternative
        self.alternative = tmp
      }

      val condition   = self.condition.nn
      val consequent  = self.consequent.nn
      val alternative = self.alternative.nn

      // x ? x : y → x || y (when condition and consequent are SymbolRef with same definition)
      (condition, consequent) match {
        case (condRef: AstSymbolRef, consRef: AstSymbolRef) =>
          val condDef = condRef.definition()
          val consDef = consRef.definition()
          if (condDef != null && consDef != null && (condDef.nn.asInstanceOf[AnyRef] eq consDef.nn.asInstanceOf[AnyRef])) {
            val binary = new AstBinary
            binary.operator = "||"
            binary.left = condition
            binary.right = alternative
            binary.start = self.start
            binary.end = self.end
            return binary // @nowarn
          }
        case _ =>
      }

      // Assign merge: c ? (a = x) : (a = y) → a = c ? x : y
      (consequent, alternative) match {
        case (asgn1: AstAssign, asgn2: AstAssign)
            if asgn1.operator == asgn2.operator
              && asgn1.logical == asgn2.logical
              && asgn1.left != null && asgn2.left != null
              && AstEquivalent.equivalentTo(asgn1.left.nn, asgn2.left.nn)
              && (!hasSideEffects(condition, this) || asgn1.operator == "=" && !hasSideEffects(asgn1.left.nn, this)) =>
          val newCond = new AstConditional
          newCond.condition = condition
          newCond.consequent = asgn1.right.nn
          newCond.alternative = asgn2.right.nn
          newCond.start = self.start
          newCond.end = self.end
          val result = new AstAssign
          result.operator = asgn1.operator
          result.left = asgn1.left
          result.logical = asgn1.logical
          result.right = newCond
          result.start = self.start
          result.end = self.end
          return result // @nowarn
        case _ =>
      }

      // x ? y(a) : y(b) → y(x ? a : b) (single-arg-diff call merge)
      (consequent, alternative) match {
        case (consCall: AstCall, altCall: AstCall)
            if consCall.args.nonEmpty
              && consCall.args.size == altCall.args.size
              && consCall.expression != null && altCall.expression != null
              && AstEquivalent.equivalentTo(consCall.expression.nn, altCall.expression.nn)
              && !hasSideEffects(condition, this)
              && !hasSideEffects(consCall.expression.nn, this)
              && consCall.nodeType == altCall.nodeType =>
          // Find single differing argument
          val argIndex = singleArgDiff(consCall.args, altCall.args)
          if (argIndex >= 0) {
            // Create a copy of the call node
            val node = new AstCall
            node.start = consCall.start
            node.end = consCall.end
            node.expression = consCall.expression
            node.args = ArrayBuffer.from(consCall.args)
            node.optional = consCall.optional
            val newArg = new AstConditional
            newArg.condition = condition
            newArg.consequent = consCall.args(argIndex)
            newArg.alternative = altCall.args(argIndex)
            newArg.start = self.start
            newArg.end = self.end
            node.args(argIndex) = newArg
            return node // @nowarn
          }
        case _ =>
      }

      // a ? b : c ? b : d → (a || c) ? b : d
      alternative match {
        case altCond: AstConditional if AstEquivalent.equivalentTo(consequent, altCond.consequent.nn) =>
          val or = new AstBinary
          or.operator = "||"
          or.left = condition
          or.right = altCond.condition.nn
          or.start = self.start
          or.end = self.end
          val result = new AstConditional
          result.condition = or
          result.consequent = consequent
          result.alternative = altCond.alternative
          result.start = self.start
          result.end = self.end
          return optimizeConditional(result) // @nowarn
        case _ =>
      }

      // a == null ? b : a → a ?? b (ECMAScript 2020+)
      if (options.ecma >= 2020 && isNullishCheck(condition, alternative)) {
        val nullish = new AstBinary
        nullish.operator = "??"
        nullish.left = alternative
        nullish.right = consequent
        nullish.start = self.start
        nullish.end = self.end
        return optimizeBinary(nullish) // @nowarn
      }

      // a ? b : (c, b) → (a || c), b
      alternative match {
        case altSeq: AstSequence if altSeq.expressions.nonEmpty && AstEquivalent.equivalentTo(consequent, altSeq.expressions.last) =>
          val or = new AstBinary
          or.operator = "||"
          or.left = condition
          or.right = makeSequence(self, ArrayBuffer.from(altSeq.expressions.dropRight(1)))
          or.start = self.start
          or.end = self.end
          val result = makeSequence(self, ArrayBuffer(or, consequent))
          return optimizeSequence(result.asInstanceOf[AstSequence]) // @nowarn
        case _ =>
      }

      // a ? b : (c && b) → (a || c) && b
      alternative match {
        case altBin: AstBinary if altBin.operator == "&&" && altBin.right != null && AstEquivalent.equivalentTo(consequent, altBin.right.nn) =>
          val or = new AstBinary
          or.operator = "||"
          or.left = condition
          or.right = altBin.left.nn
          or.start = self.start
          or.end = self.end
          val and = new AstBinary
          and.operator = "&&"
          and.left = or
          and.right = consequent
          and.start = self.start
          and.end = self.end
          return optimizeBinary(and) // @nowarn
        case _ =>
      }

      // x ? y ? z : a : a → x && y ? z : a
      consequent match {
        case consCond: AstConditional if AstEquivalent.equivalentTo(consCond.alternative.nn, alternative) =>
          val and = new AstBinary
          and.operator = "&&"
          and.left = condition
          and.right = consCond.condition.nn
          and.start = self.start
          and.end = self.end
          self.condition = and
          self.consequent = consCond.consequent
          return self // @nowarn
        case _ =>
      }

      // x ? y : y → (x, y) (when consequent equals alternative)
      if (AstEquivalent.equivalentTo(consequent, alternative)) {
        return makeSequence(self, ArrayBuffer(condition, consequent)) // @nowarn
      }

      // x ? y || z : z → x && y || z
      consequent match {
        case consOr: AstBinary if consOr.operator == "||" && consOr.right != null && AstEquivalent.equivalentTo(consOr.right.nn, alternative) =>
          val and = new AstBinary
          and.operator = "&&"
          and.left = condition
          and.right = consOr.left.nn
          and.start = self.start
          and.end = self.end
          val or = new AstBinary
          or.operator = "||"
          or.left = and
          or.right = alternative
          or.start = self.start
          or.end = self.end
          return optimizeBinary(or) // @nowarn
        case _ =>
      }

      // Full truthy/falsy handling with booleanize
      val inBool = inBooleanContext()

      // Helper: check if node is truthy (AST_True, or !0, or in boolean context any truthy constant)
      def isTrue(node: AstNode): Boolean =
        node.isInstanceOf[AstTrue] ||
          (inBool && node.isInstanceOf[AstConstant] && isTruthyValue(node.asInstanceOf[AstConstant])) ||
          (node.isInstanceOf[AstUnaryPrefix] && node.asInstanceOf[AstUnaryPrefix].operator == "!" &&
            node.asInstanceOf[AstUnaryPrefix].expression != null &&
            node.asInstanceOf[AstUnaryPrefix].expression.nn.isInstanceOf[AstConstant] &&
            !isTruthyValue(node.asInstanceOf[AstUnaryPrefix].expression.nn.asInstanceOf[AstConstant]))

      // Helper: check if node is falsy (AST_False, or !1, or in boolean context any falsy constant)
      def isFalse(node: AstNode): Boolean =
        node.isInstanceOf[AstFalse] ||
          (inBool && node.isInstanceOf[AstConstant] && !isTruthyValue(node.asInstanceOf[AstConstant])) ||
          (node.isInstanceOf[AstUnaryPrefix] && node.asInstanceOf[AstUnaryPrefix].operator == "!" &&
            node.asInstanceOf[AstUnaryPrefix].expression != null &&
            node.asInstanceOf[AstUnaryPrefix].expression.nn.isInstanceOf[AstConstant] &&
            isTruthyValue(node.asInstanceOf[AstUnaryPrefix].expression.nn.asInstanceOf[AstConstant]))

      // Helper: check if a constant has a truthy value
      def isTruthyValue(c: AstConstant): Boolean = c match {
        case n: AstNumber  => n.value != 0.0 && !n.value.isNaN
        case s: AstString  => s.value.nonEmpty
        case _: AstTrue    => true
        case _: AstFalse   => false
        case _: AstNull    => false
        case _             => true // other constants (regex, etc.) are truthy
      }

      // Helper: booleanize - ensure expression is boolean, using !! if needed
      def booleanize(node: AstNode): AstNode = {
        if (isBoolean(node)) node
        else {
          // !!expression
          val neg = new AstUnaryPrefix
          neg.operator = "!"
          neg.expression = negate(node, false)
          neg.start = node.start
          neg.end = node.end
          neg
        }
      }

      // Helper: check if expression is already boolean
      def isBoolean(node: AstNode): Boolean = node match {
        case _: AstTrue | _: AstFalse => true
        case bin: AstBinary =>
          Set("==", "!=", "===", "!==", "<", "<=", ">", ">=", "in", "instanceof").contains(bin.operator)
        case up: AstUnaryPrefix if up.operator == "!" => true
        case _ => false
      }

      if (isTrue(consequent)) {
        if (isFalse(alternative)) {
          // c ? true : false → !!c
          return booleanize(condition) // @nowarn
        }
        // c ? true : x → !!c || x
        val or = new AstBinary
        or.operator = "||"
        or.left = booleanize(condition)
        or.right = alternative
        or.start = self.start
        or.end = self.end
        return or // @nowarn
      }

      if (isFalse(consequent)) {
        if (isTrue(alternative)) {
          // c ? false : true → !c
          return booleanize(negate(condition, false)) // @nowarn
        }
        // c ? false : x → !c && x
        val and = new AstBinary
        and.operator = "&&"
        and.left = booleanize(negate(condition, false))
        and.right = alternative
        and.start = self.start
        and.end = self.end
        return and // @nowarn
      }

      if (isTrue(alternative)) {
        // c ? x : true → !c || x
        val or = new AstBinary
        or.operator = "||"
        or.left = booleanize(negate(condition, false))
        or.right = consequent
        or.start = self.start
        or.end = self.end
        return or // @nowarn
      }

      if (isFalse(alternative)) {
        // c ? x : false → !!c && x
        val and = new AstBinary
        and.operator = "&&"
        and.left = booleanize(condition)
        and.right = consequent
        and.start = self.start
        and.end = self.end
        return and // @nowarn
      }

      self
    }

  /** Helper for call argument merging: find the single differing argument index, or -1 if not exactly one differs. */
  private def singleArgDiff(a: ArrayBuffer[AstNode], b: ArrayBuffer[AstNode]): Int = {
    var diffIdx = -1
    var i = 0
    while (i < a.size) {
      if (a(i).isInstanceOf[AstExpansion]) return -1 // @nowarn
      if (!AstEquivalent.equivalentTo(a(i), b(i))) {
        if (b(i).isInstanceOf[AstExpansion]) return -1 // @nowarn
        if (diffIdx >= 0) return -1 // @nowarn  -- more than one difference
        diffIdx = i
      }
      i += 1
    }
    diffIdx
  }

  /** Optimize switch statements — constant case elimination, branch merging. */
  private def optimizeSwitch(self: AstSwitch): AstNode = {
    if (!optionBool("switches")) return self // @nowarn

    // Evaluate and simplify the switch expression
    var value: Any = self.expression.nn
    val exprEv = Evaluate.evaluate(self.expression.nn, this)
    if (exprEv != null && (exprEv.asInstanceOf[AnyRef] ne self.expression.nn.asInstanceOf[AnyRef])) {
      val orig = self.expression.nn
      self.expression = makeNodeFromConstant(exprEv, orig)
      self.expression = bestOfExpression(self.expression.nn, orig)
      value = exprEv
    }

    if (!optionBool("dead_code")) return self // @nowarn

    if (value.isInstanceOf[AstNode]) {
      value = Evaluate.evaluate(self.expression.nn, this)
      if (value != null && value.isInstanceOf[AstNode]) value = self.expression.nn // keep as-is
    }

    val decl = ArrayBuffer.empty[AstNode]
    val body = ArrayBuffer.empty[AstSwitchBranch]
    var defaultBranch: AstDefault | Null = null
    var exactMatch: AstSwitchBranch | Null = null

    // Helper: eliminate_branch — merge dead branch body into prev or extract declarations
    def eliminateBranch(branch: AstSwitchBranch, prev: AstSwitchBranch | Null = null): Unit = {
      if (prev != null && aborts(prev) == null) {
        prev.nn.body.addAll(branch.body)
      } else {
        extractFromUnreachableCode(this, branch, decl)
      }
    }

    // Helper: branches_equivalent — check if two branches have equivalent bodies
    def branchesEquivalent(branch: AstSwitchBranch, prev: AstSwitchBranch, insertBreak: Boolean): Boolean = {
      val bbody = ArrayBuffer.from(branch.body)
      val pbody = prev.body
      if (insertBreak) {
        val brk = new AstBreak
        brk.start = branch.start
        brk.end = branch.end
        bbody.addOne(brk)
      }
      if (bbody.size != pbody.size) return false // @nowarn
      val bblock = new AstBlockStatement; bblock.body = bbody; bblock.start = branch.start; bblock.end = branch.end
      val pblock = new AstBlockStatement; pblock.body = ArrayBuffer.from(pbody); pblock.start = prev.start; pblock.end = prev.end
      AstEquivalent.equivalentTo(bblock, pblock)
    }

    // Helper: statement — wrap expression in SimpleStatement
    def statement(bodyExpr: AstNode): AstSimpleStatement = {
      val ss = new AstSimpleStatement
      ss.body = bodyExpr
      ss.start = bodyExpr.start
      ss.end = bodyExpr.end
      ss
    }

    // Helper: has_nested_break — check if switch has breaks that don't target a case
    def hasNestedBreak(root: AstSwitch): Boolean = {
      var hasBreak = false
      var tw: TreeWalker = null.asInstanceOf[TreeWalker]
      tw = new TreeWalker((node, _) => {
        if (hasBreak) true
        else if (node.isInstanceOf[AstLambda]) true
        else if (node.isInstanceOf[AstSimpleStatement]) true
        else if (!isBreakTargetingSelf(node, tw)) null
        else {
          val par = tw.parent(0)
          par match {
            case sb: AstSwitchBranch if sb.body.nonEmpty && (sb.body.last eq node) =>
              null // trailing break in a case — not nested
            case _ =>
              hasBreak = true
              null
          }
        }
      })
      root.walk(tw)
      hasBreak
    }

    // Helper: is_break targeting self
    def isBreakTargetingSelf(node: AstNode, stack: TreeWalker): Boolean =
      node.isInstanceOf[AstBreak] && {
        val target = stack.loopcontrolTarget(node.asInstanceOf[AstLoopControl])
        target != null && (target.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef])
      }

    // Helper: is_inert_body — branch does not abort and body has no side effects
    def isInertBody(branch: AstSwitchBranch): Boolean =
      aborts(branch) == null && {
        val block = new AstBlockStatement; block.body = ArrayBuffer.from(branch.body); block.start = branch.start; block.end = branch.end
        !hasSideEffects(block, this)
      }

    // - compress self.body into `body`
    // - find and deduplicate default branch
    // - find the exact match (`case 1234` inside `switch(1234)`)
    var i = 0
    val len = self.body.size
    while (i < len && exactMatch == null) {
      val branch = self.body(i).asInstanceOf[AstSwitchBranch]
      var addToBody = true
      branch match {
        case d: AstDefault =>
          if (defaultBranch == null) {
            defaultBranch = d
          } else {
            eliminateBranch(d, if (body.nonEmpty) body.last else null)
          }
        case cas: AstCase if !value.isInstanceOf[AstNode] =>
          val exp = if (cas.expression != null) Evaluate.evaluate(cas.expression.nn, this) else null
          if (exp != null && !exp.isInstanceOf[AstNode] && exp != value) {
            eliminateBranch(cas, if (body.nonEmpty) body.last else null)
            addToBody = false // continue — skip body.push
          } else {
            // Check if expression with no side effects evaluates to match
            var expDeep = exp
            if (exp != null && exp.isInstanceOf[AstNode] && cas.expression != null && !hasSideEffects(cas.expression.nn, this)) {
              expDeep = Evaluate.evaluate(cas.expression.nn, this)
            }
            if (expDeep != null && !expDeep.isInstanceOf[AstNode] && expDeep == value) {
              exactMatch = cas
              if (defaultBranch != null) {
                val defaultIndex = body.indexOf(defaultBranch)
                if (defaultIndex >= 0) {
                  body.remove(defaultIndex)
                  eliminateBranch(defaultBranch.nn, if (defaultIndex > 0) body(defaultIndex - 1) else null)
                  defaultBranch = null
                }
              }
            }
          }
        case _ =>
      }
      if (addToBody) body.addOne(branch)
      i += 1
    }
    // i < len if we found an exact_match. eliminate the rest
    while (i < len) {
      eliminateBranch(self.body(i).asInstanceOf[AstSwitchBranch], if (body.nonEmpty) body.last else null)
      i += 1
    }
    self.body = ArrayBuffer.from(body)

    var defaultOrExact: AstSwitchBranch | Null = if (defaultBranch != null) defaultBranch else exactMatch
    defaultBranch = null
    exactMatch = null

    // Group equivalent branches so they will be located next to each other,
    // that way the next micro-optimization will merge them.
    // ** bail micro-optimization if not a simple switch case with breaks
    val allSimple = body.indices.forall { idx =>
      val branch = body(idx)
      ((branch.asInstanceOf[AnyRef] eq defaultOrExact.asInstanceOf[AnyRef]) || branch.isInstanceOf[AstCase] && branch.asInstanceOf[AstCase].expression != null && branch.asInstanceOf[AstCase].expression.nn.isInstanceOf[AstConstant]) &&
      (branch.body.isEmpty || aborts(branch) != null || idx == body.size - 1)
    }
    if (allSimple) {
      var gi = 0
      while (gi < body.size) {
        val branch = body(gi)
        var gj = gi + 1
        while (gj < body.size) {
          val next = body(gj)
          if (next.body.isEmpty) { gj += 1 } // skip empty fall-through
          else {
            val lastBranch = gj == body.size - 1
            val equiv = branchesEquivalent(next, branch, false)
            if (equiv || (lastBranch && branchesEquivalent(next, branch, true))) {
              if (!equiv && lastBranch) {
                val brk = new AstBreak; brk.start = next.start; brk.end = next.end
                next.body.addOne(brk)
              }
              // find previous siblings with inert fallthrough
              var x = gj - 1
              var fallthroughDepth = 0
              while (x > gi) {
                if (isInertBody(body(x))) { fallthroughDepth += 1; x -= 1 }
                else { x = gi } // break out
              }
              val plucked = body.slice(gj - fallthroughDepth, gj + 1)
              val removeIdx = gj - fallthroughDepth
              var removeCount = 1 + fallthroughDepth
              while (removeCount > 0) { body.remove(removeIdx); removeCount -= 1 }
              // Insert plucked after gi
              var insertIdx = gi + 1
              for (p <- plucked) { body.insert(insertIdx, p); insertIdx += 1 }
              gi += plucked.size
              gj = gi + 1 // restart inner loop
            } else {
              gj += 1
            }
          }
        }
        gi += 1
      }
    }

    // Merge equivalent branches in a row
    {
      var mi = 0
      while (mi < body.size) {
        val branch = body(mi)
        if (branch.body.nonEmpty && aborts(branch) != null) {
          var mj = mi + 1
          var currentBranch = branch
          while (mj < body.size) {
            val next = body(mj)
            if (next.body.isEmpty) { mi += 1; mj += 1 }
            else if (
              branchesEquivalent(next, currentBranch, false) ||
                (mj == body.size - 1 && branchesEquivalent(next, currentBranch, true))
            ) {
              currentBranch.body.clear()
              currentBranch = next
              mi += 1; mj += 1
            } else {
              mj = body.size // break
            }
          }
        }
        mi += 1
      }
    }

    // Prune any empty branches at the end of the switch statement
    {
      var pi = body.size - 1
      boundary {
        while (pi >= 0) {
          val bbody = body(pi).body
          // Pop trailing breaks targeting self
          while (bbody.nonEmpty && bbody.last.isInstanceOf[AstBreak] && {
            val brk = bbody.last.asInstanceOf[AstBreak]
            val target = loopcontrolTarget(brk)
            target != null && (target.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef])
          }) {
            bbody.remove(bbody.size - 1)
          }
          if (!isInertBody(body(pi))) break(())
          pi -= 1
        }
      }
      // i now points to the index of a branch that contains a body. By incrementing, it's
      // pointing to the first branch that's empty.
      pi += 1
      if (defaultOrExact == null || body.indexOf(defaultOrExact) >= pi) {
        // The default behavior is to do nothing. Prune side-effect-free cases.
        var pj = body.size - 1
        boundary {
          while (pj >= pi) {
            val branch = body(pj)
            if ((branch.asInstanceOf[AnyRef] eq defaultOrExact.asInstanceOf[AnyRef])) {
              defaultOrExact = null
              eliminateBranch(body.remove(body.size - 1))
            } else if (branch.isInstanceOf[AstCase] && !hasSideEffects(branch.asInstanceOf[AstCase].expression.nn, this)) {
              eliminateBranch(body.remove(body.size - 1))
            } else {
              break(())
            }
            pj -= 1
          }
        }
      }
    }

    // Prune side-effect free branches that fall into default.
    if (defaultOrExact != null) boundary {
      val defaultIndex = body.indexOf(defaultOrExact)
      var defaultBodyIndex = defaultIndex
      while (defaultBodyIndex < body.size - 1) {
        if (!isInertBody(body(defaultBodyIndex))) {
          defaultBodyIndex = body.size // will fail the check below
        }
        defaultBodyIndex += 1
      }
      // defaultBodyIndex must end at body.size - 1 or beyond
      if (defaultBodyIndex < body.size - 1) break(()) // bail

      var sideEffectIndex = body.size - 1
      boundary {
        while (sideEffectIndex >= 0) {
          val branch = body(sideEffectIndex)
          if ((branch.asInstanceOf[AnyRef] eq defaultOrExact.nn.asInstanceOf[AnyRef])) {
            sideEffectIndex -= 1
          } else if (branch.isInstanceOf[AstCase] && hasSideEffects(branch.asInstanceOf[AstCase].expression.nn, this)) {
            break(()) // found last side-effect branch
          } else {
            sideEffectIndex -= 1
          }
        }
      }
    }

    // See if we can remove the switch entirely if all cases fall into the same case body.
    if (defaultOrExact != null) boundary {
      val bi = body.indexWhere(b => !isInertBody(b))
      var caseBody: AstBlockStatement | Null = null
      if (bi == body.size - 1) {
        // All cases fall into the case body
        val branch = body(bi)
        if (hasNestedBreak(self)) break(())
        caseBody = new AstBlockStatement
        caseBody.nn.body = ArrayBuffer.from(branch.body)
        caseBody.nn.start = branch.start
        caseBody.nn.end = branch.end
        branch.body.clear()
      } else if (bi != -1) {
        break(()) // Multiple bodies, cannot optimize
      }

      val sideEffect = body.find { branch =>
        !(branch.asInstanceOf[AnyRef] eq defaultOrExact.nn.asInstanceOf[AnyRef]) &&
          branch.isInstanceOf[AstCase] && hasSideEffects(branch.asInstanceOf[AstCase].expression.nn, this)
      }
      // If no cases cause a side-effect, we can eliminate the switch entirely.
      if (sideEffect.isEmpty) {
        val stmts = ArrayBuffer.empty[AstNode]
        stmts.addAll(decl)
        stmts.addOne(statement(self.expression.nn))
        defaultOrExact.nn match {
          case c: AstCase if c.expression != null => stmts.addOne(statement(c.expression.nn))
          case _ =>
        }
        if (caseBody != null) stmts.addOne(caseBody.nn)
        val block = new AstBlockStatement
        block.body = stmts
        block.start = self.start
        block.end = self.end
        return optimizeBlockStatement(block) // @nowarn
      }

      // Remove default from body — it does nothing
      val dIdx = body.indexOf(defaultOrExact)
      if (dIdx >= 0) body.remove(dIdx)
      defaultOrExact = null

      if (caseBody != null) {
        // Recurse into switch one more time — we've pruned the default case,
        // so this recursion only happens once.
        self.body = ArrayBuffer.from(body)
        val block = new AstBlockStatement
        block.body = ArrayBuffer.from(decl)
        block.body.addOne(self)
        block.body.addOne(caseBody.nn)
        block.start = self.start
        block.end = self.end
        return optimizeBlockStatement(block) // @nowarn
      }
    }

    // Reintegrate `decl` (var statements)
    if (body.nonEmpty) {
      body(0).body.insertAll(0, decl)
    }
    self.body = ArrayBuffer.from(body)

    if (body.isEmpty) {
      val block = new AstBlockStatement
      block.body = ArrayBuffer.from(decl)
      block.body.addOne(statement(self.expression.nn))
      block.start = self.start
      block.end = self.end
      return optimizeBlockStatement(block) // @nowarn
    }

    // Single case (no default) → convert to if
    if (body.size == 1 && !hasNestedBreak(self)) {
      val branch = body(0)
      branch match {
        case cas: AstCase if cas.expression != null =>
          val cmp = new AstBinary
          cmp.operator = "==="
          cmp.left = self.expression.nn
          cmp.right = cas.expression.nn
          cmp.start = self.start
          cmp.end = self.end
          val ifNode = new AstIf
          ifNode.condition = cmp
          val thenBlock = new AstBlockStatement
          thenBlock.body = ArrayBuffer.from(cas.body)
          thenBlock.start = cas.start
          thenBlock.end = cas.end
          ifNode.body = thenBlock
          ifNode.alternative = null
          ifNode.start = self.start
          ifNode.end = self.end
          return optimizeIf(ifNode) // @nowarn
        case _ =>
      }
    }

    // Two cases with default → convert to if/else
    if (body.size == 2 && defaultOrExact != null && !hasNestedBreak(self)) {
      val branch: AstSwitchBranch = if (body(0).asInstanceOf[AnyRef] eq defaultOrExact.nn.asInstanceOf[AnyRef]) body(1) else body(0)
      val exactExp: AstSimpleStatement | Null = defaultOrExact.nn match {
        case c: AstCase if c.expression != null => statement(c.expression.nn)
        case _ => null
      }
      if (aborts(body(0)) != null) {
        // Only the first branch body could have a break (at the last statement)
        val first = body(0)
        if (first.body.nonEmpty && first.body.last.isInstanceOf[AstBreak]) {
          val brk = first.body.last.asInstanceOf[AstBreak]
          val target = loopcontrolTarget(brk)
          if (target != null && (target.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef])) {
            first.body.remove(first.body.size - 1)
          }
        }
        branch match {
          case cas: AstCase if cas.expression != null =>
            val cmp = new AstBinary; cmp.operator = "==="; cmp.left = self.expression.nn; cmp.right = cas.expression.nn; cmp.start = self.start; cmp.end = self.end
            val thenBlock = new AstBlockStatement; thenBlock.body = ArrayBuffer.from(cas.body); thenBlock.start = cas.start; thenBlock.end = cas.end
            val elseBody = ArrayBuffer.empty[AstNode]
            if (exactExp != null) elseBody.addOne(exactExp.nn)
            elseBody.addAll(defaultOrExact.nn.body)
            val elseBlock = new AstBlockStatement; elseBlock.body = elseBody; elseBlock.start = defaultOrExact.nn.start; elseBlock.end = defaultOrExact.nn.end
            val ifNode = new AstIf; ifNode.condition = cmp; ifNode.body = thenBlock; ifNode.alternative = elseBlock; ifNode.start = self.start; ifNode.end = self.end
            return optimizeIf(ifNode) // @nowarn
          case _ =>
        }
      }
      branch match {
        case cas: AstCase if cas.expression != null =>
          var operator = "==="
          var consequent: AstBlockStatement = { val b = new AstBlockStatement; b.body = ArrayBuffer.from(cas.body); b.start = cas.start; b.end = cas.end; b }
          val elseBody = ArrayBuffer.empty[AstNode]
          if (exactExp != null) elseBody.addOne(exactExp.nn)
          elseBody.addAll(defaultOrExact.nn.body)
          var always: AstBlockStatement = { val b = new AstBlockStatement; b.body = elseBody; b.start = defaultOrExact.nn.start; b.end = defaultOrExact.nn.end; b }
          if (body(0).asInstanceOf[AnyRef] eq defaultOrExact.nn.asInstanceOf[AnyRef]) {
            operator = "!=="
            val tmp = always; always = consequent; consequent = tmp
          }
          val cmp = new AstBinary; cmp.operator = operator; cmp.left = self.expression.nn; cmp.right = cas.expression.nn; cmp.start = self.start; cmp.end = self.end
          val ifNode = new AstIf; ifNode.condition = cmp; ifNode.body = consequent; ifNode.alternative = null; ifNode.start = self.start; ifNode.end = self.end
          val block = new AstBlockStatement
          block.body = ArrayBuffer(ifNode, always)
          block.start = self.start
          block.end = self.end
          return optimizeBlockStatement(block) // @nowarn
        case _ =>
      }
    }

    self
  }

  /** Optimize try-catch-finally. */
  private def optimizeTry(self: AstTry): AstNode = {
    // Remove empty finally
    if (self.bcatch != null && self.bfinally != null) {
      if (self.bfinally.nn.body.forall(isEmpty)) {
        self.bfinally = null
      }
    }

    // Remove try with empty body
    if (optionBool("dead_code") && self.body.body.forall(isEmpty)) {
      val body = ArrayBuffer.empty[AstNode]
      if (self.bcatch != null) {
        extractFromUnreachableCode(this, self.bcatch.nn, body)
      }
      if (self.bfinally != null) {
        body.addAll(self.bfinally.nn.body)
      }
      val block = new AstBlockStatement
      block.start = self.start
      block.end = self.end
      block.body = body
      block
    } else {
      self
    }
  }

  /** Optimize var definitions — remove undefined initializers for `let`. */
  private def optimizeVarDef(self: AstVarDef): AstNode = {
    if (
      self.name.isInstanceOf[AstSymbolLet]
      && self.value != null
      && isUndefined(self.value.nn, this)
    ) {
      self.value = null
    }
    self
  }

  /** Optimize return statements — remove `return undefined`. */
  private def optimizeReturn(self: AstReturn): AstNode = {
    if (self.value != null && isUndefined(self.value.nn, this)) {
      self.value = null
    }
    self
  }

  /** Optimize function calls.
    *
    * Handles unused argument trimming, built-in constructor simplification (Array, Object, String, Number, Boolean), method call optimization (toString, join, charAt, apply, call), and function
    * inlining.
    */
  /** Inline array-like spread: [...arr] where arr is an Array literal → flatten elements.
    *
    * Ported from original index.js inline_array_like_spread function. In array-like spread, spreading a non-iterable value is TypeError, so we can only optimize AST_Expansion of AST_Array without
    * holes.
    */
  private def inlineArrayLikeSpread(elements: ArrayBuffer[AstNode]): Unit = {
    var i = 0
    while (i < elements.size) {
      elements(i) match {
        case exp: AstExpansion if exp.expression != null =>
          exp.expression.nn match {
            case arr: AstArray if !arr.elements.exists(_.isInstanceOf[AstHole]) =>
              // Replace the spread with the array elements
              elements.remove(i)
              var j = 0
              while (j < arr.elements.size) {
                elements.insert(i + j, arr.elements(j))
                j += 1
              }
              // Step back one, as the element at i is now new
              i -= 1
            case _ =>
          }
        case _ =>
      }
      i += 1
    }
  }

  /** Check if expression contains optional property access or call. */
  private def containsOptional(node: AstNode): Boolean =
    node match {
      case prop: AstPropAccess =>
        prop.optional || (prop.expression != null && containsOptional(prop.expression.nn))
      case call: AstCall =>
        call.optional || (call.expression != null && containsOptional(call.expression.nn))
      case chain: AstChain =>
        // AstChain wraps optional expressions; check its inner expression
        chain.expression != null && containsOptional(chain.expression.nn)
      case _ => false
    }

  private def optimizeCall(self: AstCall): AstNode =
    if (self.expression == null) {
      self
    } else {
      val exp = self.expression.nn

      // Inline spread of array literals in args: f(...[a,b]) → f(a,b)
      inlineArrayLikeSpread(self.args)

      val simpleArgs = !self.args.exists(_.isInstanceOf[AstExpansion])

      // Resolve SymbolRef via fixedValue when reduce_vars is enabled
      var fn: AstNode = exp
      if (optionBool("reduce_vars") && fn.isInstanceOf[AstSymbolRef]) {
        fn.asInstanceOf[AstSymbolRef].fixedValue() match {
          case resolved: AstNode => fn = resolved
          case _                 =>
        }
      }

      val isFunc = fn.isInstanceOf[AstLambda]

      // Pinned functions can't be optimized
      if (isFunc && fn.asInstanceOf[AstLambda].pinned) return self // @nowarn

      // Trim unused arguments using UNUSED flag
      if (optionBool("unused") && simpleArgs && isFunc && !fn.asInstanceOf[AstLambda].usesArguments) {
        val lambda = fn.asInstanceOf[AstLambda]
        var pos = 0
        var last = 0
        var i = 0
        val len = self.args.size
        while (i < len) {
          // Check if argname at position i is AST_Expansion (rest parameter)
          if (i < lambda.argnames.size && lambda.argnames(i).isInstanceOf[AstExpansion]) {
            val restExp = lambda.argnames(i).asInstanceOf[AstExpansion]
            if (restExp.expression != null && hasFlag(restExp.expression.nn, UNUSED)) {
              // Rest param is unused — drop remaining args (keep side effects)
              while (i < len) {
                val node = DropSideEffectFree.dropSideEffectFree(self.args(i), this)
                if (node != null) {
                  self.args(pos) = node.nn
                  pos += 1
                }
                i += 1
              }
            } else {
              // Rest param is used — keep remaining args as-is
              while (i < len) {
                self.args(pos) = self.args(i)
                pos += 1
                i += 1
              }
            }
            last = pos
            // break inner loop effectively handled by while condition
          } else {
            val trim = i >= lambda.argnames.size
            val argIsUnused = !trim && hasFlag(lambda.argnames(i), UNUSED)
            if (trim || argIsUnused) {
              val dropped = DropSideEffectFree.dropSideEffectFree(self.args(i), this)
              if (dropped != null) {
                self.args(pos) = dropped.nn
                pos += 1
              } else if (!trim) {
                // Replace unused arg with 0 to preserve positional args
                val zero = new AstNumber
                zero.value = 0.0
                zero.start = self.args(i).start
                zero.end = self.args(i).end
                self.args(pos) = zero
                pos += 1
                i += 1
                last = pos
                // continue — skip the last = pos below
              }
            } else {
              self.args(pos) = self.args(i)
              pos += 1
            }
            last = pos
            i += 1
          }
        }
        // Truncate args array to last meaningful position
        while (self.args.size > last) self.args.remove(self.args.size - 1)
      }

      // console.assert(truthy) → void 0
      exp match {
        case dot: AstDot if dot.expression != null && dot.property == "assert" =>
          dot.expression.nn match {
            case ref: AstSymbolRef if ref.name == "console" && ref.definition() != null && ref.definition().nn.undeclared =>
              if (self.args.nonEmpty) {
                val condition = self.args(0)
                val ev = Evaluate.evaluate(condition, this)
                if (ev == true || ev == 1 || ev == 1.0) {
                  return makeVoid0(self) // @nowarn
                }
              }
            case _ =>
          }
        case _ =>
      }

      // Unsafe built-in constructor and method optimizations
      if (optionBool("unsafe") && !containsOptional(exp)) {
        // Array.from(arr) → arr (when arr is already an Array literal)
        exp match {
          case dot: AstDot
              if dot.expression != null && dot.expression.nn.isInstanceOf[AstSymbolRef]
                && dot.expression.nn.asInstanceOf[AstSymbolRef].name == "Array"
                && dot.property == "from"
                && self.args.size == 1 =>
            self.args(0) match {
              case arr: AstArray =>
                val result = new AstArray
                result.elements = arr.elements.clone()
                result.start = arr.start
                result.end = arr.end
                return optimizeArray(result) // @nowarn
              case _ =>
            }
          case _ =>
        }

        exp match {
          case ref: AstSymbolRef if isUndeclaredRef(ref) =>
            ref.name match {
              case "Array" =>
                if (self.args.size != 1) {
                  val arr = new AstArray
                  arr.elements = self.args.clone()
                  arr.start = self.start
                  arr.end = self.end
                  return arr // @nowarn
                } else {
                  // Array(n) where n is a small number → array of holes
                  self.args(0) match {
                    case num: AstNumber if num.value >= 0 && num.value <= 11 && num.value == num.value.toInt.toDouble =>
                      val elements = ArrayBuffer.empty[AstNode]
                      var k = 0
                      while (k < num.value.toInt) {
                        val hole = new AstHole
                        hole.start = self.start
                        hole.end = self.end
                        elements.addOne(hole)
                        k += 1
                      }
                      val arr = new AstArray
                      arr.elements = elements
                      arr.start = self.start
                      arr.end = self.end
                      return arr // @nowarn
                    case _ =>
                  }
                }
              case "Object" if self.args.isEmpty =>
                val obj = new AstObject
                obj.properties = ArrayBuffer.empty
                obj.start = self.start
                obj.end = self.end
                return obj // @nowarn
              case "String" =>
                if (self.args.isEmpty) {
                  val s = new AstString
                  s.value = ""
                  s.start = self.start
                  s.end = self.end
                  return s // @nowarn
                } else if (self.args.size <= 1) {
                  val bin = new AstBinary
                  bin.operator = "+"
                  bin.left = self.args(0)
                  bin.right = { val s = new AstString; s.value = ""; s.start = self.start; s.end = self.end; s }
                  bin.start = self.start
                  bin.end = self.end
                  return optimizeBinary(bin) // @nowarn
                }
              case "Number" =>
                if (self.args.isEmpty) {
                  val n = new AstNumber; n.value = 0.0; n.start = self.start; n.end = self.end
                  return n // @nowarn
                } else if (self.args.size == 1 && optionBool("unsafe_math")) {
                  val prefix = new AstUnaryPrefix
                  prefix.operator = "+"
                  prefix.expression = self.args(0)
                  prefix.start = self.start
                  prefix.end = self.end
                  return optimizeUnaryPrefix(prefix) // @nowarn
                }
              case "Symbol" =>
                // Symbol("desc") → Symbol() when unsafe_symbols is enabled
                if (self.args.size == 1 && self.args(0).isInstanceOf[AstString] && optionBool("unsafe_symbols")) {
                  self.args.clear()
                }
              case "Boolean" =>
                if (self.args.isEmpty) {
                  val f = new AstFalse; f.start = self.start; f.end = self.end
                  return f // @nowarn
                } else if (self.args.size == 1) {
                  val inner = new AstUnaryPrefix
                  inner.operator = "!"
                  inner.expression = self.args(0)
                  inner.start = self.start
                  inner.end = self.end
                  val outer = new AstUnaryPrefix
                  outer.operator = "!"
                  outer.expression = inner
                  outer.start = self.start
                  outer.end = self.end
                  return optimizeUnaryPrefix(outer) // @nowarn
                }
              case "RegExp" =>
                // RegExp("pattern", "flags") → /pattern/flags when args are constants
                if (self.args.nonEmpty && self.args.size <= 2 && self.args.forall(_.isInstanceOf[AstString])) {
                  val params = self.args.map { arg =>
                    Evaluate.evaluate(arg, this)
                  }
                  // Check that all args evaluated to non-node constants
                  if (params.forall(p => p != null && !p.isInstanceOf[AstNode])) {
                    val source = params(0).toString
                    val flags = if (params.size > 1) params(1).toString else ""
                    // Only optimize if the regex is safe (no problematic constructs)
                    if (regexpIsSafe(source)) {
                      try {
                        // Validate by creating the regex
                        val fixedSource = regexpSourceFix(new scala.util.matching.Regex(source).pattern.pattern())
                        val rx = new AstRegExp
                        rx.start = self.start
                        rx.end = self.end
                        rx.value = RegExpValue(fixedSource, flags)
                        // Only return if the regex evaluates successfully
                        val evResult = Evaluate.evaluate(rx, this)
                        if (evResult != null && !(evResult.asInstanceOf[AnyRef] eq rx.asInstanceOf[AnyRef])) {
                          return rx // @nowarn
                        }
                      } catch {
                        case _: Exception => // Invalid regex, don't optimize
                      }
                    }
                  }
                }
              case _ =>
            }

          // Method call optimizations
          case dot: AstDot if dot.expression != null =>
            dot.property match {
              case "toString" if self.args.isEmpty && !Inference.mayThrowOnAccess(dot, this) =>
                val bin = new AstBinary
                bin.operator = "+"
                bin.left = { val s = new AstString; s.value = ""; s.start = self.start; s.end = self.end; s }
                bin.right = dot.expression.nn
                bin.start = self.start
                bin.end = self.end
                return bestOfExpression(bin, self) // @nowarn

              case "join" if dot.expression.nn.isInstanceOf[AstArray] =>
                // [a, b, c].join(sep) → fold constant segments, concat with +
                boundary {
                  val arr = dot.expression.nn.asInstanceOf[AstArray]
                  var separator: Any = ","
                  if (self.args.nonEmpty) {
                    separator = Evaluate.evaluate(self.args(0), this)
                    if (separator == null || (separator.asInstanceOf[AnyRef] eq self.args(0).asInstanceOf[AnyRef])) break(()) // not a constant
                  }
                  val sepStr = separator.toString
                  val elements = ArrayBuffer.empty[AstNode]
                  val consts = ArrayBuffer.empty[Any]
                  var canOpt = true
                  var ei = 0
                  while (ei < arr.elements.size && canOpt) {
                    val el = arr.elements(ei)
                    if (el.isInstanceOf[AstExpansion]) { canOpt = false }
                    else {
                      val v = Evaluate.evaluate(el, this)
                      if (v != null && !(v.asInstanceOf[AnyRef] eq el.asInstanceOf[AnyRef])) {
                        consts.addOne(v)
                      } else {
                        if (consts.nonEmpty) {
                          val str = new AstString; str.value = consts.map(_.toString).mkString(sepStr); str.start = self.start; str.end = self.end
                          elements.addOne(str)
                          consts.clear()
                        }
                        elements.addOne(el)
                      }
                    }
                    ei += 1
                  }
                  if (!canOpt) break(())
                  if (consts.nonEmpty) {
                    val str = new AstString; str.value = consts.map(_.toString).mkString(sepStr); str.start = self.start; str.end = self.end
                    elements.addOne(str)
                  }
                  if (elements.isEmpty) {
                    val s = new AstString; s.value = ""; s.start = self.start; s.end = self.end
                    return s // @nowarn
                  }
                  if (elements.size == 1) {
                    if (isString(elements(0), this)) return elements(0) // @nowarn
                    val bin = new AstBinary; bin.operator = "+"; bin.start = self.start; bin.end = self.end
                    bin.left = { val s = new AstString; s.value = ""; s.start = self.start; s.end = self.end; s }
                    bin.right = elements(0)
                    return bin // @nowarn
                  }
                  if (sepStr == "") {
                    // Fold into + chain
                    var first: AstNode = null.asInstanceOf[AstNode]
                    if (isString(elements(0), this) || (elements.size > 1 && isString(elements(1), this))) {
                      first = elements.remove(0)
                    } else {
                      first = { val s = new AstString; s.value = ""; s.start = self.start; s.end = self.end; s }
                    }
                    var result: AstNode = first
                    for (el <- elements) {
                      val bin = new AstBinary; bin.operator = "+"; bin.left = result; bin.right = el; bin.start = el.start; bin.end = el.end
                      result = bin
                    }
                    return result // @nowarn
                  }
                  // Otherwise, best_of with optimized array
                  val nodeArr = new AstArray; nodeArr.elements = elements; nodeArr.start = arr.start; nodeArr.end = arr.end
                  val nodeDot = new AstDot; nodeDot.expression = nodeArr; nodeDot.property = "join"; nodeDot.optional = dot.optional; nodeDot.start = dot.start; nodeDot.end = dot.end
                  val node = new AstCall; node.expression = nodeDot; node.args = ArrayBuffer.from(self.args); node.start = self.start; node.end = self.end
                  return bestOfExpression(node, self) // @nowarn
                }

              case "charAt" if dot.expression.nn != null && isString(dot.expression.nn, this) =>
                // "str".charAt(n) → "str"[n]
                val arg = if (self.args.nonEmpty) self.args(0) else null
                val index: Any = if (arg != null) Evaluate.evaluate(arg.nn, this) else 0
                if (index != null && !(index.asInstanceOf[AnyRef] eq (if (arg != null) arg.nn.asInstanceOf[AnyRef] else null))) {
                  val idx = index match {
                    case d: Double => d.toInt
                    case i: Int    => i
                    case _         => 0
                  }
                  val sub = new AstSub
                  sub.expression = dot.expression
                  sub.property = makeNodeFromConstant(idx, if (arg != null) arg.nn else dot)
                  sub.start = self.start
                  sub.end = self.end
                  return sub // @nowarn
                }

              case "apply" if self.args.size == 2 && self.args(1).isInstanceOf[AstArray] =>
                // fn.apply(ctx, [a, b]) → fn.call(ctx, a, b)
                val spreadArgs = ArrayBuffer.from(self.args(1).asInstanceOf[AstArray].elements)
                spreadArgs.insert(0, self.args(0))
                val callDot = new AstDot; callDot.expression = dot.expression; callDot.optional = false; callDot.property = "call"; callDot.start = dot.start; callDot.end = dot.end
                val callNode = new AstCall; callNode.expression = callDot; callNode.args = spreadArgs; callNode.start = self.start; callNode.end = self.end
                return callNode // @nowarn

              case "call" =>
                // fn.call(ctx, a, b) → fn(a, b) when fn doesn't use `this`
                var func: AstNode | Null = dot.expression
                if (func != null && func.nn.isInstanceOf[AstSymbolRef]) {
                  func.nn.asInstanceOf[AstSymbolRef].fixedValue() match {
                    case fv: AstNode => func = fv
                    case _           =>
                  }
                }
                if (func != null && func.nn.isInstanceOf[AstLambda] && !containsThis(func.nn)) {
                  if (self.args.nonEmpty) {
                    // Drop the `this` argument, call directly
                    val seq = ArrayBuffer(self.args(0))
                    val directCall = new AstCall; directCall.expression = dot.expression; directCall.args = ArrayBuffer.from(self.args.drop(1)); directCall.start = self.start; directCall.end = self.end
                    seq.addOne(directCall)
                    return makeSequence(self, seq) // @nowarn
                  } else {
                    // No args at all: fn.call() → fn()
                    val directCall = new AstCall; directCall.expression = dot.expression; directCall.args = ArrayBuffer.empty; directCall.start = self.start; directCall.end = self.end
                    return directCall // @nowarn
                  }
                }

              case _ =>
            }

          case _ =>
        }
      }

      // unsafe_Function: new Function() => function(){}
      // Function("arg1", "arg2", "body") => minified function
      if (optionBool("unsafe_Function") && isUndeclaredRef(exp) && exp.asInstanceOf[AstSymbolRef].name == "Function") {
        if (self.args.isEmpty) {
          return makeEmptyFunction(self) // @nowarn
        }
        if (self.args.forall(_.isInstanceOf[AstString])) {
          // All args are string constants — we can try to minify the function body
          // This is a corner case documented at https://github.com/mishoo/UglifyJS2/issues/203
          // For now, we just return self — full implementation would parse+minify the body
          // The original Terser implementation parses the code and recompresses it
        }
      }

      // Try to evaluate constant calls
      if (optionBool("evaluate")) {
        val ev = Evaluate.evaluate(self, this)
        if (ev != null && (ev.asInstanceOf[AnyRef] ne self.asInstanceOf[AnyRef])) {
          val folded = makeNodeFromConstant(ev, self)
          return bestOfExpression(folded, self) // @nowarn
        }
      }

      // Try to inline the call (empty body, identity function, etc.)
      // NOTE: This is called AFTER builtins, matching original Terser ordering
      val inlined = Inline.inlineIntoCall(self, this)
      if (!(inlined eq self)) return inlined // @nowarn

      self
    }

  /** Check if a regex source is safe to fold into a literal. */
  private def regexpIsSafe(source: String): Boolean = {
    // Avoid problematic patterns that could change behavior
    !source.contains("\\c") && // control characters
    !source.contains("\\x") && // hex escapes without checking format
    !source.contains("\\u") // unicode escapes without checking format
  }

  /** Fix regex source for literal representation. */
  private def regexpSourceFix(source: String): String = {
    // The original does more complex escaping; this is a simplified version
    source.replace("/", "\\/")
  }

  /** Optimize binary expressions.
    *
    * Handles:
    *   - Constant folding (evaluate both sides)
    *   - Comparison simplification (=== to ==, typeof checks)
    *   - Commutative operator reordering (constant to LHS)
    *   - Algebraic simplifications (x + 0, x * 1, etc.)
    *   - Bitwise optimizations (De Morgan, shift by 0, identity)
    *   - String concatenation folding
    *   - Boolean context optimizations
    *   - Associativity flattening (a && (b && c) -> a && b && c)
    */
  private def optimizeBinary(self: AstBinary): AstNode = {
    // Lift sequences from left operand: (a, b) + c → (a, b + c)
    if (self.left != null) {
      self.left.nn match {
        case seq: AstSequence if seq.expressions.size >= 2 =>
          val exprs = ArrayBuffer.from(seq.expressions)
          val lastExpr = exprs.remove(exprs.size - 1)
          self.left = lastExpr
          exprs.addOne(self)
          return makeSequence(self, exprs) // @nowarn
        case _ =>
      }
    }

    // Commutative operator: move constant to left (lhs_constants)
    // Original: if right is a constant and left is not, swap them
    // But avoid swapping if left is a binary with same or higher precedence (breaks associativity)
    val commutativeOps = Set("==", "===", "!=", "!==", "*", "&", "|", "^")
    if (optionBool("lhs_constants") && commutativeOps.contains(self.operator)) {
      if (self.right != null && self.left != null) {
        val rightConst = Evaluate.isConstant(self.right.nn)
        val leftConst  = Evaluate.isConstant(self.left.nn)
        if (rightConst && !leftConst) {
          // Check precedence guard: don't swap if left is a binary with >= precedence
          val canSwap = self.left.nn match {
            case leftBin: AstBinary =>
              val selfPrec = Precedence.get(self.operator)
              val leftPrec = Precedence.get(leftBin.operator)
              leftPrec < selfPrec
            case _ => true
          }
          if (canSwap) {
            val tmp = self.left
            self.left = self.right
            self.right = tmp
          }
        }
      }
    }

    // Comparison optimizations: relax === to == when both sides are known same type
    if (optionBool("comparisons") && self.left != null && self.right != null) {
      self.operator match {
        case "===" if sameType(self.left.nn, self.right.nn) =>
          self.operator = "=="
        case "!==" if sameType(self.left.nn, self.right.nn) =>
          self.operator = "!="
        case _ =>
      }
    }

    // Lift sequences from right operand when left has no side effects
    if (self.left != null && self.right != null) {
      val lifted = liftSequencesBinaryRight(self)
      if (!(lifted eq self)) return lifted // @nowarn
    }

    // typeof comparisons: typeof x === "undefined" → x === void 0 (when x is declared)
    if (optionBool("typeofs") && self.left != null && self.right != null) {
      // typeof x === "undefined" → x === void 0
      if (
        self.left.nn.isInstanceOf[AstUnaryPrefix]
        && self.left.nn.asInstanceOf[AstUnaryPrefix].operator == "typeof"
        && self.right.nn.isInstanceOf[AstString]
        && self.right.nn.asInstanceOf[AstString].value == "undefined"
        && (self.operator == "==" || self.operator == "===")
      ) {
        val expr = self.left.nn.asInstanceOf[AstUnaryPrefix].expression.nn
        expr match {
          case ref: AstSymbolRef if ref.definition() != null && !ref.definition().nn.undeclared =>
            self.left = expr
            self.right = makeVoid0(self.right.nn)
            if (self.operator.length == 2) self.operator += "="
          case _ =>
        }
      }
      // "undefined" === typeof x → void 0 === x
      else if (
        self.right.nn.isInstanceOf[AstUnaryPrefix]
        && self.right.nn.asInstanceOf[AstUnaryPrefix].operator == "typeof"
        && self.left.nn.isInstanceOf[AstString]
        && self.left.nn.asInstanceOf[AstString].value == "undefined"
        && (self.operator == "==" || self.operator == "===")
      ) {
        val expr = self.right.nn.asInstanceOf[AstUnaryPrefix].expression.nn
        expr match {
          case ref: AstSymbolRef if ref.definition() != null && !ref.definition().nn.undeclared =>
            self.right = expr
            self.left = makeVoid0(self.left.nn)
            if (self.operator.length == 2) self.operator += "="
          case _ =>
        }
      }
    }

    // obj !== obj => false (when same SymbolDef and fixed value is an object)
    if (self.left != null && self.right != null && (self.operator == "===" || self.operator == "!==")) {
      (self.left.nn, self.right.nn) match {
        case (lRef: AstSymbolRef, rRef: AstSymbolRef) if lRef.definition() != null && rRef.definition() != null =>
          if ((lRef.definition().nn eq rRef.definition().nn) && {
            val fv = lRef.fixedValue()
            fv != null && (fv.isInstanceOf[AstArray] || fv.isInstanceOf[AstLambda] || fv.isInstanceOf[AstObject] || fv.isInstanceOf[AstClass])
          }) {
            if (self.operator.charAt(0) == '=') {
              val t = new AstTrue; t.start = self.start; t.end = self.end; return t // @nowarn
            } else {
              val f = new AstFalse; f.start = self.start; f.end = self.end; return f // @nowarn
            }
          }
        case _ =>
      }
    }

    // 32-bit integer comparison with 0 → booleanify
    if (self.left != null && self.right != null && (self.operator == "==" || self.operator == "===" || self.operator == "!=" || self.operator == "!==")) {
      if (Inference.is32BitInteger(self.left.nn, this) && Inference.is32BitInteger(self.right.nn, this)) {
        def mkNot(n: AstNode): AstNode = {
          val neg = new AstUnaryPrefix; neg.operator = "!"; neg.expression = n; neg.start = n.start; neg.end = n.end; neg
        }
        def booleanify(node: AstNode, truthy: Boolean): AstNode = {
          if (truthy) { if (inBooleanContext()) node else mkNot(mkNot(node)) }
          else mkNot(node)
        }
        // The only falsy 32-bit integer is 0
        if (self.left.nn.isInstanceOf[AstNumber] && self.left.nn.asInstanceOf[AstNumber].value == 0) {
          return booleanify(self.right.nn, self.operator.charAt(0) == '!') // @nowarn
        }
        if (self.right.nn.isInstanceOf[AstNumber] && self.right.nn.asInstanceOf[AstNumber].value == 0) {
          return booleanify(self.left.nn, self.operator.charAt(0) == '!') // @nowarn
        }
      }
    }

    // &&/|| → nullish coalescing when comparing against null/undefined
    if (self.left != null && self.right != null && (self.operator == "&&" || self.operator == "||")) {
      var lhs: AstNode = self.left.nn
      if (lhs.isInstanceOf[AstBinary] && lhs.asInstanceOf[AstBinary].operator == self.operator) {
        lhs = lhs.asInstanceOf[AstBinary].right.nn
      }
      if (lhs.isInstanceOf[AstBinary] && self.right.nn.isInstanceOf[AstBinary]) {
        val lBin = lhs.asInstanceOf[AstBinary]
        val rBin = self.right.nn.asInstanceOf[AstBinary]
        val expectedOp = if (self.operator == "&&") "!==" else "==="
        if (lBin.operator == expectedOp && rBin.operator == expectedOp && lBin.left != null && rBin.left != null && lBin.right != null && rBin.right != null) {
          val lIsUndef = isUndefined(lBin.left.nn, this)
          val rIsNull = rBin.left.nn.isInstanceOf[AstNull]
          val lIsNull = lBin.left.nn.isInstanceOf[AstNull]
          val rIsUndef = isUndefined(rBin.left.nn, this)
          if ((lIsUndef && rIsNull) || (lIsNull && rIsUndef)) {
            if (!hasSideEffects(lBin.right.nn, this) && AstEquivalent.equivalentTo(lBin.right.nn, rBin.right.nn)) {
              val combined = new AstBinary
              combined.operator = expectedOp.substring(0, expectedOp.length - 1)
              combined.left = new AstNull; combined.left.nn.start = self.start; combined.left.nn.end = self.end
              combined.right = lBin.right
              combined.start = self.start
              combined.end = self.end
              if (!(lhs.asInstanceOf[AnyRef] eq self.left.nn.asInstanceOf[AnyRef])) {
                val outer = new AstBinary
                outer.operator = self.operator
                outer.left = self.left.nn.asInstanceOf[AstBinary].left
                outer.right = combined
                outer.start = self.start
                outer.end = self.end
                return outer // @nowarn
              }
              return combined // @nowarn
            }
          }
        }
      }
    }

    // == / != : void 0 == x → null == x (undefined equals null in loose comparison)
    if (self.left != null && self.right != null && (self.operator == "==" || self.operator == "!=")) {
      if (isUndefined(self.left.nn, this)) {
        val nullNode = new AstNull
        nullNode.start = self.left.nn.start
        nullNode.end = self.left.nn.end
        self.left = nullNode
      } else if (isUndefined(self.right.nn, this)) {
        val nullNode = new AstNull
        nullNode.start = self.right.nn.start
        nullNode.end = self.right.nn.end
        self.right = nullNode
      }
    }

    // String concatenation: x + "" → x (when x is a string), "" + x → x
    if (self.operator == "+" && self.left != null && self.right != null) {
      // x + "" → x (when x is already a string)
      self.right.nn match {
        case s: AstString if s.value == "" && isString(self.left.nn, this) =>
          return self.left.nn // @nowarn
        case _ =>
      }
      // "" + x → x (when x is already a string)
      self.left.nn match {
        case s: AstString if s.value == "" && isString(self.right.nn, this) =>
          return self.right.nn // @nowarn
        case _ =>
      }
      // ("" + x) + y → x + y (when y is a string)
      self.left.nn match {
        case lBin: AstBinary
            if lBin.operator == "+"
              && lBin.left != null && lBin.left.nn.isInstanceOf[AstString]
              && lBin.left.nn.asInstanceOf[AstString].value == ""
              && isString(self.right.nn, this) =>
          self.left = lBin.right
          return self // @nowarn
        case _ =>
      }
      // a + -b → a - b (when a is numeric)
      self.right.nn match {
        case up: AstUnaryPrefix if up.operator == "-" && up.expression != null && isNumber(self.left.nn, this) =>
          val sub = new AstBinary
          sub.operator = "-"
          sub.left = self.left
          sub.right = up.expression.nn
          sub.start = self.start
          sub.end = self.end
          return sub // @nowarn
        case _ =>
      }
    }

    // + in boolean context: "foo" + x → (x, true) when left evaluates to non-empty string
    if (self.operator == "+" && inBooleanContext() && self.left != null && self.right != null) {
      val ll = Evaluate.evaluate(self.left.nn, this)
      if (ll.isInstanceOf[String] && ll.asInstanceOf[String].nonEmpty) {
        val trueNode = new AstTrue
        trueNode.start = self.start
        trueNode.end = self.end
        return makeSequence(self, ArrayBuffer(self.right.nn, trueNode)) // @nowarn
      }
      val rr = Evaluate.evaluate(self.right.nn, this)
      if (rr.isInstanceOf[String] && rr.asInstanceOf[String].nonEmpty) {
        val trueNode = new AstTrue
        trueNode.start = self.start
        trueNode.end = self.end
        return makeSequence(self, ArrayBuffer(self.left.nn, trueNode)) // @nowarn
      }
    }

    // Template string concatenation: `foo${bar}` + x → `foo${bar}x` (when x is constant)
    if (self.operator == "+" && self.left != null && self.right != null) {
      // template + constant → extend last segment
      (self.left.nn, self.right.nn) match {
        case (tmplL: AstTemplateString, tmplR: AstTemplateString) =>
          // `1${bar}2` + `foo${baz}` => `1${bar}2foo${baz}`
          if (tmplL.segments.nonEmpty && tmplR.segments.nonEmpty) {
            tmplL.segments.last match {
              case lastSeg: AstTemplateSegment =>
                tmplR.segments(0) match {
                  case firstSeg: AstTemplateSegment =>
                    // Concatenate the last segment of left with first segment of right
                    lastSeg.value = lastSeg.value + firstSeg.value
                    // Append remaining segments from right template
                    var i = 1
                    while (i < tmplR.segments.size) {
                      tmplL.segments.addOne(tmplR.segments(i))
                      i += 1
                    }
                    return tmplL // @nowarn
                  case _ =>
                }
              case _ =>
            }
          }
        case (tmpl: AstTemplateString, _) =>
          val rr = Evaluate.evaluate(self.right.nn, this)
          if (rr != null && (rr.asInstanceOf[AnyRef] ne self.right.nn.asInstanceOf[AnyRef])) {
            val rrStr = rr.toString
            if (tmpl.segments.nonEmpty) {
              tmpl.segments.last match {
                case seg: AstTemplateSegment =>
                  seg.value = seg.value + rrStr
                  return tmpl // @nowarn
                case _ =>
              }
            }
          }
        case (_, tmpl: AstTemplateString) =>
          val ll = Evaluate.evaluate(self.left.nn, this)
          if (ll != null && (ll.asInstanceOf[AnyRef] ne self.left.nn.asInstanceOf[AnyRef])) {
            val llStr = ll.toString
            if (tmpl.segments.nonEmpty) {
              tmpl.segments(0) match {
                case seg: AstTemplateSegment =>
                  seg.value = llStr + seg.value
                  return tmpl // @nowarn
                case _ =>
              }
            }
          }
        case _ =>
      }
    }

    // &&/||/?? short-circuit evaluation
    if (optionBool("evaluate") && self.left != null && self.right != null) {
      self.operator match {
        case "&&" =>
          val ll = Evaluate.evaluate(self.left.nn, this)
          if (ll != null && (ll.asInstanceOf[AnyRef] ne self.left.nn.asInstanceOf[AnyRef])) {
            ll match {
              case false | 0 | 0.0 | "" | null =>
                // Left is falsy → result is left (short-circuit)
                // Use maintainThisBinding to preserve this-context for method calls
                val p = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
                val s = try { this.self() } catch { case _: IndexOutOfBoundsException => self.asInstanceOf[AstNode] }
                if (p != null) return optimizeNode(maintainThisBinding(p.asInstanceOf[AstNode], s, self.left.nn)) // @nowarn
                return self.left.nn // @nowarn
              case _ =>
                // Left is truthy → result is right
                return makeSequence(self, ArrayBuffer(self.left.nn, self.right.nn)) // @nowarn
            }
          }
          val rr = Evaluate.evaluate(self.right.nn, this)
          if (rr != null && (rr.asInstanceOf[AnyRef] ne self.right.nn.asInstanceOf[AnyRef])) {
            rr match {
              case false | 0 | 0.0 | "" | null =>
                if (inBooleanContext()) {
                  val falseNode = new AstFalse
                  falseNode.start = self.start
                  falseNode.end = self.end
                  return makeSequence(self, ArrayBuffer(self.left.nn, falseNode)) // @nowarn
                } else {
                  setFlag(self, FALSY)
                }
              case _ =>
                // Right is truthy in && → result depends only on left
                // But also check: parent.operator == "&&" && parent.left === compressor.self()
                val par = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
                val inParentAndWithLeft = par match {
                  case pbin: AstBinary if pbin.operator == "&&" && pbin.left != null =>
                    pbin.left.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef]
                  case _ => false
                }
                if (inParentAndWithLeft || inBooleanContext()) {
                  return self.left.nn // @nowarn
                } else {
                  setFlag(self, TRUTHY)
                }
            }
          }
          // x || false && y ---> x ? y : false
          self.left.nn match {
            case leftOr: AstBinary if leftOr.operator == "||" && leftOr.right != null =>
              val lr = Evaluate.evaluate(leftOr.right.nn, this)
              if (lr != null && (lr.asInstanceOf[AnyRef] ne leftOr.right.nn.asInstanceOf[AnyRef])) {
                lr match {
                  case false | 0 | 0.0 | "" | null =>
                    val cond = new AstConditional
                    cond.condition = leftOr.left
                    cond.consequent = self.right
                    cond.alternative = leftOr.right
                    cond.start = self.start
                    cond.end = self.end
                    return cond // @nowarn
                  case _ =>
                }
              }
            case _ =>
          }

        case "||" =>
          val ll = Evaluate.evaluate(self.left.nn, this)
          if (ll != null && (ll.asInstanceOf[AnyRef] ne self.left.nn.asInstanceOf[AnyRef])) {
            ll match {
              case false | 0 | 0.0 | "" | null =>
                // Left is falsy → result is right
                return makeSequence(self, ArrayBuffer(self.left.nn, self.right.nn)) // @nowarn
              case _ =>
                // Left is truthy → result is left (short-circuit)
                // Use maintainThisBinding to preserve this-context for method calls
                val p = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
                val s = try { this.self() } catch { case _: IndexOutOfBoundsException => self.asInstanceOf[AstNode] }
                if (p != null) return optimizeNode(maintainThisBinding(p.asInstanceOf[AstNode], s, self.left.nn)) // @nowarn
                return self.left.nn // @nowarn
            }
          }
          val rr = Evaluate.evaluate(self.right.nn, this)
          if (rr != null && (rr.asInstanceOf[AnyRef] ne self.right.nn.asInstanceOf[AnyRef])) {
            rr match {
              case false | 0 | 0.0 | "" | null =>
                // Right is falsy in || → result is left
                // But also check: parent.operator == "||" && parent.left === compressor.self()
                val par = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
                val inParentOrWithLeft = par match {
                  case pbin: AstBinary if pbin.operator == "||" && pbin.left != null =>
                    pbin.left.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef]
                  case _ => false
                }
                if (inParentOrWithLeft || inBooleanContext()) {
                  return self.left.nn // @nowarn
                } else {
                  setFlag(self, FALSY)
                }
              case _ =>
                // Right is truthy → always truthy
                if (inBooleanContext()) {
                  val trueNode = new AstTrue
                  trueNode.start = self.start
                  trueNode.end = self.end
                  return makeSequence(self, ArrayBuffer(self.left.nn, trueNode)) // @nowarn
                } else {
                  setFlag(self, TRUTHY)
                }
            }
          }
          // x && truthy || y ---> x ? x && truthy : y (but original does x ? consequent : alternative)
          // Original: if (self.left.operator == "&&") { lr = left.right.evaluate; if (lr && !(lr instanceof AST_Node)) ... }
          self.left.nn match {
            case leftAnd: AstBinary if leftAnd.operator == "&&" && leftAnd.right != null =>
              val lr = Evaluate.evaluate(leftAnd.right.nn, this)
              if (lr != null && (lr.asInstanceOf[AnyRef] ne leftAnd.right.nn.asInstanceOf[AnyRef])) {
                lr match {
                  case false | 0 | 0.0 | "" | null =>
                    // lr is falsy — don't optimize
                  case _ =>
                    // lr is truthy: x && truthy || y → x ? (x && truthy) : y
                    val cond = new AstConditional
                    cond.condition = leftAnd.left
                    cond.consequent = leftAnd.right
                    cond.alternative = self.right
                    cond.start = self.start
                    cond.end = self.end
                    return cond // @nowarn
                }
              }
            case _ =>
          }

        case "??" =>
          // x ?? y: if x is known nullish, result is y
          if (isNullish(self.left.nn, this)) {
            return self.right.nn // @nowarn
          }
          // x ?? y: if x evaluates to a known value, dispatch
          val llN = Evaluate.evaluate(self.left.nn, this)
          if (llN != null && (llN.asInstanceOf[AnyRef] ne self.left.nn.asInstanceOf[AnyRef])) {
            // null or undefined → right; anything else → left
            if (llN == null || llN.isInstanceOf[Unit]) return self.right.nn // @nowarn
            else return self.left.nn // @nowarn
          }
          // x ?? y in boolean context: if y is falsy, just x
          if (inBooleanContext()) {
            val rrN = Evaluate.evaluate(self.right.nn, this)
            if (rrN != null && (rrN.asInstanceOf[AnyRef] ne self.right.nn.asInstanceOf[AnyRef])) {
              rrN match {
                case false | 0 | 0.0 | "" | null =>
                  return self.left.nn // @nowarn
                case _ =>
              }
            }
          }

        case _ =>
      }
    }

    // Associative constant folding for +, *, &, |, ^
    // Note: * associativity requires unsafe_math option (floating point rounding differences)
    if (optionBool("evaluate") && self.left != null && self.right != null) {
      val assocOps = Set("+", "&", "|", "^")
      // For "*" we need unsafe_math to be true for associativity
      val isAssociative = assocOps.contains(self.operator) ||
        (self.operator == "*" && optionBool("unsafe_math"))
      if (isAssociative) {
        // (n + 2) + 3 → 5 + n  or  (2 * n) * 3 → 6 * n
        if (self.right.nn.isInstanceOf[AstConstant] && self.left.nn.isInstanceOf[AstBinary]) {
          val lBin = self.left.nn.asInstanceOf[AstBinary]
          if (lBin.operator == self.operator) {
            if (lBin.left != null && lBin.left.nn.isInstanceOf[AstConstant]) {
              // (C1 op x) op C2 → (C1 op C2) op x
              val folded = new AstBinary
              folded.operator = self.operator
              folded.left = lBin.left
              folded.right = self.right
              folded.start = lBin.left.nn.start
              folded.end = self.right.nn.end
              self.left = folded
              self.right = lBin.right
            } else if (lBin.right != null && lBin.right.nn.isInstanceOf[AstConstant]) {
              // (x op C1) op C2 → (C1 op C2) op x
              val folded = new AstBinary
              folded.operator = self.operator
              folded.left = lBin.right
              folded.right = self.right
              folded.start = lBin.right.nn.start
              folded.end = self.right.nn.end
              self.left = folded
              self.right = lBin.left
            }
          }
        }

        // a + (b + c) → (a + b) + c  (right-associative to left-associative)
        if (self.right.nn.isInstanceOf[AstBinary] && self.right.nn.asInstanceOf[AstBinary].operator == self.operator) {
          val rBin = self.right.nn.asInstanceOf[AstBinary]
          if (rBin.left != null) {
            val newLeft = new AstBinary
            newLeft.operator = self.operator
            newLeft.left = self.left
            newLeft.right = rBin.left
            newLeft.start = self.left.nn.start
            newLeft.end = rBin.left.nn.end
            self.left = newLeft
            self.right = rBin.right
          }
        }
      }
    }

    // Bitwise optimizations
    if (self.left != null && self.right != null && bitwiseBinop.contains(self.operator)) {
      // ~x ^ ~y → x ^ y
      if (
        self.operator == "^"
        && self.left.nn.isInstanceOf[AstUnaryPrefix] && self.left.nn.asInstanceOf[AstUnaryPrefix].operator == "~"
        && self.right.nn.isInstanceOf[AstUnaryPrefix] && self.right.nn.asInstanceOf[AstUnaryPrefix].operator == "~"
      ) {
        val newBin = new AstBinary
        newBin.operator = "^"
        newBin.left = self.left.nn.asInstanceOf[AstUnaryPrefix].expression
        newBin.right = self.right.nn.asInstanceOf[AstUnaryPrefix].expression
        newBin.start = self.start
        newBin.end = self.end
        return newBin // @nowarn
      }

      // Shifts by 0: x >> 0 → x | 0,  x << 0 → x | 0
      if (
        (self.operator == "<<" || self.operator == ">>")
        && self.right.nn.isInstanceOf[AstNumber] && self.right.nn.asInstanceOf[AstNumber].value == 0.0
      ) {
        self.operator = "|"
      }

      // Identity with 0: x | 0 → x (when x is 32-bit), x ^ 0 → x (when x is 32-bit)
      val zeroSide: AstNode | Null =
        if (self.right.nn.isInstanceOf[AstNumber] && self.right.nn.asInstanceOf[AstNumber].value == 0.0) self.right.nn
        else if (self.left.nn.isInstanceOf[AstNumber] && self.left.nn.asInstanceOf[AstNumber].value == 0.0) self.left.nn
        else null
      if (zeroSide != null) {
        val nonZeroSide = if ((zeroSide.nn.asInstanceOf[AnyRef] eq self.right.nn.asInstanceOf[AnyRef])) self.left.nn else self.right.nn
        // x | 0 → x or x ^ 0 → x (when x is 32-bit or in 32-bit context)
        if ((self.operator == "|" || self.operator == "^") && in32BitContext()) {
          return nonZeroSide // @nowarn
        }
        // x & 0 → 0 (when x has no side effects and is 32-bit)
        if (self.operator == "&" && !hasSideEffects(nonZeroSide, this)) {
          return zeroSide.nn // @nowarn
        }
      }

      // Full mask: x & -1 → x (when x is 32-bit or in 32-bit context)
      def isFullMask(node: AstNode): Boolean =
        (node.isInstanceOf[AstNumber] && node.asInstanceOf[AstNumber].value == -1.0) ||
          (node.isInstanceOf[AstUnaryPrefix] && node.asInstanceOf[AstUnaryPrefix].operator == "-" &&
            node.asInstanceOf[AstUnaryPrefix].expression != null &&
            node.asInstanceOf[AstUnaryPrefix].expression.nn.isInstanceOf[AstNumber] &&
            node.asInstanceOf[AstUnaryPrefix].expression.nn.asInstanceOf[AstNumber].value == 1.0)

      val fullMask: AstNode | Null =
        if (isFullMask(self.right.nn)) self.right.nn
        else if (isFullMask(self.left.nn)) self.left.nn
        else null
      if (fullMask != null) {
        val otherSide = if ((fullMask.nn.asInstanceOf[AnyRef] eq self.right.nn.asInstanceOf[AnyRef])) self.left.nn else self.right.nn
        // x & -1 → x
        if (self.operator == "&" && in32BitContext()) {
          return otherSide // @nowarn
        }
        // x ^ -1 → ~x
        if (self.operator == "^" && in32BitContext()) {
          val neg = new AstUnaryPrefix
          neg.operator = "~"
          neg.expression = otherSide
          neg.start = self.start
          neg.end = self.end
          return neg // @nowarn
        }
      }

      // x | x �� 0 | x, x & x → 0 | x (when equivalent and no side effects, in 32-bit context)
      if (
        (self.operator == "|" || self.operator == "&")
        && AstEquivalent.equivalentTo(self.left.nn, self.right.nn)
        && !hasSideEffects(self.left.nn, this)
        && in32BitContext()
      ) {
        val zero = new AstNumber
        zero.value = 0.0
        zero.start = self.start
        zero.end = self.end
        self.left = zero
        self.operator = "|"
      }

      // De Morgan's laws: z & (X | y) => z & X (given y & z === 0), or z & X | {y & z} (given y & z !== 0)
      if (self.operator == "&") {
        val zEval = Evaluate.evaluate(self.left.nn, this)
        if (zEval.isInstanceOf[Double]) {
          val z = zEval.asInstanceOf[Double].toInt
          self.right.nn match {
            case rBin: AstBinary if rBin.operator == "|" && rBin.left != null && rBin.right != null =>
              // Check if rBin.right evaluates to a number
              var xNode: AstNode | Null = null
              var yNode: AstNode | Null = null
              var y: Int = 0
              val rrEval = Evaluate.evaluate(rBin.right.nn, this)
              if (rrEval.isInstanceOf[Double]) {
                // z & (X | y)
                xNode = rBin.left.nn
                yNode = rBin.right.nn
                y = rrEval.asInstanceOf[Double].toInt
              } else {
                val rlEval = Evaluate.evaluate(rBin.left.nn, this)
                if (rlEval.isInstanceOf[Double]) {
                  // z & (y | X)
                  xNode = rBin.right.nn
                  yNode = rBin.left.nn
                  y = rlEval.asInstanceOf[Double].toInt
                }
              }
              if (xNode != null && yNode != null) {
                if ((y & z) == 0) {
                  // y & z === 0 -> result is z & X
                  val newBin = new AstBinary
                  newBin.operator = "&"
                  newBin.left = self.left
                  newBin.right = xNode.nn
                  newBin.start = self.start
                  newBin.end = self.end
                  return newBin // @nowarn
                } else {
                  // y & z !== 0 -> try (X & z) | (y & z)
                  val reordered = new AstBinary
                  reordered.operator = "|"
                  val leftAnd = new AstBinary
                  leftAnd.operator = "&"
                  leftAnd.left = xNode.nn
                  leftAnd.right = self.left
                  leftAnd.start = self.start
                  leftAnd.end = self.end
                  reordered.left = leftAnd
                  reordered.right = makeNodeFromConstant(y & z, yNode.nn)
                  reordered.start = self.start
                  reordered.end = self.end
                  return bestOfExpression(reordered, self) // @nowarn
                }
              }
            case _ =>
          }
        }
      }
    }

    // Associativity flattening: x && (y && z) → x && y && z (also ||, +)
    if (
      self.left != null && self.right != null
      && self.right.nn.isInstanceOf[AstBinary]
      && self.right.nn.asInstanceOf[AstBinary].operator == self.operator
    ) {
      val rBin = self.right.nn.asInstanceOf[AstBinary]
      val shouldFlatten =
        Inference.lazyOp.contains(self.operator) ||
          (self.operator == "+"
            && rBin.left != null
            && (isString(rBin.left.nn, this) || (isString(self.left.nn, this) && rBin.right != null && isString(rBin.right.nn, this))))
      if (shouldFlatten && rBin.left != null) {
        val newLeft = new AstBinary
        newLeft.operator = self.operator
        newLeft.left = self.left
        newLeft.right = rBin.left
        newLeft.start = self.left.nn.start
        newLeft.end = rBin.left.nn.end
        self.left = newLeft
        self.right = rBin.right
      }
    }

    // Final constant folding (after all other optimizations)
    if (optionBool("evaluate") && self.left != null && self.right != null) {
      val ev = Evaluate.evaluate(self, this)
      if (ev != null && (ev.asInstanceOf[AnyRef] ne self.asInstanceOf[AnyRef])) {
        val folded = makeNodeFromConstant(ev, self)
        return bestOfExpression(folded, self) // @nowarn
      }
    }

    self
  }

  /** Check if two nodes are known to be the same JS type (for comparison relaxation). */
  private def sameType(a: AstNode, b: AstNode): Boolean =
    (isString(a, this) && isString(b, this)) ||
      (isNumber(a, this) && isNumber(b, this)) ||
      (isBoolean(a) && isBoolean(b))

  /** Optimize assignment expressions.
    *
    * ISS-111 fix: Add dead_code elimination for unreachable assignments
    * and lift_sequences call for logical assignments.
    */
  private def optimizeAssign(self: AstAssign): AstNode = {
    // Logical assignments (&&=, ||=, ??=) need lift_sequences
    if (self.logical) {
      return liftSequencesAssign(self) // @nowarn
    }

    var theDef: SymbolDef | Null = null

    // x = x -> x (self-assignment)
    if (
      self.operator == "="
      && self.left != null && self.right != null
      && self.left.nn.isInstanceOf[AstSymbolRef]
      && self.right.nn.isInstanceOf[AstSymbolRef]
    ) {
      val leftRef  = self.left.nn.asInstanceOf[AstSymbolRef]
      val rightRef = self.right.nn.asInstanceOf[AstSymbolRef]
      if (leftRef.name == rightRef.name && leftRef.name != "arguments") {
        theDef = leftRef.definition()
        if (theDef == null || !theDef.nn.undeclared) return self.right.nn // @nowarn — safe self-assignment removal
      }
    }

    // dead_code: eliminate assignment to unreachable var in exit context
    if (
      optionBool("dead_code")
      && self.left != null && self.left.nn.isInstanceOf[AstSymbolRef]
    ) {
      val leftRef = self.left.nn.asInstanceOf[AstSymbolRef]
      theDef = leftRef.definition()
      if (theDef != null) {
        val defScope = theDef.nn.scope
        val lambdaScope = findParent[AstLambda]
        if (defScope != null && lambdaScope != null && (defScope.asInstanceOf[AnyRef] eq lambdaScope.asInstanceOf[AnyRef])) {
          // Walk up to find if we're in an exit context (return/throw)
          var level = 0
          var node: AstNode = self
          var par: AstNode | Null = try { parent(level) } catch { case _: IndexOutOfBoundsException => null }
          var foundExit = false
          boundary {
            while (par != null) {
              par.nn match {
                case _: AstExit =>
                  // Check not in try block
                  val inTry = findParentBetween[AstTry](level)
                  if (inTry == null && !Common.isReachable(defScope.asInstanceOf[AstScope], ArrayBuffer(theDef))) {
                    foundExit = true
                    break(())
                  }
                case bin: AstBinary if bin.right != null && (bin.right.nn.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef]) =>
                  // Continue up
                case seq: AstSequence if seq.expressions.nonEmpty && (seq.expressions.last.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef]) =>
                  // Continue up
                case _ =>
                  break(()) // Stop searching
              }
              node = par.nn
              level += 1
              par = try { parent(level) } catch { case _: IndexOutOfBoundsException => null }
            }
          }
          if (foundExit) {
            if (self.operator == "=") return self.right.nn // @nowarn
            theDef.nn.fixed = false
            val bin = new AstBinary
            bin.operator = self.operator.substring(0, self.operator.length - 1)
            bin.left = self.left
            bin.right = self.right
            bin.start = self.start
            bin.end = self.end
            return optimizeBinary(bin) // @nowarn
          }
        }
      }
    }

    // Lift sequences from assignment
    val lifted = liftSequencesAssign(self)
    if (!(lifted eq self)) return lifted // @nowarn

    // Compound assignment: x = x + y -> x += y
    val assignOps = Set("+", "-", "/", "*", "%", ">>", "<<", ">>>", "|", "^", "&")
    if (
      self.operator == "="
      && self.left != null && self.right != null
      && self.left.nn.isInstanceOf[AstSymbolRef]
      && self.right.nn.isInstanceOf[AstBinary]
    ) {
      val leftRef  = self.left.nn.asInstanceOf[AstSymbolRef]
      val binRight = self.right.nn.asInstanceOf[AstBinary]
      if (
        binRight.left != null
        && binRight.left.nn.isInstanceOf[AstSymbolRef]
        && binRight.left.nn.asInstanceOf[AstSymbolRef].name == leftRef.name
        && assignOps.contains(binRight.operator)
      ) {
        // x = x OP y -> x OP= y
        self.operator = binRight.operator + "="
        self.right = binRight.right
      }
    }

    // Commutative compound from right side: x = y OP x -> x OP= y (for commutative ops)
    val commAssignOps = Set("*", "&", "|", "^")
    if (
      self.operator == "="
      && self.left != null && self.right != null
      && self.left.nn.isInstanceOf[AstSymbolRef]
      && self.right.nn.isInstanceOf[AstBinary]
    ) {
      val leftRef  = self.left.nn.asInstanceOf[AstSymbolRef]
      val binRight = self.right.nn.asInstanceOf[AstBinary]
      if (
        binRight.right != null
        && binRight.right.nn.isInstanceOf[AstSymbolRef]
        && binRight.right.nn.asInstanceOf[AstSymbolRef].name == leftRef.name
        && commAssignOps.contains(binRight.operator)
      ) {
        // x = y OP x -> x OP= y
        self.operator = binRight.operator + "="
        self.right = binRight.left
      }
    }

    self
  }

  /** Lift sequences from assignment: (a, b) = c → (a, b = c) */
  private def liftSequencesAssign(self: AstAssign): AstNode =
    if (optionBool("sequences") && self.left != null) {
      self.left.nn match {
        case seq: AstSequence if seq.expressions.size >= 2 =>
          val exprs = ArrayBuffer.from(seq.expressions)
          val lastExpr = exprs.remove(exprs.size - 1)
          self.left = lastExpr
          exprs.addOne(self)
          makeSequence(self, exprs)
        case _ => self
      }
    } else self

  /** Find a parent of type T between current position and level. */
  private def findParentBetween[T <: AstNode](maxLevel: Int)(using ct: scala.reflect.ClassTag[T]): T | Null = {
    var i = 0
    while (i < maxLevel) {
      try {
        parent(i) match {
          case p if ct.runtimeClass.isInstance(p) => return p.asInstanceOf[T] // @nowarn
          case _ =>
        }
      } catch { case _: IndexOutOfBoundsException => return null }
      i += 1
    }
    null
  }

  /** Optimize property dot access — evaluate property reads on literals.
    *
    * ISS-110 fix: Add unsafe_proto optimization and PropAccess-within-PropAccess
    * early return guard.
    */
  private def optimizeDot(self: AstDot): AstNode = {
    val parentNode = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }

    // Don't optimize LHS
    if (isLhs() != null) return self // @nowarn

    // unsafe_proto: Array.prototype.foo => [].foo, etc.
    if (optionBool("unsafe_proto") && self.expression != null && self.expression.nn.isInstanceOf[AstDot]) {
      val innerDot = self.expression.nn.asInstanceOf[AstDot]
      if (innerDot.property == "prototype" && innerDot.expression != null) {
        innerDot.expression.nn match {
          case ref: AstSymbolRef if isUndeclaredRef(ref) =>
            ref.name match {
              case "Array" =>
                val arr = new AstArray
                arr.elements = ArrayBuffer.empty
                arr.start = self.expression.nn.start
                arr.end = self.expression.nn.end
                self.expression = arr
              case "Function" =>
                self.expression = makeEmptyFunction(self.expression.nn)
              case "Number" =>
                val num = new AstNumber
                num.value = 0.0
                num.start = self.expression.nn.start
                num.end = self.expression.nn.end
                self.expression = num
              case "Object" =>
                val obj = new AstObject
                obj.properties = ArrayBuffer.empty
                obj.start = self.expression.nn.start
                obj.end = self.expression.nn.end
                self.expression = obj
              case "RegExp" =>
                val rx = new AstRegExp
                rx.value = RegExpValue("t", "")
                rx.start = self.expression.nn.start
                rx.end = self.expression.nn.end
                self.expression = rx
              case "String" =>
                val str = new AstString
                str.value = ""
                str.start = self.expression.nn.start
                str.end = self.expression.nn.end
                self.expression = str
              case _ =>
            }
          case _ =>
        }
      }
    }

    if (optionBool("properties") && self.expression != null) {
      self.property match {
        case prop: String =>
          // Try to read from literal objects
          val propNode = new AstString
          propNode.value = prop
          val value = readProperty(self.expression.nn, propNode)
          if (value != null && Evaluate.isConstant(value.nn)) {
            return bestOfExpression(value.nn, self) // @nowarn
          }
          // Try flatten_object: {a:1}.a → [1][0]
          val flat = flattenObject(self, prop)
          if (flat != null) return flat.nn // @nowarn
        case _ =>
      }
    }

    // PropAccess-within-PropAccess early return guard
    // When self.expression is PropAccess and parent is also PropAccess, return early
    if (self.expression != null && self.expression.nn.isInstanceOf[AstPropAccess] &&
        parentNode != null && parentNode.nn.isInstanceOf[AstPropAccess]) {
      return self // @nowarn
    }

    // Evaluate: obj.prop when obj is a constant
    if (optionBool("evaluate") && self.expression != null) {
      val ev = Evaluate.evaluate(self, this)
      if (ev != null && (ev.asInstanceOf[AnyRef] ne self.asInstanceOf[AnyRef])) {
        val folded = makeNodeFromConstant(ev, self)
        return bestOfExpression(folded, self) // @nowarn
      }
    }

    self
  }

  /** Optimize computed property access — convert to dot when key is a valid identifier.
    *
    * ISS-109 fix: Add arguments[n] to named-param optimization and
    * array-literal index flattening ([a,b,c][1] => side-effects + b).
    */
  private def optimizeSub(self: AstSub): AstNode = {
    val expr = self.expression
    val prop = self.property match {
      case n: AstNode => n
      case _ => null
    }

    if (optionBool("properties") && prop != null) {
      val keyEv = Evaluate.evaluate(prop, this)
      val keyStr: String | Null = keyEv match {
        case s: String => s
        case d: Double if d == d.toInt.toDouble => d.toInt.toString
        case _ => null
      }

      if (keyStr != null) {
        val ks = keyStr.nn
        // Convert to dot notation if key is a valid identifier
        val propSize = AstSize.size(prop)
        if (isValidIdentifier(ks) && !isReservedWord(ks) && ks.length <= propSize + 1) {
          val dot = new AstDot
          dot.start = self.start
          dot.end = self.end
          dot.expression = self.expression
          dot.optional = self.optional
          dot.property = ks
          dot.quote = prop match { case s: AstString => s.quote; case _ => null }
          return optimizeDot(dot) // @nowarn
        }
        // Try flatten_object with the evaluated key
        val flat = flattenObject(self, ks)
        if (flat != null) return flat.nn // @nowarn
      }

      // If key wasn't evaluated, try string key directly
      prop match {
        case str: AstString =>
          val flat = flattenObject(self, str.value)
          if (flat != null) return flat.nn // @nowarn
        case _ =>
      }
    }

    // arguments[n] -> named param optimization
    if (optionBool("arguments") && expr != null && prop != null) {
      boundary {
        expr match {
          case ref: AstSymbolRef if ref.name == "arguments" =>
            val theDef = ref.definition()
            if (theDef != null && theDef.nn.orig.size == 1) {
              // Find enclosing function
              val fn = findParent[AstLambda]
              if (fn != null && fn.usesArguments && !fn.isInstanceOf[AstArrow]) {
                prop match {
                  case num: AstNumber =>
                    val index = num.value.toInt
                    if (num.value == index.toDouble && index >= 0) {
                      // Check for duplicate or destructuring parameters
                      val params = scala.collection.mutable.Set.empty[String]
                      var valid = true
                      var n = 0
                      while (n < fn.argnames.size && valid) {
                        fn.argnames(n) match {
                          case sym: AstSymbolFunarg =>
                            if (params.contains(sym.name)) valid = false
                            else params.add(sym.name)
                          case _ => valid = false // destructuring
                        }
                        n += 1
                      }
                      if (valid) {
                        val argname: AstSymbolFunarg | Null =
                          if (index < fn.argnames.size) {
                            fn.argnames(index) match {
                              case sym: AstSymbolFunarg =>
                                if (hasDirective("use strict") != null) {
                                  val d = sym.definition()
                                  if (d != null && (d.nn.assignments > 0 || d.nn.orig.size > 1)) null
                                  else sym
                                } else sym
                              case _ => null
                            }
                          } else if (!optionBool("keep_fargs") && index < fn.argnames.size + 5) {
                            // Create new argument symbol
                            val newSym = new AstSymbolFunarg
                            newSym.name = "argument_" + fn.argnames.size
                            newSym.start = self.start
                            newSym.end = self.end
                            while (fn.argnames.size <= index) {
                              val padSym = new AstSymbolFunarg
                              padSym.name = "argument_" + fn.argnames.size
                              padSym.start = self.start
                              padSym.end = self.end
                              fn.argnames.addOne(padSym)
                            }
                            fn.argnames(index).asInstanceOf[AstSymbolFunarg]
                          } else null
                        if (argname != null) {
                          val sym = new AstSymbolRef
                          sym.start = self.start
                          sym.end = self.end
                          sym.name = argname.name
                          sym.scope = fn
                          sym.thedef = argname.thedef
                          clearFlag(argname, UNUSED)
                          break(sym)
                        }
                      }
                    }
                  case _ =>
                }
              }
            }
          case _ =>
        }
      } match {
        case sym: AstSymbolRef => return sym // @nowarn
        case _ =>
      }
    }

    // Don't optimize LHS
    if (isLhs() != null) return self // @nowarn

    // Array-literal index flattening: [a,b,c][1] => side-effects + b
    if (optionBool("properties") && optionBool("side_effects") && prop != null && expr != null) {
      prop match {
        case num: AstNumber if expr.isInstanceOf[AstArray] =>
          val arr = expr.asInstanceOf[AstArray]
          val index = num.value.toInt
          if (num.value == index.toDouble && index >= 0 && index < arr.elements.size) {
            val retValue = arr.elements(index)
            // Check safe_to_flatten
            val safeToFlatten = retValue match {
              case ref: AstSymbolRef =>
                val fv = ref.fixedValue()
                fv == null || !(fv.isInstanceOf[AstLambda] || fv.isInstanceOf[AstClass]) ||
                  (fv.isInstanceOf[AstLambda] && !containsThis(fv.asInstanceOf[AstLambda])) ||
                  (try { parent(0) } catch { case _: IndexOutOfBoundsException => null }).isInstanceOf[AstNew]
              case _: AstLambda | _: AstClass => false
              case _ => true
            }
            if (safeToFlatten && !retValue.isInstanceOf[AstExpansion]) {
              boundary {
                var flatten = true
                val values = ArrayBuffer.empty[AstNode]
                // Elements after index
                var i = arr.elements.size - 1
                while (i > index) {
                  val elem = arr.elements(i)
                  if (elem.isInstanceOf[AstExpansion]) break(())
                  val dropped = DropSideEffectFree.dropSideEffectFree(elem, this)
                  if (dropped != null) {
                    values.insert(0, dropped.nn)
                    if (flatten && hasSideEffects(dropped.nn, this)) flatten = false
                  }
                  i -= 1
                }
                // The return value
                val rv = if (retValue.isInstanceOf[AstHole]) makeVoid0(retValue) else retValue
                if (!flatten) values.insert(0, rv)
                // Elements before index
                var newIndex = index
                i = index - 1
                while (i >= 0) {
                  val elem = arr.elements(i)
                  if (elem.isInstanceOf[AstExpansion]) break(())
                  val dropped = DropSideEffectFree.dropSideEffectFree(elem, this)
                  if (dropped != null) values.insert(0, dropped.nn)
                  else newIndex -= 1
                  i -= 1
                }
                if (flatten) {
                  values.addOne(rv)
                  return makeSequence(self, values) // @nowarn
                } else {
                  val newArr = new AstArray
                  newArr.elements = values
                  newArr.start = arr.start
                  newArr.end = arr.end
                  val newNum = new AstNumber
                  newNum.value = newIndex.toDouble
                  newNum.start = num.start
                  newNum.end = num.end
                  val newSub = new AstSub
                  newSub.expression = newArr
                  newSub.property = newNum
                  newSub.start = self.start
                  newSub.end = self.end
                  return newSub // @nowarn
                }
              }
            }
          }
        case _ =>
      }
    }

    // Evaluate
    if (optionBool("evaluate") && prop != null) {
      val ev = Evaluate.evaluate(self, this)
      if (ev != null && (ev.asInstanceOf[AnyRef] ne self.asInstanceOf[AnyRef])) {
        val folded = makeNodeFromConstant(ev, self)
        return bestOfExpression(folded, self) // @nowarn
      }
    }

    self
  }

  /** Check if a string is a valid JS identifier name. */
  private def isValidIdentifier(s: String): Boolean =
    s.nonEmpty && {
      val c0 = s.charAt(0)
      (c0.isLetter || c0 == '_' || c0 == '$') && s.forall(c => c.isLetterOrDigit || c == '_' || c == '$')
    }

  /** Check if a string is a JS reserved word. */
  private def isReservedWord(s: String): Boolean =
    ssg.js.parse.Token.ALL_RESERVED_WORDS.contains(s)

  /** Optimize optional chain expressions — unwrap when receiver is non-nullish. */
  private def optimizeChain(self: AstChain): AstNode = {
    if (self.expression == null) return self // @nowarn

    // If the entire chain expression is nullish, replace with void 0
    if (isNullish(self.expression.nn, this)) {
      val p = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
      // `delete x?.y` → `delete 0` (not `delete undefined` which would be a syntax error)
      p match {
        case up: AstUnaryPrefix if up.operator == "delete" =>
          return makeNodeFromConstant(0, self) // @nowarn
        case _ =>
      }
      return makeVoid0(self) // @nowarn
    }

    // If expression is PropAccess or Call, keep chain; otherwise unwrap
    self.expression.nn match {
      case _: AstPropAccess | _: AstCall =>
        self
      case other =>
        // The child might have swapped itself — keep AST valid
        other
    }
  }

  /** Optimize symbol references — inline or replace with constants. */
  private def optimizeSymbolRef(self: AstSymbolRef): AstNode = {
    // Don't optimize LHS references (assignment targets)
    if (isLhs() != null) return self // @nowarn

    // Don't optimize inside `with` blocks (all bets are off)
    if (findParent[AstWith] != null) return self // @nowarn

    // Replace undeclared references to well-known globals
    if (isUndeclaredRef(self)) {
      self.name match {
        case "undefined" =>
          val undef = new AstUndefined
          undef.start = self.start
          undef.end = self.end
          return optimizeUndefined(undef) // @nowarn
        case "NaN" =>
          val nan = new AstNaN
          nan.start = self.start
          nan.end = self.end
          return optimizeNaN(nan) // @nowarn
        case "Infinity" =>
          val inf = new AstInfinity
          inf.start = self.start
          inf.end = self.end
          return optimizeInfinity(inf) // @nowarn
        case _ =>
      }
    }

    // Inline
    if (optionBool("reduce_vars")) {
      inlineIntoSymbolRef(self, this)
    } else {
      self
    }
  }

  /** Walk up the parent stack to find the enclosing scope, then look up the variable `name` in it.
    *
    * Port of `find_variable(compressor, name)` from index.js:655-665.
    */
  private def findVariable(name: String): SymbolDef | Null = {
    var i = 0
    var scope: AstNode | Null = parent(i)
    while (scope != null) {
      scope.nn match {
        case _: AstScope => // found a scope — break out of the loop
          val sc = scope.nn.asInstanceOf[AstScope]
          return ScopeAnalysis.findVariable(sc, name) // @nowarn
        case c: AstCatch if c.argname != null =>
          c.argname.nn match {
            case sym: AstSymbol =>
              val defn = sym.definition()
              if (defn != null) {
                scope = defn.nn.scope
                return ScopeAnalysis.findVariable(scope.nn.asInstanceOf[AstScope], name) // @nowarn
              }
            case _ => // argname is not a symbol — continue walking
          }
        case _ => // not a scope or catch — keep walking
      }
      i += 1
      scope = parent(i)
    }
    null
  }

  /** Check if `lhs` is "atomic" with respect to `self` — i.e. it's a symbol ref or the same node type.
    *
    * Port of `is_atomic(lhs, self)` from index.js:2963-2964.
    */
  private def isAtomic(lhs: AstNode, self: AstNode): Boolean =
    lhs.isInstanceOf[AstSymbolRef] || lhs.nodeType == self.nodeType

  /** Apply the `unsafe_undefined` option: find a variable called `undefined` and turn `self` into a reference to it.
    *
    * Port of `unsafe_undefined_ref(self, compressor)` from index.js:2968-2982.
    */
  private def unsafeUndefinedRef(self: AstNode): AstNode | Null = {
    if (optionBool("unsafe_undefined")) {
      val undef = findVariable("undefined")
      if (undef != null) {
        val ref = new AstSymbolRef
        ref.start = self.start
        ref.end = self.end
        ref.name = "undefined"
        ref.scope = undef.nn.scope
        ref.thedef = undef
        setFlag(ref, UNDEFINED)
        return ref // @nowarn
      }
    }
    null
  }

  /** Optimize `undefined` -> `void 0` (or unsafe_undefined ref if enabled). */
  private def optimizeUndefined(self: AstUndefined): AstNode = {
    val symbolref = unsafeUndefinedRef(self)
    if (symbolref != null) return symbolref.nn // @nowarn
    val lhs = isLhs()
    if (lhs != null && isAtomic(lhs.nn, self)) return self // @nowarn
    makeVoid0(self)
  }

  /** Optimize `Infinity` -> `1/0` (unless keep_infinity and not shadowed). */
  private def optimizeInfinity(self: AstInfinity): AstNode = {
    val lhs = isLhs()
    if (lhs != null && isAtomic(lhs.nn, self)) return self // @nowarn — don't optimize atomic LHS
    if (
      optionBool("keep_infinity")
      && !(lhs != null && !isAtomic(lhs.nn, self))
      && findVariable("Infinity") == null
    ) {
      return self // @nowarn
    }
    val one = new AstNumber
    one.start = self.start
    one.end = self.end
    one.value = 1.0

    val zero = new AstNumber
    zero.start = self.start
    zero.end = self.end
    zero.value = 0.0

    val div = new AstBinary
    div.start = self.start
    div.end = self.end
    div.operator = "/"
    div.left = one
    div.right = zero
    div
  }

  /** Optimize `NaN` -> `0/0` only when NaN is shadowed or LHS is non-atomic. */
  private def optimizeNaN(self: AstNaN): AstNode = {
    val lhs = isLhs()
    if ((lhs != null && !isAtomic(lhs.nn, self))
      || findVariable("NaN") != null) {
      val zero1 = new AstNumber
      zero1.start = self.start
      zero1.end = self.end
      zero1.value = 0.0

      val zero2 = new AstNumber
      zero2.start = self.start
      zero2.end = self.end
      zero2.value = 0.0

      val div = new AstBinary
      div.start = self.start
      div.end = self.end
      div.operator = "/"
      div.left = zero1
      div.right = zero2
      return div // @nowarn
    }
    self
  }

  /** Optimize boolean literals in boolean context. */
  private def optimizeBoolean(self: AstBoolean): AstNode = {
    val selfValue = if (self.isInstanceOf[AstTrue]) 1.0 else 0.0
    if (inBooleanContext()) {
      val num = new AstNumber; num.start = self.start; num.end = self.end; num.value = selfValue
      return num // @nowarn
    }
    val p = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
    if (optionBool("booleans_as_integers")) {
      // Relax === to == in parent binary
      p match {
        case bin: AstBinary if bin.operator == "===" || bin.operator == "!==" =>
          bin.operator = bin.operator.substring(0, bin.operator.length - 1)
        case _ =>
      }
      val num = new AstNumber; num.start = self.start; num.end = self.end; num.value = selfValue
      return num // @nowarn
    }
    if (optionBool("booleans")) {
      // In == / != context, use number directly
      p match {
        case bin: AstBinary if bin.operator == "==" || bin.operator == "!=" =>
          val num = new AstNumber; num.start = self.start; num.end = self.end; num.value = selfValue
          return num // @nowarn
        case _ =>
      }
      // true -> !0, false -> !1
      val inner = new AstNumber; inner.start = self.start; inner.end = self.end
      inner.value = 1.0 - selfValue
      val not = new AstUnaryPrefix; not.start = self.start; not.end = self.end
      not.operator = "!"; not.expression = inner
      return not // @nowarn
    }
    self
  }

  /** Optimize default assignment — remove `= undefined`. */
  private def optimizeDefaultAssign(self: AstDefaultAssign): AstNode =
    if (!optionBool("evaluate")) self
    else {
      val evaluateRight = if (self.right != null) Evaluate.evaluate(self.right.nn, this) else null

      // `[x = undefined] = foo` → `[x] = foo`
      // `(arg = undefined) => ...` → `(arg) => ...` (unless keep_fargs)
      if (evaluateRight != null && isUndefined(self.right.nn, this)) {
        // Check keep_fargs context: if parent is a Lambda, only drop when
        // keep_fargs is false, or parent is an IIFE
        val par = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
        par match {
          case lambda: AstLambda =>
            val keepFargs = optionBool("keep_fargs")
            if (!keepFargs) {
              return self.left.nn // @nowarn
            }
            // Check if parent's parent is a Call with the lambda as expression (IIFE)
            val gpar = try { parent(1) } catch { case _: IndexOutOfBoundsException => null }
            gpar match {
              case call: AstCall if call.expression != null && (call.expression.nn.asInstanceOf[AnyRef] eq lambda.asInstanceOf[AnyRef]) =>
                return self.left.nn // @nowarn
              case _ =>
            }
          case _ =>
            return self.left.nn // @nowarn — not inside a lambda, safe to drop
        }
      } else if (evaluateRight != null && (evaluateRight.asInstanceOf[AnyRef] ne self.right.nn.asInstanceOf[AnyRef])) {
        // Replace with simpler constant form
        val evalNode = makeNodeFromConstant(evaluateRight, self.right.nn)
        self.right = bestOfExpression(evalNode, self.right.nn)
      }
      self
    }

  /** Optimize array literals — inline spread of array literals, boolean context. */
  private def optimizeArray(self: AstArray): AstNode = {
    // Inline array-like spread: [...[a, b, c]] → [a, b, c]
    if (self.elements.nonEmpty) {
      val flattened = ArrayBuffer.empty[AstNode]
      var changed = false
      for (elem <- self.elements) {
        elem match {
          case exp: AstExpansion if exp.expression != null =>
            exp.expression.nn match {
              case arr: AstArray =>
                flattened.addAll(arr.elements)
                changed = true
              case _ =>
                flattened.addOne(elem)
            }
          case _ =>
            flattened.addOne(elem)
        }
      }
      if (changed) self.elements = flattened
    }
    literalsInBooleanContext(self)
  }

  /** Optimize object literals — inline spread of object literals, boolean context. */
  private def optimizeObject(self: AstObject): AstNode = {
    // Inline object-prop spread: {...{a: 1, b: 2}} → {a: 1, b: 2}
    if (self.properties.nonEmpty) {
      val flattened = ArrayBuffer.empty[AstNode]
      var changed = false
      for (prop <- self.properties) {
        prop match {
          case exp: AstExpansion if exp.expression != null =>
            exp.expression.nn match {
              case obj: AstObject =>
                flattened.addAll(obj.properties)
                changed = true
              case _ =>
                flattened.addOne(prop)
            }
          case _ =>
            flattened.addOne(prop)
        }
      }
      if (changed) self.properties = flattened
    }
    literalsInBooleanContext(self)
  }

  /** Optimize yield expressions — remove `yield undefined`. */
  private def optimizeYield(self: AstYield): AstNode = {
    if (self.expression != null && !self.isStar && isUndefined(self.expression.nn, this)) {
      self.expression = null
    }
    self
  }

  /** Optimize class — remove empty static blocks. */
  private def optimizeClass(self: AstClass): AstNode = {
    // Remove empty static blocks from class properties
    if (self.properties.nonEmpty) {
      self.properties = self.properties.filterNot {
        case sb: AstClassStaticBlock => sb.body.forall(isEmpty)
        case _ => false
      }
    }
    self
  }

  // -----------------------------------------------------------------------
  // Shared helpers
  // -----------------------------------------------------------------------

  /** In boolean context, `[1,2,3]` or `{a:1}` can be `[1,2,3], true`. */
  private def literalsInBooleanContext(self: AstNode): AstNode =
    if (inBooleanContext()) {
      val trueNode = new AstTrue
      trueNode.start = self.start
      trueNode.end = self.end
      val optimized = makeSequence(self, ArrayBuffer(self, trueNode))
      bestOfExpression(optimized, self)
    } else {
      self
    }

  /** Create `void 0` from a node, preserving source position. */
  private def makeVoid0(orig: AstNode): AstNode = {
    val zero = new AstNumber
    zero.start = orig.start
    zero.end = orig.end
    zero.value = 0.0

    val prefix = new AstUnaryPrefix
    prefix.start = orig.start
    prefix.end = orig.end
    prefix.operator = "void"
    prefix.expression = zero
    prefix
  }

  /** Negate a boolean expression, choosing the shortest representation.
    *
    * Ported from inference.js def_negate. For UnaryPrefix("!"), unwraps; for Binary comparisons, flips the operator; for &&/||, applies De Morgan's law recursively.
    */
  /** Check if a node contains a `this` reference (stops at non-arrow scope boundaries). */
  private def containsThis(node: AstNode): Boolean = {
    var found = false
    val tw = new TreeWalker((n, _) => {
      if (found) true
      else if (n.isInstanceOf[AstThis]) { found = true; true }
      else if (!(n.asInstanceOf[AnyRef] eq node.asInstanceOf[AnyRef]) && n.isInstanceOf[AstScope] && !n.isInstanceOf[AstArrow]) true
      else null
    })
    node.walk(tw)
    found
  }

  /** Negate a boolean expression, choosing the shortest representation.
    *
    * Ported from inference.js def_negate. For UnaryPrefix("!"), unwraps; for Binary comparisons, flips the operator; for &&/||, applies De Morgan's law recursively.
    */
  @annotation.nowarn("msg=unused private member") // used by optimizeIf expansion
  private def negate(node: AstNode, firstInStatement: Boolean = false): AstNode = {
    def basicNegation(exp: AstNode): AstNode = {
      val neg = new AstUnaryPrefix
      neg.operator = "!"
      neg.expression = exp
      neg.start = exp.start
      neg.end = exp.end
      neg
    }
    def best(orig: AstNode, alt: AstNode, fis: Boolean): AstNode = {
      val negated = basicNegation(orig)
      if (fis) {
        val stat = new AstSimpleStatement; stat.body = alt; stat.start = alt.start; stat.end = alt.end
        if (bestOfExpression(negated, stat) eq stat) alt else negated
      } else {
        bestOfExpression(negated, alt)
      }
    }
    node match {
      case _: AstStatement =>
        throw new RuntimeException("Cannot negate a statement")
      case up: AstUnaryPrefix if up.operator == "!" =>
        up.expression.nn
      case seq: AstSequence if seq.expressions.size >= 2 =>
        val exprs = ArrayBuffer.from(seq.expressions)
        exprs(exprs.size - 1) = negate(exprs.last, false)
        makeSequence(seq, exprs)
      case cond: AstConditional =>
        val neg = new AstConditional
        neg.condition = cond.condition
        neg.consequent = negate(cond.consequent.nn, false)
        neg.alternative = negate(cond.alternative.nn, false)
        neg.start = cond.start
        neg.end = cond.end
        best(node, neg, firstInStatement)
      case bin: AstBinary =>
        val negBin = new AstBinary
        negBin.start = bin.start
        negBin.end = bin.end
        negBin.left = bin.left
        negBin.right = bin.right
        if (optionBool("unsafe_comps")) {
          bin.operator match {
            case "<=" => negBin.operator = ">"; return negBin // @nowarn
            case "<"  => negBin.operator = ">="; return negBin // @nowarn
            case ">=" => negBin.operator = "<"; return negBin // @nowarn
            case ">"  => negBin.operator = "<="; return negBin // @nowarn
            case _ =>
          }
        }
        bin.operator match {
          case "==" => negBin.operator = "!="; negBin
          case "!=" => negBin.operator = "=="; negBin
          case "===" => negBin.operator = "!=="; negBin
          case "!==" => negBin.operator = "==="; negBin
          case "&&" =>
            negBin.operator = "||"
            negBin.left = negate(bin.left.nn, firstInStatement)
            negBin.right = negate(bin.right.nn, false)
            best(node, negBin, firstInStatement)
          case "||" =>
            negBin.operator = "&&"
            negBin.left = negate(bin.left.nn, firstInStatement)
            negBin.right = negate(bin.right.nn, false)
            best(node, negBin, firstInStatement)
          case _ => basicNegation(node)
        }
      // AST_Function, AST_Class, AST_Arrow — all get basic negation
      case _ => basicNegation(node)
    }
  }

  // self() is inherited from TreeWalker

  // findScope() is inherited from TreeWalker

  /** Reset optimization flags before each pass. */
  private def resetOptFlags(toplevel: AstToplevel): Unit = {
    val hasTopRetain = topRetain != null && (topRetain ne ((_: Any) => false))
    val tw = new TreeWalker((node, _) => {
      clearFlag(node, CLEAR_BETWEEN_PASSES)

      // Set TOP flag on retained top-level definitions
      if (hasTopRetain) {
        node match {
          case defun: AstDefun if defun.name != null =>
            defun.name.nn match {
              case sym: AstSymbol if sym.thedef != null && topRetain(sym.thedef) =>
                setFlag(defun, TOP)
              case _ =>
            }
          case _ =>
        }
      }

      null // continue walking
    })
    toplevel.walk(tw)
    // Note: reduceVars is called separately in the compress() loop
  }

  /** Drop console.* calls from the AST.
    *
    * Replaces `console.method(...)` calls with `void 0`. When `dropConsole` is `Methods(names)`, only drops calls to those specific methods.
    */
  private def dropConsole(toplevel: AstToplevel): AstToplevel = {
    val methodFilter: Option[Set[String]] = options.dropConsole match {
      case DropConsoleConfig.Methods(names) => Some(names)
      case _                               => None
    }

    val tt = new TreeTransformer(
      before = (self, _) => {
        self match {
          case call: AstCall =>
            call.expression match {
              case pa: AstPropAccess =>
                // Walk up property chains: console.log.bind(console) etc.
                var nameNode: AstNode = pa.expression.nn
                var property: String | AstNode = pa.property
                var keepWalking = true
                while (keepWalking) {
                  nameNode match {
                    case inner: AstPropAccess =>
                      property = inner.property
                      nameNode = inner.expression.nn
                    case _ =>
                      keepWalking = false
                  }
                }

                // Check if filtering by method name
                val propertyName = property match {
                  case s: String => s
                  case _         => null
                }
                if (methodFilter.isDefined && propertyName != null && !methodFilter.get.contains(propertyName)) {
                  null // not a filtered method, keep it
                } else if (Inference.isUndeclaredRef(nameNode) && nameNode.isInstanceOf[AstSymbolRef] && nameNode.asInstanceOf[AstSymbolRef].name == "console") {
                  // Replace with void 0
                  makeVoid0(self)
                } else {
                  null // not a console reference
                }

              case _ => null // not a property access call
            }

          case _ => null // not a call node
        }
      }
    )
    toplevel.transform(tt).asInstanceOf[AstToplevel]
  }

  // -----------------------------------------------------------------------
  // Object property handlers (lift_key, concise method, keyval, destructuring)
  // -----------------------------------------------------------------------

  /** lift_key: convert computed prop ["p"]:1 → p:1, [42]:1 → 42:1 */
  private def liftKey(self: AstObjectProperty): AstNode = {
    if (!optionBool("computed_props")) return self // @nowarn
    self.key match {
      case str: AstString =>
        val key = str.value
        if (key == "__proto__") return self // @nowarn
        if (key == "constructor" && parent(0).isInstanceOf[AstClass]) return self // @nowarn
        self match {
          case kv: AstObjectKeyVal =>
            kv.quote = str.quote
            kv.key = key
          case _ =>
            self.key = {
              val sym = new AstSymbolMethod
              sym.name = key
              sym.start = str.start
              sym.end = str.end
              sym
            }
        }
      case num: AstNumber =>
        val key = num.value.toString
        if (key == "__proto__") return self // @nowarn
        self match {
          case kv: AstObjectKeyVal =>
            kv.key = key
          case _ =>
            self.key = {
              val sym = new AstSymbolMethod
              sym.name = key
              sym.start = num.start
              sym.end = num.end
              sym
            }
        }
      case _ =>
    }
    self
  }

  /** Optimize concise method: lift_key + method→arrow conversion. */
  private def optimizeConciseMethod(self: AstConciseMethod): AstNode = {
    liftKey(self)
    // p(){return x;} → p:()=>x (when safe)
    if (
      optionBool("arrows")
      && parent(0).isInstanceOf[AstObject]
      && self.value != null
    ) {
      self.value.nn match {
        case fn: AstLambda
            if !fn.isGenerator
              && !fn.usesArguments
              && !fn.pinned
              && fn.body.size == 1
              && fn.body(0).isInstanceOf[AstReturn]
              && fn.body(0).asInstanceOf[AstReturn].value != null =>
          // Check no `this` usage
          var usesThis = false
          val tw = new TreeWalker((node, _) => {
            node match {
              case _: AstThis  => usesThis = true; true
              case _: AstScope => true
              case _           => null
            }
          })
          fn.walk(tw)
          if (!usesThis) {
            val arrow = new AstArrow
            arrow.start = fn.start
            arrow.end = fn.end
            arrow.body = fn.body
            arrow.argnames = fn.argnames
            arrow.isAsync = fn.isAsync
            arrow.isGenerator = fn.isGenerator
            val kv = new AstObjectKeyVal
            kv.start = self.start
            kv.end = self.end
            kv.key = self.key match {
              case sym: AstSymbolMethod => sym.name
              case other               => other
            }
            kv.value = arrow
            kv.quote = self.quote
            return kv // @nowarn
          }
        case _ =>
      }
    }
    self
  }

  /** Optimize object key-value: lift_key + function→concise method.
    *
    * ISS-112 fix: Add unsafe_methods optimization that converts
    * p:function(){} to p(){} and p:()=>{} to p(){} (concise method).
    */
  private def optimizeObjectKeyVal(self: AstObjectKeyVal): AstNode = {
    liftKey(self)

    // p:function(){} ---> p(){}
    // p:function*(){} ---> *p(){}
    // p:async function(){} ---> async p(){}
    // p:()=>{} ---> p(){}
    // p:async()=>{} ---> async p(){}
    val unsafeMethods = option("unsafe_methods")
    if (
      unsafeMethods != null && unsafeMethods != false
      && options.ecma >= 2015
      && self.value != null
    ) {
      // Check if unsafe_methods is a regex filter or just true
      // NOTE: keyStr extracted but regex test simplified for now (original checks RegExp.test(key + ""))
      @annotation.nowarn("msg=unused local definition")
      val keyStr = self.key match {
        case s: String => s
        case sym: AstSymbol => sym.name
        case _ => ""
      }
      val passesFilter = unsafeMethods match {
        case true | 1 | "true" => true
        case _ => true // Simplified - original checks RegExp.test(keyStr)
      }
      if (passesFilter) {
        val value = self.value.nn
        val isArrowWithBlock = value.isInstanceOf[AstArrow] &&
          value.asInstanceOf[AstArrow].body.nonEmpty &&
          !containsThis(value)
        val isFunction = value.isInstanceOf[AstFunction] && value.asInstanceOf[AstLambda].name == null

        if (isArrowWithBlock || isFunction) {
          val lambda = value.asInstanceOf[AstLambda]
          val concise = new AstConciseMethod
          concise.start = self.start
          concise.end = self.end
          concise.key = self.key match {
            case s: String =>
              val sym = new AstSymbolMethod
              sym.name = s
              sym.start = self.start
              sym.end = self.end
              sym
            case other => other
          }
          // Create an Accessor from the lambda
          val accessor = new AstAccessor
          accessor.start = lambda.start
          accessor.end = lambda.end
          accessor.argnames = lambda.argnames
          accessor.body = lambda.body
          accessor.isAsync = lambda.isAsync
          accessor.isGenerator = lambda.isGenerator
          accessor.usesArguments = lambda.usesArguments
          accessor.variables = lambda.variables
          accessor.usesWith = lambda.usesWith
          accessor.usesEval = lambda.usesEval
          accessor.parentScope = lambda.parentScope
          accessor.enclosed = lambda.enclosed
          accessor.cname = lambda.cname
          accessor.blockScope = lambda.blockScope
          concise.value = accessor
          concise.quote = self.quote
          return concise // @nowarn
        }
      }
    }
    self
  }

  /** Optimize destructuring: prune unused properties (pure_getters + unused).
    *
    * ISS-143 fix: Export check must match Const/Let/Var at ancestor index 1.
    */
  private def optimizeDestructuring(self: AstDestructuring): AstNode = {
    if (
      optionBool("pure_getters")
      && optionBool("unused")
      && !self.isArray
      && self.names.nonEmpty
      && !self.names.last.isInstanceOf[AstExpansion]
    ) {
      // Check this isn't inside an export declaration
      // ISS-143: ancestors = [/^VarDef$/, /^(Const|Let|Var)$/, /^Export$/]
      val isExportDecl = {
        var a = 0
        var p = 0
        var matched = true
        while (a < 3 && matched) {
          val par = try { parent(p) } catch { case _: IndexOutOfBoundsException => null }
          if (par == null) { matched = false }
          else if (a == 0 && par.nn.nodeType == "Destructuring") { p += 1 }
          else {
            val parType = par.nn.nodeType
            val expected = a match {
              case 0 => parType == "VarDef"
              case 1 => parType == "Const" || parType == "Let" || parType == "Var"
              case 2 => parType == "Export"
              case _ => false
            }
            if (!expected) {
              matched = false
            } else { a += 1; p += 1 }
          }
        }
        matched && a == 3
      }
      if (!isExportDecl) {
        val kept = self.names.filter {
          case kv: AstObjectKeyVal =>
            kv.key match {
              case _: String =>
                kv.value match {
                  case sym: AstSymbolDeclaration =>
                    val d = sym.definition()
                    if (d != null) {
                      val dd = d.nn
                      // Retain if referenced, or global without toplevel.vars
                      dd.references.nonEmpty || (dd.global && !toplevel.vars) || topRetain(dd)
                    } else {
                      true
                    }
                  case _ => true
                }
              case _ => true
            }
          case _ => true
        }
        if (kept.size != self.names.size) {
          self.names = kept
        }
      }
    }
    self
  }

  // -----------------------------------------------------------------------
  // Batch 2: Missing critical handlers
  // -----------------------------------------------------------------------

  /** Optimize unary prefix expressions: !, void, typeof, -, +, ~, delete.
    *
    * ISS-107 fix: Add negate binary in boolean context, tilde-xor distributivity,
    * and is_32_bit_integer check for double-tilde.
    */
  private def optimizeUnaryPrefix(self: AstUnaryPrefix): AstNode = {
    if (self.expression == null) return self // @nowarn

    val e = self.expression.nn

    // delete on non-ref/non-prop → (expr, true)
    if (
      self.operator == "delete"
      && !e.isInstanceOf[AstSymbolRef]
      && !e.isInstanceOf[AstPropAccess]
      && !e.isInstanceOf[AstChain]
      && !isIdentifierAtom(e)
    ) {
      val trueNode = new AstTrue
      trueNode.start = self.start
      trueNode.end = self.end
      return makeSequence(self, ArrayBuffer(e, trueNode)) // @nowarn
    }

    // void 0 shortcut → unsafe_undefined_ref if enabled, otherwise return as-is
    // ISS-142: Add unsafe_undefined_ref for void 0
    if (self.operator == "void" && e.isInstanceOf[AstNumber] && e.asInstanceOf[AstNumber].value == 0.0) {
      val ref = unsafeUndefinedRef(self)
      return if (ref != null) ref.nn else self // @nowarn
    }

    // Lift sequences: !(a, b) → (a, !b)
    val lifted = liftSequencesUnary(self)
    if (!(lifted eq self)) return lifted // @nowarn

    // void expr with side_effects → drop pure parts
    if (optionBool("side_effects") && self.operator == "void") {
      val dropped = DropSideEffectFree.dropSideEffectFree(e, this)
      if (dropped == null) {
        return makeVoid0(self) // @nowarn
      } else if (!(dropped.nn eq e)) {
        self.expression = dropped.nn
        return self // @nowarn
      }
    }

    // Boolean context optimizations
    if (inBooleanContext()) {
      self.operator match {
        case "!" =>
          // !!x in boolean context → x
          e match {
            case inner: AstUnaryPrefix if inner.operator == "!" =>
              return inner.expression.nn // @nowarn
            // ISS-107: !Binary → negate in boolean context (best_of with negate)
            case bin: AstBinary =>
              val negated = negate(bin, firstInStatement(this))
              val result = bestOf(this, self, negated)
              if (!(result eq self)) return result // @nowarn
            case _ =>
          }
        case "typeof" =>
          // typeof always returns a non-empty string → true in boolean context
          if (e.isInstanceOf[AstSymbolRef]) {
            val trueNode = new AstTrue
            trueNode.start = self.start
            trueNode.end = self.end
            return trueNode // @nowarn
          } else {
            val trueNode = new AstTrue
            trueNode.start = self.start
            trueNode.end = self.end
            return makeSequence(self, ArrayBuffer(e, trueNode)) // @nowarn
          }
        case _ =>
      }
    }

    // -Infinity handling
    if (self.operator == "-" && e.isInstanceOf[AstInfinity]) {
      // let it be handled by evaluate below
    }

    // ISS-107: ~(x^y) => x^~y (tilde-xor distributivity)
    if (self.operator == "~" && e.isInstanceOf[AstBinary]) {
      val bin = e.asInstanceOf[AstBinary]
      if (bin.operator == "^" && bin.left != null && bin.right != null) {
        val newTilde = new AstUnaryPrefix
        newTilde.operator = "~"
        newTilde.expression = bin.right
        newTilde.start = bin.right.nn.start
        newTilde.end = bin.right.nn.end
        val newBin = new AstBinary
        newBin.operator = "^"
        newBin.left = bin.left
        newBin.right = newTilde
        newBin.start = self.start
        newBin.end = self.end
        return newBin // @nowarn
      }
    }

    // Distribute +/- over * / %: -(a * b) → (-a) * b
    if ((self.operator == "+" || self.operator == "-") && e.isInstanceOf[AstBinary]) {
      val bin = e.asInstanceOf[AstBinary]
      if (bin.operator == "*" || bin.operator == "/" || bin.operator == "%") {
        if (bin.left != null) {
          val newUnary = new AstUnaryPrefix
          newUnary.operator = self.operator
          newUnary.expression = bin.left.nn
          newUnary.start = bin.left.nn.start
          newUnary.end = bin.left.nn.end
          val newBin = new AstBinary
          newBin.operator = bin.operator
          newBin.left = newUnary
          newBin.right = bin.right
          newBin.start = self.start
          newBin.end = self.end
          return newBin // @nowarn
        }
      }
    }

    // Evaluate
    if (optionBool("evaluate")) {
      // ISS-107: ~~x → x when x is 32-bit integer OR in 32-bit context
      if (
        self.operator == "~"
        && e.isInstanceOf[AstUnaryPrefix]
        && e.asInstanceOf[AstUnaryPrefix].operator == "~"
      ) {
        val innerExpr = e.asInstanceOf[AstUnaryPrefix].expression
        if (innerExpr != null) {
          // Check if inner expression is 32-bit integer or we're in 32-bit context
          if (Inference.is32BitInteger(innerExpr.nn, this) || in32BitContext()) {
            return innerExpr.nn // @nowarn
          }
        }
      }

      // General evaluation (skip for -number/-Infinity/-BigInt to avoid infinite recursion)
      if (
        self.operator != "-"
        || !(e.isInstanceOf[AstNumber] || e.isInstanceOf[AstInfinity] || e.isInstanceOf[AstBigInt])
      ) {
        val ev = Evaluate.evaluate(self, this)
        if (ev != null && (ev.asInstanceOf[AnyRef] ne self.asInstanceOf[AnyRef])) {
          val evNode = makeNodeFromConstant(ev, self)
          return bestOfExpression(evNode, self) // @nowarn
        }
      }
    }

    self
  }

  /** Optimize unary postfix (x++, x--): just lift sequences. */
  private def optimizeUnaryPostfix(self: AstUnaryPostfix): AstNode =
    liftSequencesUnary(self)

  /** Optimize sequence expressions: remove side-effect-free prefix, trim trailing undefined.
    *
    * ISS-143 fix: Add first_in_statement, trim_right_for_undefined, maintain_this_binding.
    */
  private def optimizeSequence(self: AstSequence): AstNode = {
    if (!optionBool("side_effects")) return self // @nowarn
    if (self.expressions.isEmpty) return self // @nowarn

    val expressions = ArrayBuffer.empty[AstNode]

    // filter_for_side_effects: drop side-effect-free expressions (keep last always)
    // ISS-143: Use first_in_statement for correct first expression handling
    var first = firstInStatement(this)
    val lastIdx = self.expressions.size - 1
    var i = 0
    while (i <= lastIdx) {
      var expr: AstNode | Null = self.expressions(i)
      if (i < lastIdx) {
        expr = DropSideEffectFree.dropSideEffectFree(expr.nn, this, first)
      }
      if (expr != null) {
        // merge_sequence: flatten nested sequences
        expr.nn match {
          case seq: AstSequence => expressions.addAll(seq.expressions)
          case _                => expressions.addOne(expr.nn)
        }
        first = false
      }
      i += 1
    }

    // ISS-143: trim_right_for_undefined — trim trailing undefined expressions
    var end = expressions.size - 1
    while (end > 0 && isUndefined(expressions(end), this)) {
      end -= 1
    }
    if (end < expressions.size - 1) {
      // Wrap the new last expression in void to preserve undefined return
      val voidExpr = new AstUnaryPrefix
      voidExpr.operator = "void"
      voidExpr.expression = expressions(end)
      voidExpr.start = expressions(end).start
      voidExpr.end = expressions(end).end
      expressions(end) = voidExpr
      // Truncate to end + 1
      while (expressions.size > end + 1) expressions.remove(expressions.size - 1)
    }

    // ISS-143: maintain_this_binding for single expression result
    if (end == 0 || expressions.size == 1) {
      val p = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
      val s = try { this.self() } catch { case _: IndexOutOfBoundsException => self.asInstanceOf[AstNode] }
      val result = maintainThisBinding(if (p != null) p.nn else self, s, expressions(0))
      if (!result.isInstanceOf[AstSequence]) return optimizeNode(result) // @nowarn
      return result // @nowarn
    }

    self.expressions = expressions
    self
  }

  /** Optimize `new` expressions: unsafe constructor replacements.
    *
    * ISS-142 fix: Add RegExp/Function/Error/Array→Call conversion.
    * Original: `new Object/RegExp/Function/Error/Array(...)` → `Object/RegExp/Function/Error/Array(...)`
    * This delegates to the call optimizer which handles these builtins.
    */
  private def optimizeNew(self: AstNew): AstNode = {
    if (!optionBool("unsafe") || self.expression == null) return self // @nowarn
    self.expression.nn match {
      case ref: AstSymbolRef if isUndeclaredRef(ref) =>
        // ISS-142: new Object/RegExp/Function/Error/Array → Call form
        // This allows the call optimizer to handle these constructors
        val builtins = Set("Object", "RegExp", "Function", "Error", "Array")
        if (builtins.contains(ref.name)) {
          // Convert new X(...) to X(...) and let optimizeCall handle it
          val call = new AstCall
          call.start = self.start
          call.end = self.end
          call.expression = self.expression
          call.args = self.args
          call.optional = self.optional
          return optimizeCall(call) // @nowarn
        }
      case _ =>
    }
    // Fall through to call optimization for other cases
    optimizeCall(self)
  }

  /** Optimize template strings: fold constant segments, unwrap single-segment.
    *
    * ISS-143 fix: Add PrefixedTemplateString guard, length check, template-in-template
    * flatten, and 3-segment folds to binary expressions.
    */
  private def optimizeTemplateString(self: AstTemplateString): AstNode = {
    // ISS-143: Skip if evaluate is off or parent is PrefixedTemplateString
    if (!optionBool("evaluate")) return self // @nowarn
    val par = try { parent(0) } catch { case _: IndexOutOfBoundsException => null }
    if (par != null && par.nn.isInstanceOf[AstPrefixedTemplateString]) return self // @nowarn

    if (self.segments.isEmpty) return self // @nowarn

    // Fold adjacent constant string segments
    val segments = ArrayBuffer.empty[AstNode]
    var i = 0
    while (i < self.segments.size) {
      val segment = self.segments(i)
      segment match {
        case ts: AstTemplateSegment =>
          // Check if previous is also a template segment — merge
          if (segments.nonEmpty) {
            segments.last match {
              case prev: AstTemplateSegment =>
                prev.value = prev.value + ts.value
              case _ =>
                segments.addOne(ts)
            }
          } else {
            segments.addOne(ts)
          }
          i += 1

        case inner: AstTemplateString =>
          // ISS-143: template-in-template flatten
          // `before ${`innerBefore ${any} innerAfter`} after` => `before innerBefore ${any} innerAfter after`
          val inners = inner.segments
          if (inners.nonEmpty) {
            // Merge first inner segment into previous
            if (segments.nonEmpty) {
              segments.last match {
                case prev: AstTemplateSegment =>
                  inners(0) match {
                    case innerFirst: AstTemplateSegment =>
                      prev.value = prev.value + innerFirst.value
                      // Add remaining inner segments
                      var j = 1
                      while (j < inners.size) {
                        segments.addOne(inners(j))
                        j += 1
                      }
                    case _ =>
                      // First inner is not a segment, add all
                      segments.addAll(inners)
                  }
                case _ =>
                  segments.addAll(inners)
              }
            } else {
              segments.addAll(inners)
            }
          }
          i += 1

        case expr: AstNode =>
          // Check if expression evaluates to a string constant
          val ev = Evaluate.evaluate(expr, this)
          val evStr = ev match {
            case s: String => s
            case d: Double => d.toString
            case b: Boolean => b.toString
            case null => "null"
            case _ => null
          }
          // ISS-143: Length check - only fold if constant is shorter than ${segment}
          if (evStr != null && (evStr.asInstanceOf[AnyRef] ne expr.asInstanceOf[AnyRef]) &&
              evStr.length <= AstSize.size(expr) + "${}".length) {
            // There should always be a previous segment if segment is a node
            // Merge into previous segment
            if (segments.nonEmpty) {
              segments.last match {
                case prev: AstTemplateSegment =>
                  // Also merge next segment if present
                  if (i + 1 < self.segments.size) {
                    self.segments(i + 1) match {
                      case nextSeg: AstTemplateSegment =>
                        prev.value = prev.value + evStr + nextSeg.value
                        i += 2 // skip both current expr and next segment
                      case _ =>
                        prev.value = prev.value + evStr
                        i += 1
                    }
                  } else {
                    prev.value = prev.value + evStr
                    i += 1
                  }
                case _ =>
                  // No previous segment to merge into, create one
                  val tsSeg = new AstTemplateSegment
                  tsSeg.value = evStr
                  tsSeg.start = expr.start
                  tsSeg.end = expr.end
                  segments.addOne(tsSeg)
                  i += 1
              }
            } else {
              val tsSeg = new AstTemplateSegment
              tsSeg.value = evStr
              tsSeg.start = expr.start
              tsSeg.end = expr.end
              segments.addOne(tsSeg)
              i += 1
            }
          } else {
            segments.addOne(expr)
            i += 1
          }
      }
    }
    self.segments = segments

    // Single constant segment → string literal
    if (segments.size == 1) {
      segments(0) match {
        case ts: AstTemplateSegment =>
          val str = new AstString
          str.value = ts.value
          str.start = self.start
          str.end = self.end
          return str // @nowarn
        case _ =>
      }
    }

    // ISS-143: 3-segment folds to binary expressions
    if (segments.size == 3 && segments(1).isInstanceOf[AstNode] && !segments(1).isInstanceOf[AstTemplateSegment]) {
      val middle = segments(1)
      val isStringOrNumberOrNullish =
        Inference.isString(middle, this) ||
        Inference.isNumber(middle, this) ||
        isNullish(middle, this) ||
        optionBool("unsafe")
      if (isStringOrNumberOrNullish) {
        val first = segments(0).asInstanceOf[AstTemplateSegment]
        val last = segments(2).asInstanceOf[AstTemplateSegment]
        // `foo${bar}` => "foo" + bar (when last is empty)
        if (last.value == "") {
          val leftStr = new AstString
          leftStr.value = first.value
          leftStr.start = self.start
          leftStr.end = self.end
          val bin = new AstBinary
          bin.operator = "+"
          bin.left = leftStr
          bin.right = middle
          bin.start = self.start
          bin.end = self.end
          return bin // @nowarn
        }
        // `${bar}baz` => bar + "baz" (when first is empty)
        if (first.value == "") {
          val rightStr = new AstString
          rightStr.value = last.value
          rightStr.start = self.start
          rightStr.end = self.end
          val bin = new AstBinary
          bin.operator = "+"
          bin.left = middle
          bin.right = rightStr
          bin.start = self.start
          bin.end = self.end
          return bin // @nowarn
        }
      }
    }

    self
  }

  // -----------------------------------------------------------------------
  // Infrastructure helpers (used by multiple optimization handlers)
  // -----------------------------------------------------------------------

  /** Alternative is_lhs() that works within .optimize() by reading from the TreeWalker stack. */
  def isLhs(): AstNode | Null = {
    try {
      val selfNode = self()
      val parentNode = parent(0)
      if (parentNode == null) null
      else Inference.isLhs(selfNode, parentNode.nn)
    } catch {
      case _: IndexOutOfBoundsException => null // stack empty during initial transform
    }
  }

  /** Lift sequences from a unary expression: !(a, b) → (a, !b) */
  private def liftSequencesUnary(self: AstUnary): AstNode = {
    if (optionBool("sequences") && self.expression != null) {
      self.expression.nn match {
        case seq: AstSequence if seq.expressions.size >= 2 =>
          val exprs = ArrayBuffer.from(seq.expressions)
          val lastExpr = exprs.remove(exprs.size - 1)
          val cloned = self match {
            case _: AstUnaryPrefix =>
              val u = new AstUnaryPrefix
              u.operator = self.operator
              u.expression = lastExpr
              u.start = self.start
              u.end = self.end
              u
            case _ =>
              val u = new AstUnaryPostfix
              u.operator = self.operator
              u.expression = lastExpr
              u.start = self.start
              u.end = self.end
              u
          }
          exprs.addOne(cloned)
          return makeSequence(self, exprs) // @nowarn
        case _ =>
      }
    }
    self
  }

  /** Lift sequences from a binary right operand: x + (a, b) → (a, x + b) when x is side-effect-free. */
  private def liftSequencesBinaryRight(self: AstBinary): AstNode = {
    if (optionBool("sequences") && self.right != null && self.left != null && !hasSideEffects(self.left.nn, this)) {
      self.right.nn match {
        case seq: AstSequence if seq.expressions.size >= 2 =>
          val exprs = seq.expressions
          val last = exprs.size - 1
          // Check if all expressions before the last are side-effect-free (for non-assignment)
          val isAssign = self.operator == "=" && self.left.nn.isInstanceOf[AstSymbolRef]
          var canLift = true
          var splitAt = 0
          while (splitAt < last && canLift) {
            if (!isAssign && hasSideEffects(exprs(splitAt), this)) canLift = false
            else splitAt += 1
          }
          if (splitAt == last) {
            // Lift all: (a, b, c) → a, b, (x + c)
            val x = ArrayBuffer.from(exprs.take(last))
            val cloned = new AstBinary
            cloned.operator = self.operator
            cloned.left = self.left
            cloned.right = exprs(last)
            cloned.start = self.start
            cloned.end = self.end
            x.addOne(cloned)
            return makeSequence(self, x) // @nowarn
          } else if (splitAt > 0) {
            // Partial lift
            val prefix = ArrayBuffer.from(exprs.take(splitAt))
            val cloned = new AstBinary
            cloned.operator = self.operator
            cloned.left = self.left
            val newRight = new AstSequence
            newRight.expressions = ArrayBuffer.from(exprs.drop(splitAt))
            newRight.start = seq.start
            newRight.end = seq.end
            cloned.right = newRight
            cloned.start = self.start
            cloned.end = self.end
            prefix.addOne(cloned)
            return makeSequence(self, prefix) // @nowarn
          }
        case _ =>
      }
    }
    self
  }

  /** flatten_object: convert `{a:1, b:2}.a` → `[1, 2][0]` for property access on literal objects. */
  private def flattenObject(self: AstPropAccess, key: String): AstNode | Null = {
    if (!optionBool("properties")) return null // @nowarn
    if (key == "__proto__") return null // @nowarn
    if (self.isInstanceOf[AstDotHash]) return null // @nowarn

    self.expression match {
      case obj: AstObject if obj != null =>
        val props = obj.properties
        var matchIdx = -1
        var i = props.size - 1
        while (i >= 0 && matchIdx < 0) {
          props(i) match {
            case kv: AstObjectKeyVal if !kv.computedKey() =>
              val propKey = kv.key match {
                case s: String       => s
                case sym: AstSymbol  => sym.name
                case _               => ""
              }
              if (propKey == key) matchIdx = i
            case _ =>
          }
          i -= 1
        }
        if (matchIdx < 0) return null // @nowarn

        // Check all props are flattenable (no computed keys, no getters/setters)
        val allFlattenable = props.forall {
          case kv: AstObjectKeyVal => !kv.computedKey()
          case _ => false
        }
        if (!allFlattenable) return null // @nowarn

        // Build: [v0, v1, ...][matchIdx]
        val elements = ArrayBuffer.empty[AstNode]
        for (p <- props) {
          p match {
            case kv: AstObjectKeyVal if kv.value != null => elements.addOne(kv.value.nn)
            case _ =>
          }
        }
        val arr = new AstArray
        arr.elements = elements
        arr.start = obj.start
        arr.end = obj.end
        val idx = new AstNumber
        idx.value = matchIdx.toDouble
        idx.start = self.start
        idx.end = self.end
        val sub = new AstSub
        sub.expression = arr
        sub.property = idx
        sub.start = self.start
        sub.end = self.end
        sub
      case _ => null
    }
  }

  /** if_break_in_loop: optimize `for (...) { if (...) break; ... }` patterns. */
  private def ifBreakInLoop(self: AstFor): AstNode = {
    val first: AstNode | Null = self.body match {
      case block: AstBlockStatement if block.body.nonEmpty => block.body(0)
      case other => other
    }
    if (first == null) return self // @nowarn

    // Check if first statement is a plain break targeting this loop
    def isBreak(node: AstNode): Boolean =
      node.isInstanceOf[AstBreak] && {
        val target = loopcontrolTarget(node.asInstanceOf[AstLoopControl])
        target != null && (target.nn.asInstanceOf[AnyRef] eq self.asInstanceOf[AnyRef])
      }

    // Case 1: `for (...) { break; ... }` → extract init + condition, drop loop
    if (optionBool("dead_code") && isBreak(first.nn)) {
      val body = ArrayBuffer.empty[AstNode]
      if (self.init != null) {
        self.init.nn match {
          case s: AstStatement => body.addOne(s)
          case expr =>
            val ss = new AstSimpleStatement
            ss.body = expr
            ss.start = expr.start
            ss.end = expr.end
            body.addOne(ss)
        }
      }
      if (self.condition != null) {
        val ss = new AstSimpleStatement
        ss.body = self.condition
        ss.start = self.condition.nn.start
        ss.end = self.condition.nn.end
        body.addOne(ss)
      }
      extractFromUnreachableCode(this, self.body.nn, body)
      val block = new AstBlockStatement
      block.body = body
      block.start = self.start
      block.end = self.end
      return block // @nowarn
    }

    // Case 2: `for (...) { if (cond) break; ... }` → fold cond into for-condition
    first.nn match {
      case ifNode: AstIf if ifNode.body != null && isBreak(ifNode.body.nn) =>
        // if (cond) break; → condition &&= !cond
        if (self.condition != null) {
          val neg = new AstUnaryPrefix
          neg.operator = "!"
          neg.expression = ifNode.condition.nn
          neg.start = ifNode.start
          neg.end = ifNode.end
          val combined = new AstBinary
          combined.operator = "&&"
          combined.left = self.condition
          combined.right = neg
          combined.start = self.start
          combined.end = self.end
          self.condition = combined
        } else {
          val neg = new AstUnaryPrefix
          neg.operator = "!"
          neg.expression = ifNode.condition.nn
          neg.start = ifNode.start
          neg.end = ifNode.end
          self.condition = neg
        }
        // Drop the if statement, keep the rest of the body
        self.body match {
          case block: AstBlockStatement =>
            block.body = ifNode.alternative match {
              case null => block.body.tail
              case alt =>
                val rest = block.body.tail
                alt.nn match {
                  case b: AstBlock => ArrayBuffer.from(b.body) ++ rest
                  case s           => ArrayBuffer(s) ++ rest
                }
            }
          case _ =>
            if (ifNode.alternative != null) self.body = ifNode.alternative
            else {
              val empty = new AstEmptyStatement
              empty.start = self.start
              empty.end = self.end
              self.body = empty
            }
        }

      case ifNode: AstIf if ifNode.alternative != null && isBreak(ifNode.alternative.nn) =>
        // if (cond) {...} else break; → condition &&= cond
        if (self.condition != null) {
          val combined = new AstBinary
          combined.operator = "&&"
          combined.left = self.condition
          combined.right = ifNode.condition.nn
          combined.start = self.start
          combined.end = self.end
          self.condition = combined
        } else {
          self.condition = ifNode.condition
        }
        // Replace the if with its body
        self.body match {
          case block: AstBlockStatement =>
            block.body = ifNode.body.nn match {
              case b: AstBlock => ArrayBuffer.from(b.body) ++ block.body.tail
              case s           => ArrayBuffer(s) ++ block.body.tail
            }
          case _ =>
            self.body = ifNode.body
        }

      case _ =>
    }

    self
  }

  /** Check if an expression is a nullish check (== null or === null || === undefined).
    *
    * ISS-143 fix: Add ===null||===undefined compound form check.
    * This matches the original `is_nullish_check` function that handles both
    * `foo == null` and `foo === null || foo === undefined` patterns.
    */
  @annotation.nowarn("msg=unused private member") // used by optimizeConditional expansion (Batch 4)
  private def isNullishCheck(check: AstNode, checkSubject: AstNode): Boolean = {
    // Early exit if check_subject may throw
    if (hasSideEffects(checkSubject, this)) return false // @nowarn

    check match {
      // Form 1: foo == null
      case binary: AstBinary if binary.operator == "==" && binary.left != null && binary.right != null =>
        val leftNullish = isNullish(binary.left.nn, this)
        val rightNullish = isNullish(binary.right.nn, this)
        if (leftNullish) {
          AstEquivalent.equivalentTo(binary.right.nn, checkSubject)
        } else if (rightNullish) {
          AstEquivalent.equivalentTo(binary.left.nn, checkSubject)
        } else {
          false
        }

      // ISS-143: Form 2: foo === null || foo === undefined
      case binary: AstBinary if binary.operator == "||" && binary.left != null && binary.right != null =>
        var nullCmp: AstBinary | Null = null
        var undefinedCmp: AstBinary | Null = null

        def findComparison(cmp: AstNode): Boolean = cmp match {
          case cmpBin: AstBinary if (cmpBin.operator == "===" || cmpBin.operator == "==") &&
            cmpBin.left != null && cmpBin.right != null =>
            var found = 0
            var definedSide: AstNode | Null = null

            // Check left side for null
            if (cmpBin.left.nn.isInstanceOf[AstNull]) {
              found += 1
              nullCmp = cmpBin
              definedSide = cmpBin.right.nn
            }
            // Check right side for null
            if (cmpBin.right.nn.isInstanceOf[AstNull]) {
              found += 1
              nullCmp = cmpBin
              definedSide = cmpBin.left.nn
            }
            // Check left side for undefined
            if (isUndefined(cmpBin.left.nn, this)) {
              found += 1
              undefinedCmp = cmpBin
              definedSide = cmpBin.right.nn
            }
            // Check right side for undefined
            if (isUndefined(cmpBin.right.nn, this)) {
              found += 1
              undefinedCmp = cmpBin
              definedSide = cmpBin.left.nn
            }

            // Should find exactly one null or undefined
            if (found != 1) return false // @nowarn
            // The defined side should match check_subject
            if (definedSide == null || !AstEquivalent.equivalentTo(definedSide.nn, checkSubject)) return false // @nowarn
            true

          case _ => false
        }

        if (!findComparison(binary.left.nn)) return false // @nowarn
        if (!findComparison(binary.right.nn)) return false // @nowarn

        // We need both a null comparison and an undefined comparison
        nullCmp != null && undefinedCmp != null && (nullCmp.nn.asInstanceOf[AnyRef] ne undefinedCmp.nn.asInstanceOf[AnyRef])

      case _ => false
    }
  }

  /** Walk a node tree, calling visitor for each node. Used in multi-pass convergence check. */
  @scala.annotation.nowarn("msg=unused private member")
  private def walk(
    node:    AstNode,
    visitor: (AstNode, ArrayBuffer[AstNode]) => Any
  ): Boolean = {
    val parents = ArrayBuffer.empty[AstNode]
    var aborted = false
    val tw      = new TreeWalker((n, _) =>
      if (aborted) {
        true
      } else {
        visitor(n, parents) match {
          case WalkAbort =>
            aborted = true
            true
          case true => true
          case _    => null
        }
      }
    )
    node.walk(tw)
    aborted
  }
}

object Compressor {

  /** Create a Compressor with default options. */
  def apply(): Compressor = new Compressor(CompressorOptions())

  /** Create a Compressor with custom options. */
  def apply(options: CompressorOptions): Compressor = new Compressor(options)
}

/** Operator precedence table from Terser (lib/parse.js).
  *
  * Lower number = lower precedence (binds less tightly). Used by optimizeBinary to determine when swapping operands is safe (avoid breaking associativity).
  */
private[compress] object Precedence {
  private val table: Map[String, Int] = {
    val levels = Seq(
      Seq("||"),
      Seq("??"),
      Seq("&&"),
      Seq("|"),
      Seq("^"),
      Seq("&"),
      Seq("==", "===", "!=", "!=="),
      Seq("<", ">", "<=", ">=", "in", "instanceof"),
      Seq(">>", "<<", ">>>"),
      Seq("+", "-"),
      Seq("*", "/", "%"),
      Seq("**")
    )
    levels.zipWithIndex.flatMap { case (ops, i) => ops.map(_ -> (i + 1)) }.toMap
  }

  /** Get precedence for an operator (0 if not found). */
  def get(op: String): Int = table.getOrElse(op, 0)
}
