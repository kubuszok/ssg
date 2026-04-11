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
 *   Gap: 1067 LOC vs upstream 4129 LOC (~26%). Multi-pass convergence loop
 *     stubbed at lines 1021-1033 — TerserSuite compression tests are disabled
 *     because the loop hangs. Single-pass orchestration only. Pure-call elision
 *     (lines 107, 116) and global hoisting (line 286) gated on SymbolDef
 *     integration. See ISS-031, ISS-032. docs/architecture/terser-port.md.
 *   Audited: 2026-04-07 (major_issues)
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
      val current  = node
      val wasScope = current.isInstanceOf[AstScope]
      // Hoisting (hoist_props/hoist_vars options, default off) — deferred, see ISS-035

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
        // Can unwrap if parent is an if-block and content is extractable,
        // or if the content can be evicted from a block
        if (canBeEvictedFromBlock(stmt)) {
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

  /** Optimize a function expression — try to convert to arrow when safe. */
  private def optimizeFunction(self: AstFunction): AstNode = {
    val base = optimizeLambda(self)
    // unsafe_arrows: convert function(){} to ()=>{} when safe
    if (optionBool("unsafe_arrows") && options.ecma >= 2015) {
      base match {
        case fn: AstFunction if !fn.isGenerator && !fn.isAsync && fn.name == null && !fn.pinned =>
          // Check that function body doesn't use `this` or `arguments`
          var usesThis = false
          var usesArguments = false
          val tw = new TreeWalker((node, _) => {
            node match {
              case _: AstThis => usesThis = true; true
              case ref: AstSymbolRef if ref.name == "arguments" => usesArguments = true; true
              case _: AstScope => true // don't descend into nested scopes
              case _ => null
            }
          })
          fn.walk(tw)
          if (!usesThis && !usesArguments) {
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

  /** Optimize do-while loops. */
  private def optimizeDo(self: AstDo): AstNode =
    if (!optionBool("loops")) self
    else {
      // Evaluate condition
      if (self.condition != null) {
        val ev = Evaluate.evaluate(self.condition.nn, this)
        if (ev != null && (ev.asInstanceOf[AnyRef] ne self.condition.nn.asInstanceOf[AnyRef])) {
          ev match {
            case false | 0 | 0.0 | "" | null =>
              // Condition is always false — body runs exactly once
              // But only if body has no break/continue targeting this loop
              if (
                self.body != null
                && !Common.hasBreakOrContinue(self.asInstanceOf[AstNode & AstIterationStatement], null)
              ) {
                val block = new AstBlockStatement
                block.start = self.start
                block.end = self.end
                block.body = self.body.nn match {
                  case b: AstBlock => b.body
                  case s           => scala.collection.mutable.ArrayBuffer(s)
                }
                return block // @nowarn
              }
            case _ =>
              // Condition is always truthy — convert to for(;;) { body }
              val forNode = new AstFor
              forNode.start = self.start
              forNode.end = self.end
              forNode.body = self.body
              forNode.init = null
              forNode.step = null
              forNode.condition = null
              return forNode // @nowarn
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

    if (!optionBool("conditionals")) {
      self
    } else {
      // Evaluate condition for dead branch elimination
      if (self.condition != null) {
        val ev = Evaluate.evaluate(self.condition.nn, this)
        if (ev != null && (ev.asInstanceOf[AnyRef] ne self.condition.nn.asInstanceOf[AnyRef])) {
          ev match {
            case false | 0 | 0.0 | "" | null =>
              // Condition is always false — drop body, keep alternative
              val block = new AstBlockStatement
              block.start = self.start
              block.end = self.end
              block.body = ArrayBuffer.empty
              // Preserve condition side effects
              if (hasSideEffects(self.condition.nn, this)) {
                val ss = new AstSimpleStatement
                ss.body = self.condition
                ss.start = self.start
                ss.end = self.end
                block.body.addOne(ss)
              }
              if (self.alternative != null) block.body.addOne(self.alternative.nn)
              return optimizeBlockStatement(block) // @nowarn
            case _ =>
              // Condition is always true — drop alternative, keep body
              val block = new AstBlockStatement
              block.start = self.start
              block.end = self.end
              block.body = ArrayBuffer.empty
              if (hasSideEffects(self.condition.nn, this)) {
                val ss = new AstSimpleStatement
                ss.body = self.condition
                ss.start = self.start
                ss.end = self.end
                block.body.addOne(ss)
              }
              if (self.body != null) block.body.addOne(self.body.nn)
              return optimizeBlockStatement(block) // @nowarn
          }
        }
      }

      // Merge nested if: if(a) if(b) x → if(a&&b) x (when no else on outer)
      if (self.alternative == null && self.body != null) {
        self.body.nn match {
          case innerIf: AstIf if innerIf.alternative == null =>
            val merged = new AstBinary
            merged.operator = "&&"
            merged.left = self.condition.nn
            merged.right = innerIf.condition.nn
            merged.start = self.start
            merged.end = self.end
            self.condition = merged
            self.body = innerIf.body
          case _ =>
        }
      }

      // Empty body + non-empty alternative → negate condition, swap body/alt
      if (self.body != null && isEmpty(self.body) && self.alternative != null && !isEmpty(self.alternative)) {
        val neg = new AstUnaryPrefix
        neg.operator = "!"
        neg.expression = self.condition.nn
        neg.start = self.start
        neg.end = self.end
        self.condition = neg
        self.body = self.alternative
        self.alternative = null
      }

      // Empty body + empty alternative → just condition as statement
      if (self.body != null && isEmpty(self.body) && (self.alternative == null || isEmpty(self.alternative))) {
        val ss = new AstSimpleStatement
        ss.start = self.start
        ss.end = self.end
        ss.body = self.condition
        return optimizeSimpleStatement(ss) // @nowarn
      }

      // Both branches are return/throw of same type → merge into single return/throw with ternary
      if (self.body != null && self.alternative != null) {
        (self.body.nn, self.alternative.nn) match {
          case (ret1: AstReturn, ret2: AstReturn) =>
            val cond = new AstConditional
            cond.start = self.start
            cond.end = self.end
            cond.condition = self.condition.nn
            cond.consequent = if (ret1.value != null) ret1.value.nn else makeVoid0(ret1)
            cond.alternative = if (ret2.value != null) ret2.value.nn else makeVoid0(ret2)
            val ret = new AstReturn
            ret.start = self.start
            ret.end = self.end
            ret.value = cond
            return ret // @nowarn
          case (thr1: AstThrow, thr2: AstThrow) if thr1.value != null && thr2.value != null =>
            val cond = new AstConditional
            cond.start = self.start
            cond.end = self.end
            cond.condition = self.condition.nn
            cond.consequent = thr1.value.nn
            cond.alternative = thr2.value.nn
            val thr = new AstThrow
            thr.start = self.start
            thr.end = self.end
            thr.value = cond
            return thr // @nowarn
          case _ =>
        }
      }

      // Convert if(x) expr; to x && expr; when no alternative
      if (self.alternative == null) {
        self.body match {
          case ss: AstSimpleStatement =>
            val binary = new AstBinary
            binary.start = self.start
            binary.end = self.end
            binary.operator = "&&"
            binary.left = self.condition.nn
            binary.right = ss.body.nn

            val result = new AstSimpleStatement
            result.start = self.start
            result.end = self.end
            result.body = binary
            result
          case _ => self
        }
      } else {
        // Both branches are simple statements -> ternary
        (self.body, self.alternative.nn) match {
          case (thenSS: AstSimpleStatement, elseSS: AstSimpleStatement) =>
            val cond = new AstConditional
            cond.start = self.start
            cond.end = self.end
            cond.condition = self.condition.nn
            cond.consequent = thenSS.body.nn
            cond.alternative = elseSS.body.nn

            val result = new AstSimpleStatement
            result.start = self.start
            result.end = self.end
            result.body = cond
            result
          case _ => self
        }
      }
    }
  }

  /** Optimize conditional expressions (ternary operator). */
  private def optimizeConditional(self: AstConditional): AstNode =
    if (!optionBool("conditionals")) self
    else {
      // Lift sequences from condition
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
      if (self.condition != null) {
        val ev = Evaluate.evaluate(self.condition.nn, this)
        if (ev != null && (ev.asInstanceOf[AnyRef] ne self.condition.nn.asInstanceOf[AnyRef])) {
          ev match {
            case false | 0 | 0.0 | "" | null =>
              // Condition is falsy — return alternative, keep condition for side effects
              if (hasSideEffects(self.condition.nn, this)) {
                return makeSequence(self, ArrayBuffer(self.condition.nn, self.alternative.nn)) // @nowarn
              }
              return self.alternative.nn // @nowarn
            case _ =>
              // Condition is truthy — return consequent
              if (hasSideEffects(self.condition.nn, this)) {
                return makeSequence(self, ArrayBuffer(self.condition.nn, self.consequent.nn)) // @nowarn
              }
              return self.consequent.nn // @nowarn
          }
        }
      }

      // Boolean simplification: c ? true : false → !!c, c ? false : true → !c
      (self.consequent.nn, self.alternative.nn) match {
        case (_: AstTrue, _: AstFalse) =>
          // c ? true : false → !!c
          val inner = new AstUnaryPrefix
          inner.operator = "!"
          inner.expression = self.condition.nn
          inner.start = self.start
          inner.end = self.end
          val outer = new AstUnaryPrefix
          outer.operator = "!"
          outer.expression = inner
          outer.start = self.start
          outer.end = self.end
          return bestOfExpression(outer, self) // @nowarn

        case (_: AstFalse, _: AstTrue) =>
          // c ? false : true → !c
          val neg = new AstUnaryPrefix
          neg.operator = "!"
          neg.expression = self.condition.nn
          neg.start = self.start
          neg.end = self.end
          return bestOfExpression(neg, self) // @nowarn

        case _ =>
      }

      // c ? x : false → c && x
      self.alternative.nn match {
        case _: AstFalse if optionBool("booleans") =>
          val binary = new AstBinary
          binary.operator = "&&"
          binary.left = self.condition.nn
          binary.right = self.consequent.nn
          binary.start = self.start
          binary.end = self.end
          return bestOfExpression(binary, self) // @nowarn
        case _ =>
      }

      // c ? true : x → c || x
      self.consequent.nn match {
        case _: AstTrue if optionBool("booleans") =>
          val binary = new AstBinary
          binary.operator = "||"
          binary.left = self.condition.nn
          binary.right = self.alternative.nn
          binary.start = self.start
          binary.end = self.end
          return bestOfExpression(binary, self) // @nowarn
        case _ =>
      }

      // c ? false : x → !c && x
      (self.consequent.nn, self.alternative.nn) match {
        case (_: AstFalse, _) if optionBool("booleans") =>
          val neg = new AstUnaryPrefix
          neg.operator = "!"
          neg.expression = self.condition.nn
          neg.start = self.start
          neg.end = self.end
          val binary = new AstBinary
          binary.operator = "&&"
          binary.left = neg
          binary.right = self.alternative.nn
          binary.start = self.start
          binary.end = self.end
          return bestOfExpression(binary, self) // @nowarn
        case _ =>
      }

      // c ? x : true → !c || x
      (self.consequent.nn, self.alternative.nn) match {
        case (_, _: AstTrue) if optionBool("booleans") =>
          val neg = new AstUnaryPrefix
          neg.operator = "!"
          neg.expression = self.condition.nn
          neg.start = self.start
          neg.end = self.end
          val binary = new AstBinary
          binary.operator = "||"
          binary.left = neg
          binary.right = self.consequent.nn
          binary.start = self.start
          binary.end = self.end
          return bestOfExpression(binary, self) // @nowarn
        case _ =>
      }

      // x ? x : y → x || y (when condition equals consequent)
      if (AstEquivalent.equivalentTo(self.condition.nn, self.consequent.nn)) {
        val binary = new AstBinary
        binary.operator = "||"
        binary.left = self.condition.nn
        binary.right = self.alternative.nn
        binary.start = self.start
        binary.end = self.end
        return binary // @nowarn
      }

      // x ? y : y → (x, y) (when consequent equals alternative)
      if (AstEquivalent.equivalentTo(self.consequent.nn, self.alternative.nn)) {
        return makeSequence(self, ArrayBuffer(self.condition.nn, self.consequent.nn)) // @nowarn
      }

      self
    }

  /** Optimize switch statements — constant case elimination, branch merging. */
  private def optimizeSwitch(self: AstSwitch): AstNode =
    if (!optionBool("switches")) self
    else {
      // Remove empty trailing default case
      if (self.body.nonEmpty) {
        self.body.last match {
          case d: AstDefault if d.body.forall(isEmpty) =>
            self.body.remove(self.body.size - 1)
          case _ =>
        }
      }

      // Merge consecutive cases with empty bodies (fall-through)
      // e.g. case 1: case 2: x → keep as-is (correct fall-through)

      // Evaluate switch expression for constant dispatch
      if (self.expression != null && optionBool("dead_code")) {
        val ev = Evaluate.evaluate(self.expression.nn, this)
        if (ev != null && (ev.asInstanceOf[AnyRef] ne self.expression.nn.asInstanceOf[AnyRef])) {
          // Switch expression is constant — find matching case
          var matchIdx = -1
          var i = 0
          while (i < self.body.size) {
            self.body(i) match {
              case _: AstDefault => // track default for future use
              case cas: AstCase if cas.expression != null =>
                val caseEv = Evaluate.evaluate(cas.expression.nn, this)
                if (caseEv != null && (caseEv.asInstanceOf[AnyRef] ne cas.expression.nn.asInstanceOf[AnyRef])) {
                  // Both switch expr and case expr are constant — compare
                  if (ev == caseEv) matchIdx = i
                }
              case _ =>
            }
            i += 1
          }

          // If we found a constant match, extract that case's body
          if (matchIdx >= 0) {
            val matched = self.body(matchIdx)
            val block = new AstBlockStatement
            block.start = self.start
            block.end = self.end
            block.body = matched.asInstanceOf[AstSwitchBranch].body.clone()
            return optimizeBlockStatement(block) // @nowarn
          }
        }
      }

      // Single non-default case → convert to if-statement
      val nonDefaultCases = self.body.count(_.isInstanceOf[AstCase])
      if (nonDefaultCases == 1 && self.body.size <= 2) {
        val caseNode = self.body.find(_.isInstanceOf[AstCase])
        val defaultNode = self.body.find(_.isInstanceOf[AstDefault])
        caseNode match {
          case Some(cas: AstCase) if cas.expression != null =>
            val ifNode = new AstIf
            ifNode.start = self.start
            ifNode.end = self.end
            // condition: switch_expr === case_expr
            val cmp = new AstBinary
            cmp.operator = "==="
            cmp.left = self.expression.nn
            cmp.right = cas.expression.nn
            cmp.start = self.start
            cmp.end = self.end
            ifNode.condition = cmp
            val thenBlock = new AstBlockStatement
            thenBlock.body = cas.body.clone()
            thenBlock.start = cas.start
            thenBlock.end = cas.end
            ifNode.body = thenBlock
            defaultNode match {
              case Some(d: AstDefault) if d.body.nonEmpty =>
                val elseBlock = new AstBlockStatement
                elseBlock.body = d.body.clone()
                elseBlock.start = d.start
                elseBlock.end = d.end
                ifNode.alternative = elseBlock
              case _ =>
                ifNode.alternative = null
            }
            return optimizeIf(ifNode) // @nowarn
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
  private def optimizeCall(self: AstCall): AstNode =
    if (self.expression == null) {
      self
    } else {
      val exp = self.expression.nn
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
              while (false) {} // no-op to match original's continue
            }
          } else {
            self.args(pos) = self.args(i)
            pos += 1
          }
          last = pos
          i += 1
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

      // Try to inline the call (empty body, identity function, etc.)
      val inlined = Inline.inlineIntoCall(self, this)
      if (!(inlined eq self)) return inlined // @nowarn

      // Unsafe built-in constructor simplifications
      if (optionBool("unsafe")) {
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
                } else if (self.args.size == 1) {
                  val bin = new AstBinary
                  bin.operator = "+"
                  bin.left = self.args(0)
                  bin.right = { val s = new AstString; s.value = ""; s.start = self.start; s.end = self.end; s }
                  bin.start = self.start
                  bin.end = self.end
                  return bin // @nowarn
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
                  return prefix // @nowarn
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
                  return outer // @nowarn
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
              case _ =>
            }

          case _ =>
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

      self
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

    // Commutative operator: move constant to left
    val commutativeOps = Set("==", "===", "!=", "!==", "*", "&", "|", "^")
    if (optionBool("lhs_constants") && commutativeOps.contains(self.operator)) {
      if (self.right != null && self.left != null) {
        val rightConst = self.right.nn.isInstanceOf[AstConstant]
        val leftConst  = self.left.nn.isInstanceOf[AstConstant]
        if (rightConst && !leftConst) {
          // Swap left and right
          val tmp = self.left
          self.left = self.right
          self.right = tmp
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
        case (tmpl: AstTemplateString, _) =>
          val rr = Evaluate.evaluate(self.right.nn, this)
          if (rr != null && (rr.asInstanceOf[AnyRef] ne self.right.nn.asInstanceOf[AnyRef]) && rr.isInstanceOf[String]) {
            if (tmpl.segments.nonEmpty) {
              tmpl.segments.last match {
                case seg: AstTemplateSegment =>
                  seg.value = seg.value + rr.asInstanceOf[String]
                  return tmpl // @nowarn
                case _ =>
              }
            }
          }
        case (_, tmpl: AstTemplateString) =>
          val ll = Evaluate.evaluate(self.left.nn, this)
          if (ll != null && (ll.asInstanceOf[AnyRef] ne self.left.nn.asInstanceOf[AnyRef]) && ll.isInstanceOf[String]) {
            if (tmpl.segments.nonEmpty) {
              tmpl.segments(0) match {
                case seg: AstTemplateSegment =>
                  seg.value = ll.asInstanceOf[String] + seg.value
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
                if (inBooleanContext()) {
                  return self.left.nn // @nowarn
                }
            }
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
                return self.left.nn // @nowarn
            }
          }
          val rr = Evaluate.evaluate(self.right.nn, this)
          if (rr != null && (rr.asInstanceOf[AnyRef] ne self.right.nn.asInstanceOf[AnyRef])) {
            rr match {
              case false | 0 | 0.0 | "" | null =>
                // Right is falsy in || → result is left
                if (inBooleanContext()) {
                  return self.left.nn // @nowarn
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
    if (optionBool("evaluate") && self.left != null && self.right != null) {
      val assocOps = Set("+", "*", "&", "|", "^")
      if (assocOps.contains(self.operator)) {
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

  /** Optimize assignment expressions. */
  private def optimizeAssign(self: AstAssign): AstNode = {
    if (self.logical) {
      return self // @nowarn
    }

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
        val d = leftRef.definition()
        if (d == null || !d.nn.undeclared) return self.right.nn // @nowarn — safe self-assignment removal
      }
    }

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

  /** Optimize property dot access — evaluate property reads on literals. */
  private def optimizeDot(self: AstDot): AstNode = {
    // Don't optimize LHS
    if (isLhs() != null) return self // @nowarn

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

  /** Optimize computed property access — convert to dot when key is a valid identifier. */
  private def optimizeSub(self: AstSub): AstNode = {
    if (optionBool("properties") && self.property != null) {
      // Evaluate the key
      val propNode: AstNode | Null = self.property match {
        case n: AstNode => n
        case _          => null
      }
      val keyEv = if (propNode != null) Evaluate.evaluate(propNode.nn, this) else null
      val keyStr: String | Null = keyEv match {
        case s: String => s
        case d: Double if d == d.toInt.toDouble => d.toInt.toString
        case _ => null
      }

      if (keyStr != null) {
        val ks = keyStr.nn
        // Convert to dot notation if key is a valid identifier
        if (isValidIdentifier(ks) && !isReservedWord(ks)) {
          val dot = new AstDot
          dot.start = self.start
          dot.end = self.end
          dot.expression = self.expression
          dot.optional = self.optional
          dot.property = ks
          return optimizeDot(dot) // @nowarn
        }
        // Try flatten_object with the evaluated key
        val flat = flattenObject(self, ks)
        if (flat != null) return flat.nn // @nowarn
      }

      // If key wasn't evaluated, try string key directly
      propNode match {
        case str: AstString =>
          val flat = flattenObject(self, str.value)
          if (flat != null) return flat.nn // @nowarn
        case _ =>
      }
    }

    // Evaluate
    if (optionBool("evaluate") && self.property != null) {
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
    // If the chain's expression is a PropAccess or Call, pass through to let
    // the inner optimization handle it
    self.expression.nn match {
      case pa: AstPropAccess if pa.optional && pa.expression != null && !isNullish(pa.expression.nn, this) =>
        // Receiver is non-nullish — optional chain is unnecessary, unwrap
        pa.optional = false
        return pa // @nowarn
      case call: AstCall if call.optional && call.expression != null && !isNullish(call.expression.nn, this) =>
        call.optional = false
        return call // @nowarn
      case _ =>
    }
    self
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

  /** Optimize `undefined` -> `void 0`. */
  private def optimizeUndefined(self: AstUndefined): AstNode = {
    // Don't optimize LHS (assignment targets)
    if (isLhs() != null) return self // @nowarn
    makeVoid0(self)
  }

  /** Optimize `Infinity` -> `1/0` (unless keep_infinity). */
  private def optimizeInfinity(self: AstInfinity): AstNode = {
    if (isLhs() != null) return self // @nowarn — don't optimize LHS
    if (optionBool("keep_infinity")) {
      self
    } else {
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
  }

  /** Optimize `NaN` -> `0/0`. */
  private def optimizeNaN(self: AstNaN): AstNode = {
    if (isLhs() != null) return self // @nowarn — don't optimize LHS
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
    div
  }

  /** Optimize boolean literals in boolean context. */
  private def optimizeBoolean(self: AstBoolean): AstNode =
    if (inBooleanContext()) {
      val num = new AstNumber
      num.start = self.start
      num.end = self.end
      num.value = if (self.isInstanceOf[AstTrue]) 1.0 else 0.0
      num
    } else if (optionBool("booleans")) {
      // true -> !0, false -> !1
      val inner = new AstNumber
      inner.start = self.start
      inner.end = self.end
      inner.value = if (self.isInstanceOf[AstTrue]) 0.0 else 1.0

      val not = new AstUnaryPrefix
      not.start = self.start
      not.end = self.end
      not.operator = "!"
      not.expression = inner
      not
    } else {
      self
    }

  /** Optimize default assignment — remove `= undefined`. */
  private def optimizeDefaultAssign(self: AstDefaultAssign): AstNode =
    if (!optionBool("evaluate")) self
    else {
      // If the default value is undefined, the assignment is redundant
      if (self.right != null && isUndefined(self.right.nn, this)) {
        return self.left.nn // @nowarn — drop `= undefined`
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

  /** Optimize object key-value: lift_key + function→concise method. */
  private def optimizeObjectKeyVal(self: AstObjectKeyVal): AstNode = {
    liftKey(self)
    self
  }

  /** Optimize destructuring: prune unused properties (pure_getters + unused). */
  private def optimizeDestructuring(self: AstDestructuring): AstNode = {
    if (
      optionBool("pure_getters")
      && optionBool("unused")
      && !self.isArray
      && self.names.nonEmpty
      && !self.names.last.isInstanceOf[AstExpansion]
    ) {
      // Check this isn't inside an export declaration
      val isExportDecl = {
        var a = 0
        var p = 0
        val ancestors = Array("VarDef", "Var", "Export") // simplified pattern
        var matched = true
        while (a < ancestors.length && matched) {
          val par = parent(p)
          if (par == null) { matched = false }
          else if (a == 0 && par.nn.nodeType == "Destructuring") { p += 1 }
          else if (par.nn.nodeType != ancestors(a) && !par.nn.nodeType.matches("Const|Let|Var")) {
            matched = false
          } else { a += 1; p += 1 }
        }
        matched && a == ancestors.length
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

  /** Optimize unary prefix expressions: !, void, typeof, -, +, ~, delete. */
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

    // void 0 shortcut → return as-is (already minimal)
    if (self.operator == "void" && e.isInstanceOf[AstNumber] && e.asInstanceOf[AstNumber].value == 0.0) {
      return self // @nowarn
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
      // ~~x in 32-bit context → x
      if (
        self.operator == "~"
        && e.isInstanceOf[AstUnaryPrefix]
        && e.asInstanceOf[AstUnaryPrefix].operator == "~"
        && in32BitContext()
      ) {
        return e.asInstanceOf[AstUnaryPrefix].expression.nn // @nowarn
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

  /** Optimize sequence expressions: remove side-effect-free prefix, trim trailing undefined. */
  private def optimizeSequence(self: AstSequence): AstNode = {
    if (self.expressions.isEmpty) return self // @nowarn

    // Filter out side-effect-free expressions (keep last always)
    if (optionBool("side_effects")) {
      val exprs = ArrayBuffer.empty[AstNode]
      var i = 0
      val last = self.expressions.size - 1
      while (i < last) {
        val e = self.expressions(i)
        val dropped = DropSideEffectFree.dropSideEffectFree(e, this)
        if (dropped != null) exprs.addOne(dropped.nn)
        i += 1
      }
      exprs.addOne(self.expressions(last))
      if (exprs.size < self.expressions.size) {
        self.expressions = exprs
      }
    }

    // Flatten nested sequences
    val flat = ArrayBuffer.empty[AstNode]
    for (e <- self.expressions) {
      e match {
        case seq: AstSequence => flat.addAll(seq.expressions)
        case _                => flat.addOne(e)
      }
    }
    self.expressions = flat

    // Single expression → unwrap
    if (self.expressions.size == 1) return self.expressions(0) // @nowarn
    // Empty → void 0
    if (self.expressions.isEmpty) return makeVoid0(self) // @nowarn

    self
  }

  /** Optimize `new` expressions: unsafe constructor replacements. */
  private def optimizeNew(self: AstNew): AstNode = {
    if (!optionBool("unsafe") || self.expression == null) return self // @nowarn
    self.expression.nn match {
      case ref: AstSymbolRef if isUndeclaredRef(ref) =>
        ref.name match {
          case "Object" if self.args.isEmpty =>
            // new Object() → {}
            val obj = new AstObject
            obj.properties = ArrayBuffer.empty
            obj.start = self.start
            obj.end = self.end
            return obj // @nowarn
          case "RegExp" =>
            // Could optimize but complex — skip for now
          case _ =>
        }
      case _ =>
    }
    // Fall through to call optimization
    optimizeCall(self)
  }

  /** Optimize template strings: fold constant segments, unwrap single-segment. */
  private def optimizeTemplateString(self: AstTemplateString): AstNode = {
    if (self.segments.isEmpty) return self // @nowarn

    // Fold adjacent constant string segments
    val folded = ArrayBuffer.empty[AstNode]
    for (seg <- self.segments) {
      seg match {
        case ts: AstTemplateSegment =>
          // Check if previous is also a template segment — merge
          if (folded.nonEmpty) {
            folded.last match {
              case prev: AstTemplateSegment =>
                prev.value = prev.value + ts.value
              case _ =>
                folded.addOne(ts)
            }
          } else {
            folded.addOne(ts)
          }
        case expr =>
          // Check if expression evaluates to a string constant
          if (optionBool("evaluate")) {
            val ev = Evaluate.evaluate(expr, this)
            ev match {
              case s: String =>
                val tsSeg = new AstTemplateSegment
                tsSeg.value = s
                tsSeg.start = expr.start
                tsSeg.end = expr.end
                // Merge with previous segment if possible
                if (folded.nonEmpty) {
                  folded.last match {
                    case prev: AstTemplateSegment =>
                      prev.value = prev.value + s
                    case _ =>
                      folded.addOne(tsSeg)
                  }
                } else {
                  folded.addOne(tsSeg)
                }
              case _ =>
                folded.addOne(expr)
            }
          } else {
            folded.addOne(expr)
          }
      }
    }
    self.segments = folded

    // Single constant segment → string literal
    if (self.segments.size == 1) {
      self.segments(0) match {
        case ts: AstTemplateSegment =>
          val str = new AstString
          str.value = ts.value
          str.start = self.start
          str.end = self.end
          return str // @nowarn
        case _ =>
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

  /** Check if an expression is a nullish check (== null or equivalent). */
  @annotation.nowarn("msg=unused private member") // used by optimizeConditional expansion (Batch 4)
  private def isNullishCheck(check: AstNode, checkSubject: AstNode): Boolean = {
    check match {
      case binary: AstBinary if binary.operator == "==" && binary.left != null && binary.right != null =>
        val leftNullish = isNullish(binary.left.nn, this)
        val rightNullish = isNullish(binary.right.nn, this)
        if (leftNullish) {
          // check.right should be equivalent to checkSubject
          AstEquivalent.equivalentTo(binary.right.nn, checkSubject)
        } else if (rightNullish) {
          AstEquivalent.equivalentTo(binary.left.nn, checkSubject)
        } else {
          false
        }
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
