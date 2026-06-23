/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

import java.lang.foreign.*
import java.lang.invoke.MethodHandle

object TreeSitterPlatformImpl extends TreeSitterPlatform {

  // ── Struct layouts ──────────────────────────────────────────────────

  private val TS_NODE: StructLayout = MemoryLayout.structLayout(
    MemoryLayout.sequenceLayout(4, ValueLayout.JAVA_INT).withName("context"),
    ValueLayout.ADDRESS.withName("id"),
    ValueLayout.ADDRESS.withName("tree")
  )

  private val TS_QUERY_MATCH: StructLayout = MemoryLayout.structLayout(
    ValueLayout.JAVA_INT.withName("id"),
    ValueLayout.JAVA_SHORT.withName("pattern_index"),
    ValueLayout.JAVA_SHORT.withName("capture_count"),
    ValueLayout.ADDRESS.withName("captures")
  )

  private val TS_QUERY_CAPTURE: StructLayout = MemoryLayout.structLayout(
    TS_NODE.withName("node"),
    ValueLayout.JAVA_INT.withName("index"),
    MemoryLayout.paddingLayout(4)
  )

  // ── Library loading via multiarch-core NativeLibLoader ───────────────

  private lazy val nativeLinker: Linker       = Linker.nativeLinker()
  private lazy val libLookup:    SymbolLookup = loadLibrary("tree_sitter_all", multiarch.core.NativeLibLoader.load)

  /** Loads a native library and returns its symbol lookup, wrapping any failure in an `IllegalStateException` that names the library and the host platform so load errors are immediately actionable
    * instead of surfacing as a buried `ExceptionInInitializerError`.
    */
  private[highlight] def loadLibrary(libName: String, loader: String => java.nio.file.Path): SymbolLookup =
    try
      SymbolLookup.libraryLookup(loader(libName), Arena.global())
    catch {
      case cause: Throwable =>
        throw new IllegalStateException(
          s"Failed to load the '$libName' native library (host: ${System.getProperty("os.arch")}/${System.getProperty("os.name")}). " +
            "Ensure the tree_sitter_all native library is on java.library.path or bundled as a classpath resource.",
          cause
        )
    }

  private def sym(name: String): MemorySegment =
    libLookup.find(name).orElseThrow(() => new UnsupportedOperationException(s"Symbol not found: $name"))

  private val handleCache = new java.util.concurrent.ConcurrentHashMap[String, MethodHandle]()

  private def bindCached(name: String, desc: FunctionDescriptor): MethodHandle =
    handleCache.computeIfAbsent(name, _ => nativeLinker.downcallHandle(sym(name), desc))

  // ── Lazy function accessors ─────────────────────────────────────────

  private def parserNew(): MemorySegment =
    bindCached("ts_parser_new", FunctionDescriptor.of(ValueLayout.ADDRESS)).invoke().asInstanceOf[MemorySegment]

  private def parserDelete(p: MemorySegment): Unit =
    bindCached("ts_parser_delete", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)).invoke(p)

  private def parserSetLanguage(p: MemorySegment, lang: MemorySegment): Unit =
    bindCached("ts_parser_set_language", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS)).invoke(p, lang)

  private def parserParseString(p: MemorySegment, old: MemorySegment, src: MemorySegment, len: Int): MemorySegment =
    bindCached(
      "ts_parser_parse_string",
      FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
    ).invoke(p, old, src, len).asInstanceOf[MemorySegment]

  private def treeRootNode(tree: MemorySegment, arena: Arena): MemorySegment =
    bindCached("ts_tree_root_node", FunctionDescriptor.of(TS_NODE, ValueLayout.ADDRESS)).invoke(arena, tree).asInstanceOf[MemorySegment]

  private def treeDelete(tree: MemorySegment): Unit =
    bindCached("ts_tree_delete", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)).invoke(tree)

  private def queryNew(lang: MemorySegment, src: MemorySegment, len: Int, errOff: MemorySegment, errType: MemorySegment): MemorySegment =
    bindCached(
      "ts_query_new",
      FunctionDescriptor.of(
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS
      )
    ).invoke(lang, src, len, errOff, errType).asInstanceOf[MemorySegment]

  private def queryDelete(q: MemorySegment): Unit =
    bindCached("ts_query_delete", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)).invoke(q)

  private def queryCaptureCount(q: MemorySegment): Int =
    bindCached("ts_query_capture_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)).invoke(q).asInstanceOf[Int]

  private def queryCaptureNameForId(q: MemorySegment, id: Int, lenPtr: MemorySegment): MemorySegment =
    bindCached(
      "ts_query_capture_name_for_id",
      FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
    ).invoke(q, id, lenPtr).asInstanceOf[MemorySegment]

  private def queryCursorNew(): MemorySegment =
    bindCached("ts_query_cursor_new", FunctionDescriptor.of(ValueLayout.ADDRESS)).invoke().asInstanceOf[MemorySegment]

  private def queryCursorDelete(c: MemorySegment): Unit =
    bindCached("ts_query_cursor_delete", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)).invoke(c)

  private def queryCursorExec(c: MemorySegment, q: MemorySegment, node: MemorySegment): Unit =
    bindCached("ts_query_cursor_exec", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, TS_NODE)).invoke(c, q, node)

  private def queryCursorNextMatch(c: MemorySegment, m: MemorySegment): Boolean =
    bindCached("ts_query_cursor_next_match", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS)).invoke(c, m).asInstanceOf[Boolean]

  private def nodeStartByte(node: MemorySegment): Int =
    bindCached("ts_node_start_byte", FunctionDescriptor.of(ValueLayout.JAVA_INT, TS_NODE)).invoke(node).asInstanceOf[Int]

  private def nodeEndByte(node: MemorySegment): Int =
    bindCached("ts_node_end_byte", FunctionDescriptor.of(ValueLayout.JAVA_INT, TS_NODE)).invoke(node).asInstanceOf[Int]

  // ── Grammar lookup (lazy) ───────────────────────────────────────────

  private lazy val grammarNames: Seq[String] = {
    val hCount = bindCached("ts_natives_grammar_count", FunctionDescriptor.of(ValueLayout.JAVA_INT))
    val hNames = bindCached("ts_natives_grammar_names", FunctionDescriptor.of(ValueLayout.ADDRESS))
    val count  = hCount.invoke().asInstanceOf[Int]
    val raw    = hNames.invoke().asInstanceOf[MemorySegment]
    val ptr    = raw.reinterpret(count.toLong * ValueLayout.ADDRESS.byteSize() + ValueLayout.ADDRESS.byteSize())
    val buf    = scala.collection.mutable.ArrayBuffer.empty[String]
    var i      = 0
    while (i < count) {
      val strPtr = ptr.getAtIndex(ValueLayout.ADDRESS, i.toLong).reinterpret(256)
      buf += strPtr.getString(0)
      i += 1
    }
    buf.toSeq
  }

  private def grammarLanguagePtr(name: String): MemorySegment =
    bindCached(s"tree_sitter_$name", FunctionDescriptor.of(ValueLayout.ADDRESS)).invoke().asInstanceOf[MemorySegment]

  // ── TreeSitterPlatform implementation ───────────────────────────────

  override def availableGrammars: Seq[String] = grammarNames

  override def highlight(source: String, grammarName: String, highlightQuery: String): Seq[HighlightSpan] =
    if (!grammarNames.contains(grammarName)) Seq.empty
    else {
      val arena = Arena.ofConfined()
      try
        doHighlight(grammarName, source, highlightQuery, arena)
      finally
        arena.close()
    }

  private def doHighlight(grammarName: String, source: String, highlightQuery: String, arena: Arena): Seq[HighlightSpan] = {
    val languagePtr = grammarLanguagePtr(grammarName)
    val p           = parserNew()
    try {
      parserSetLanguage(p, languagePtr)
      val srcBytes = source.getBytes("UTF-8")
      val srcNat   = arena.allocate(srcBytes.length.toLong + 1)
      MemorySegment.copy(srcBytes, 0, srcNat, ValueLayout.JAVA_BYTE, 0, srcBytes.length)
      srcNat.set(ValueLayout.JAVA_BYTE, srcBytes.length.toLong, 0.toByte)

      val tree = parserParseString(p, MemorySegment.NULL, srcNat, srcBytes.length)
      if (tree.address() == 0) Seq.empty
      else {
        try
          runQuery(languagePtr, tree, highlightQuery, arena)
        finally
          treeDelete(tree)
      }
    } finally
      parserDelete(p)
  }

  private def runQuery(lang: MemorySegment, tree: MemorySegment, queryStr: String, arena: Arena): Seq[HighlightSpan] = {
    val qBytes = queryStr.getBytes("UTF-8")
    val qNat   = arena.allocate(qBytes.length.toLong + 1)
    MemorySegment.copy(qBytes, 0, qNat, ValueLayout.JAVA_BYTE, 0, qBytes.length)
    qNat.set(ValueLayout.JAVA_BYTE, qBytes.length.toLong, 0.toByte)

    val errOff  = arena.allocate(ValueLayout.JAVA_INT)
    val errType = arena.allocate(ValueLayout.JAVA_INT)
    val q       = queryNew(lang, qNat, qBytes.length, errOff, errType)
    if (q.address() == 0) Seq.empty
    else {
      try {
        val names = loadCaptureNames(q)
        val root  = treeRootNode(tree, arena)
        executeQuery(q, root, names, arena)
      } finally
        queryDelete(q)
    }
  }

  private def loadCaptureNames(q: MemorySegment): Array[String] = {
    val count = queryCaptureCount(q)
    val names = new Array[String](count)
    val a     = Arena.ofConfined()
    try {
      val lenPtr = a.allocate(ValueLayout.JAVA_INT)
      var i      = 0
      while (i < count) {
        val namePtr = queryCaptureNameForId(q, i, lenPtr)
        val len     = lenPtr.get(ValueLayout.JAVA_INT, 0)
        names(i) = namePtr.reinterpret(len.toLong + 1).getString(0)
        i += 1
      }
    } finally
      a.close()
    names
  }

  private def executeQuery(q: MemorySegment, rootNode: MemorySegment, captureNames: Array[String], arena: Arena): Seq[HighlightSpan] = {
    val cursor = queryCursorNew()
    try {
      queryCursorExec(cursor, q, rootNode)
      val matchBuf = arena.allocate(TS_QUERY_MATCH)
      val spans    = scala.collection.mutable.ArrayBuffer.empty[HighlightSpan]

      while (queryCursorNextMatch(cursor, matchBuf)) {
        val capCount = java.lang.Short.toUnsignedInt(matchBuf.get(ValueLayout.JAVA_SHORT, 6))
        val capsPtr  = matchBuf.get(ValueLayout.ADDRESS, 8).reinterpret(capCount.toLong * TS_QUERY_CAPTURE.byteSize())
        var c        = 0
        while (c < capCount) {
          val base    = capsPtr.asSlice(c.toLong * TS_QUERY_CAPTURE.byteSize(), TS_QUERY_CAPTURE.byteSize())
          val nodeSeg = base.asSlice(0, TS_NODE.byteSize())
          val capIdx  = base.get(ValueLayout.JAVA_INT, TS_NODE.byteSize())
          val startB  = nodeStartByte(nodeSeg)
          val endB    = nodeEndByte(nodeSeg)
          if (capIdx >= 0 && capIdx < captureNames.length) {
            spans += HighlightSpan(startB, endB, captureNames(capIdx))
          }
          c += 1
        }
      }
      spans.toSeq
    } finally
      queryCursorDelete(cursor)
  }
}
