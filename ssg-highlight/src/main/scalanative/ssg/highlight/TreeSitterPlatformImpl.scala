/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

object TreeSitterPlatformImpl extends TreeSitterPlatform {

  @extern
  @link("tree_sitter_all")
  private object Extern {
    def ts_parser_new():                                                                                                                                            Ptr[Byte]    = extern
    def ts_parser_delete(parser:            Ptr[Byte]):                                                                                                             Unit         = extern
    def ts_parser_set_language(parser:      Ptr[Byte], language: Ptr[Byte]):                                                                                        CBool        = extern
    def ts_parser_parse_string(parser:      Ptr[Byte], oldTree:  Ptr[Byte], string:    CString, length:           CUnsignedInt):                                    Ptr[Byte]    = extern
    def ts_tree_root_node_p(tree:           Ptr[Byte], out:      Ptr[Byte]):                                                                                        Unit         = extern
    def ts_tree_delete(tree:                Ptr[Byte]):                                                                                                             Unit         = extern
    def ts_query_new(language:              Ptr[Byte], source:   CString, sourceLen:   CUnsignedInt, errorOffset: Ptr[CUnsignedInt], errorType: Ptr[CUnsignedInt]): Ptr[Byte]    = extern
    def ts_query_delete(query:              Ptr[Byte]):                                                                                                             Unit         = extern
    def ts_query_capture_count(query:       Ptr[Byte]):                                                                                                             CUnsignedInt = extern
    def ts_query_capture_name_for_id(query: Ptr[Byte], id:       CUnsignedInt, length: Ptr[CUnsignedInt]):                                                          CString      = extern
    def ts_query_cursor_new():                                                                                                                                      Ptr[Byte]    = extern
    def ts_query_cursor_delete(cursor:      Ptr[Byte]):                                                                                                             Unit         = extern
    def ts_query_cursor_exec_p(cursor:      Ptr[Byte], query:    Ptr[Byte], node:      Ptr[Byte]):                                                                  Unit         = extern
    def ts_query_cursor_next_match(cursor:  Ptr[Byte], matchOut: Ptr[Byte]):                                                                                        CBool        = extern
    def ts_node_start_byte_p(node:          Ptr[Byte]):                                                                                                             CUnsignedInt = extern
    def ts_node_end_byte_p(node:            Ptr[Byte]):                                                                                                             CUnsignedInt = extern
    def ts_natives_grammar_count():                                                                                                                                 CInt         = extern
    def ts_natives_grammar_names():                                                                                                                                 Ptr[CString] = extern

    def tree_sitter_bash():              Ptr[Byte] = extern
    def tree_sitter_c():                 Ptr[Byte] = extern
    def tree_sitter_cpp():               Ptr[Byte] = extern
    def tree_sitter_c_sharp():           Ptr[Byte] = extern
    def tree_sitter_css():               Ptr[Byte] = extern
    def tree_sitter_go():                Ptr[Byte] = extern
    def tree_sitter_html():              Ptr[Byte] = extern
    def tree_sitter_java():              Ptr[Byte] = extern
    def tree_sitter_javascript():        Ptr[Byte] = extern
    def tree_sitter_json():              Ptr[Byte] = extern
    def tree_sitter_markdown():          Ptr[Byte] = extern
    def tree_sitter_python():            Ptr[Byte] = extern
    def tree_sitter_regex():             Ptr[Byte] = extern
    def tree_sitter_ruby():              Ptr[Byte] = extern
    def tree_sitter_rust():              Ptr[Byte] = extern
    def tree_sitter_scala():             Ptr[Byte] = extern
    def tree_sitter_sql():               Ptr[Byte] = extern
    def tree_sitter_toml():              Ptr[Byte] = extern
    def tree_sitter_typescript():        Ptr[Byte] = extern
    def tree_sitter_tsx():               Ptr[Byte] = extern
    def tree_sitter_yaml():              Ptr[Byte] = extern
    def tree_sitter_cmake():             Ptr[Byte] = extern
    def tree_sitter_dockerfile():        Ptr[Byte] = extern
    def tree_sitter_dtd():               Ptr[Byte] = extern
    def tree_sitter_elixir():            Ptr[Byte] = extern
    def tree_sitter_erlang():            Ptr[Byte] = extern
    def tree_sitter_haskell():           Ptr[Byte] = extern
    def tree_sitter_julia():             Ptr[Byte] = extern
    def tree_sitter_kotlin():            Ptr[Byte] = extern
    def tree_sitter_lua():               Ptr[Byte] = extern
    def tree_sitter_make():              Ptr[Byte] = extern
    def tree_sitter_ocaml():             Ptr[Byte] = extern
    def tree_sitter_ocaml_interface():   Ptr[Byte] = extern
    def tree_sitter_php():               Ptr[Byte] = extern
    def tree_sitter_php_only():          Ptr[Byte] = extern
    def tree_sitter_r():                 Ptr[Byte] = extern
    def tree_sitter_swift():             Ptr[Byte] = extern
    def tree_sitter_vim():               Ptr[Byte] = extern
    def tree_sitter_xml():               Ptr[Byte] = extern
    def tree_sitter_zig():               Ptr[Byte] = extern
    def tree_sitter_agda():              Ptr[Byte] = extern
    def tree_sitter_arduino():           Ptr[Byte] = extern
    def tree_sitter_bicep():             Ptr[Byte] = extern
    def tree_sitter_cairo():             Ptr[Byte] = extern
    def tree_sitter_commonlisp():        Ptr[Byte] = extern
    def tree_sitter_cpon():              Ptr[Byte] = extern
    def tree_sitter_cuda():              Ptr[Byte] = extern
    def tree_sitter_diff():              Ptr[Byte] = extern
    def tree_sitter_embedded_template(): Ptr[Byte] = extern
    def tree_sitter_func():              Ptr[Byte] = extern
    def tree_sitter_gitattributes():     Ptr[Byte] = extern
    def tree_sitter_glsl():              Ptr[Byte] = extern
    def tree_sitter_gosum():             Ptr[Byte] = extern
    def tree_sitter_hare():              Ptr[Byte] = extern
    def tree_sitter_hcl():               Ptr[Byte] = extern
    def tree_sitter_hlsl():              Ptr[Byte] = extern
    def tree_sitter_jsdoc():             Ptr[Byte] = extern
    def tree_sitter_kconfig():           Ptr[Byte] = extern
    def tree_sitter_kdl():               Ptr[Byte] = extern
    def tree_sitter_luadoc():            Ptr[Byte] = extern
    def tree_sitter_luap():              Ptr[Byte] = extern
    def tree_sitter_luau():              Ptr[Byte] = extern
    def tree_sitter_objc():              Ptr[Byte] = extern
    def tree_sitter_odin():              Ptr[Byte] = extern
    def tree_sitter_po():                Ptr[Byte] = extern
    def tree_sitter_pony():              Ptr[Byte] = extern
    def tree_sitter_printf():            Ptr[Byte] = extern
    def tree_sitter_properties():        Ptr[Byte] = extern
    def tree_sitter_puppet():            Ptr[Byte] = extern
    def tree_sitter_ql():                Ptr[Byte] = extern
    def tree_sitter_qmldir():            Ptr[Byte] = extern
    def tree_sitter_query():             Ptr[Byte] = extern
    def tree_sitter_requirements():      Ptr[Byte] = extern
    def tree_sitter_ron():               Ptr[Byte] = extern
    def tree_sitter_scss():              Ptr[Byte] = extern
    def tree_sitter_squirrel():          Ptr[Byte] = extern
    def tree_sitter_starlark():          Ptr[Byte] = extern
    def tree_sitter_svelte():            Ptr[Byte] = extern
    def tree_sitter_test():              Ptr[Byte] = extern
    def tree_sitter_ungrammar():         Ptr[Byte] = extern
    def tree_sitter_verilog():           Ptr[Byte] = extern
    def tree_sitter_vue():               Ptr[Byte] = extern
    def tree_sitter_wgsl_bevy():         Ptr[Byte] = extern
    def tree_sitter_yuck():              Ptr[Byte] = extern
  }

  private val grammarLookup: Map[String, () => Ptr[Byte]] = Map(
    "bash" -> (() => Extern.tree_sitter_bash()),
    "c" -> (() => Extern.tree_sitter_c()),
    "cpp" -> (() => Extern.tree_sitter_cpp()),
    "c_sharp" -> (() => Extern.tree_sitter_c_sharp()),
    "css" -> (() => Extern.tree_sitter_css()),
    "go" -> (() => Extern.tree_sitter_go()),
    "html" -> (() => Extern.tree_sitter_html()),
    "java" -> (() => Extern.tree_sitter_java()),
    "javascript" -> (() => Extern.tree_sitter_javascript()),
    "json" -> (() => Extern.tree_sitter_json()),
    "markdown" -> (() => Extern.tree_sitter_markdown()),
    "python" -> (() => Extern.tree_sitter_python()),
    "regex" -> (() => Extern.tree_sitter_regex()),
    "ruby" -> (() => Extern.tree_sitter_ruby()),
    "rust" -> (() => Extern.tree_sitter_rust()),
    "scala" -> (() => Extern.tree_sitter_scala()),
    "sql" -> (() => Extern.tree_sitter_sql()),
    "toml" -> (() => Extern.tree_sitter_toml()),
    "typescript" -> (() => Extern.tree_sitter_typescript()),
    "tsx" -> (() => Extern.tree_sitter_tsx()),
    "yaml" -> (() => Extern.tree_sitter_yaml()),
    "cmake" -> (() => Extern.tree_sitter_cmake()),
    "dockerfile" -> (() => Extern.tree_sitter_dockerfile()),
    "dtd" -> (() => Extern.tree_sitter_dtd()),
    "elixir" -> (() => Extern.tree_sitter_elixir()),
    "erlang" -> (() => Extern.tree_sitter_erlang()),
    "haskell" -> (() => Extern.tree_sitter_haskell()),
    "julia" -> (() => Extern.tree_sitter_julia()),
    "kotlin" -> (() => Extern.tree_sitter_kotlin()),
    "lua" -> (() => Extern.tree_sitter_lua()),
    "make" -> (() => Extern.tree_sitter_make()),
    "ocaml" -> (() => Extern.tree_sitter_ocaml()),
    "ocaml_interface" -> (() => Extern.tree_sitter_ocaml_interface()),
    "php" -> (() => Extern.tree_sitter_php()),
    "php_only" -> (() => Extern.tree_sitter_php_only()),
    "r" -> (() => Extern.tree_sitter_r()),
    "swift" -> (() => Extern.tree_sitter_swift()),
    "vim" -> (() => Extern.tree_sitter_vim()),
    "xml" -> (() => Extern.tree_sitter_xml()),
    "zig" -> (() => Extern.tree_sitter_zig()),
    "agda" -> (() => Extern.tree_sitter_agda()),
    "arduino" -> (() => Extern.tree_sitter_arduino()),
    "bicep" -> (() => Extern.tree_sitter_bicep()),
    "cairo" -> (() => Extern.tree_sitter_cairo()),
    "commonlisp" -> (() => Extern.tree_sitter_commonlisp()),
    "cpon" -> (() => Extern.tree_sitter_cpon()),
    "cuda" -> (() => Extern.tree_sitter_cuda()),
    "diff" -> (() => Extern.tree_sitter_diff()),
    "embedded_template" -> (() => Extern.tree_sitter_embedded_template()),
    "func" -> (() => Extern.tree_sitter_func()),
    "gitattributes" -> (() => Extern.tree_sitter_gitattributes()),
    "glsl" -> (() => Extern.tree_sitter_glsl()),
    "gosum" -> (() => Extern.tree_sitter_gosum()),
    "hare" -> (() => Extern.tree_sitter_hare()),
    "hcl" -> (() => Extern.tree_sitter_hcl()),
    "hlsl" -> (() => Extern.tree_sitter_hlsl()),
    "jsdoc" -> (() => Extern.tree_sitter_jsdoc()),
    "kconfig" -> (() => Extern.tree_sitter_kconfig()),
    "kdl" -> (() => Extern.tree_sitter_kdl()),
    "luadoc" -> (() => Extern.tree_sitter_luadoc()),
    "luap" -> (() => Extern.tree_sitter_luap()),
    "luau" -> (() => Extern.tree_sitter_luau()),
    "objc" -> (() => Extern.tree_sitter_objc()),
    "odin" -> (() => Extern.tree_sitter_odin()),
    "po" -> (() => Extern.tree_sitter_po()),
    "pony" -> (() => Extern.tree_sitter_pony()),
    "printf" -> (() => Extern.tree_sitter_printf()),
    "properties" -> (() => Extern.tree_sitter_properties()),
    "puppet" -> (() => Extern.tree_sitter_puppet()),
    "ql" -> (() => Extern.tree_sitter_ql()),
    "qmldir" -> (() => Extern.tree_sitter_qmldir()),
    "query" -> (() => Extern.tree_sitter_query()),
    "requirements" -> (() => Extern.tree_sitter_requirements()),
    "ron" -> (() => Extern.tree_sitter_ron()),
    "scss" -> (() => Extern.tree_sitter_scss()),
    "squirrel" -> (() => Extern.tree_sitter_squirrel()),
    "starlark" -> (() => Extern.tree_sitter_starlark()),
    "svelte" -> (() => Extern.tree_sitter_svelte()),
    "test" -> (() => Extern.tree_sitter_test()),
    "ungrammar" -> (() => Extern.tree_sitter_ungrammar()),
    "verilog" -> (() => Extern.tree_sitter_verilog()),
    "vue" -> (() => Extern.tree_sitter_vue()),
    "wgsl_bevy" -> (() => Extern.tree_sitter_wgsl_bevy()),
    "yuck" -> (() => Extern.tree_sitter_yuck())
  )

  override def availableGrammars: Seq[String] = grammarLookup.keys.toSeq.sorted

  override def highlight(source: String, grammarName: String, highlightQuery: String): Seq[HighlightSpan] =
    grammarLookup.get(grammarName) match {
      case None            => Seq.empty
      case Some(grammarFn) =>
        val z = Zone.open()
        try
          doHighlight(grammarFn, source, highlightQuery)(using z)
        finally
          z.close()
    }

  private def doHighlight(grammarFn: () => Ptr[Byte], source: String, highlightQuery: String)(using Zone): Seq[HighlightSpan] = {
    val languagePtr = grammarFn()
    val parserPtr   = Extern.ts_parser_new()
    try {
      Extern.ts_parser_set_language(parserPtr, languagePtr)
      val sourceC   = toCString(source)
      val sourceLen = source.getBytes("UTF-8").length.toUInt
      val treePtr   = Extern.ts_parser_parse_string(parserPtr, null, sourceC, sourceLen)
      if (treePtr == null) Seq.empty
      else {
        try {
          val rootNode = stackalloc[Byte](32)
          Extern.ts_tree_root_node_p(treePtr, rootNode)

          val queryC      = toCString(highlightQuery)
          val queryLen    = highlightQuery.getBytes("UTF-8").length.toUInt
          val errorOffset = stackalloc[CUnsignedInt]()
          val errorType   = stackalloc[CUnsignedInt]()
          val queryPtr    = Extern.ts_query_new(languagePtr, queryC, queryLen, errorOffset, errorType)
          if (queryPtr == null) Seq.empty
          else {
            try {
              val captureNames = loadCaptureNames(queryPtr)
              executeQuery(queryPtr, rootNode, captureNames)
            } finally
              Extern.ts_query_delete(queryPtr)
          }
        } finally
          Extern.ts_tree_delete(treePtr)
      }
    } finally
      Extern.ts_parser_delete(parserPtr)
  }

  private def loadCaptureNames(queryPtr: Ptr[Byte]): Array[String] = {
    val count  = Extern.ts_query_capture_count(queryPtr).toInt
    val names  = new Array[String](count)
    val lenBuf = stackalloc[CUnsignedInt]()
    var i      = 0
    while (i < count) {
      val namePtr = Extern.ts_query_capture_name_for_id(queryPtr, i.toUInt, lenBuf)
      names(i) = fromCString(namePtr)
      i += 1
    }
    names
  }

  // TSQueryMatch: { uint32_t id (4), uint16_t pattern_index (2), uint16_t capture_count (2), TSQueryCapture* captures (8) } = 16 bytes
  // TSQueryCapture: { TSNode node (32), uint32_t index (4), padding (4) } = 40 bytes
  // TSNode: { uint32_t context[4] (16), void* id (8), TSTree* tree (8) } = 32 bytes

  private def executeQuery(queryPtr: Ptr[Byte], rootNode: Ptr[Byte], captureNames: Array[String]): Seq[HighlightSpan] = {
    val cursorPtr = Extern.ts_query_cursor_new()
    try {
      Extern.ts_query_cursor_exec_p(cursorPtr, queryPtr, rootNode)
      val matchBuf = stackalloc[Byte](16)
      val spans    = scala.collection.mutable.ArrayBuffer.empty[HighlightSpan]

      while (Extern.ts_query_cursor_next_match(cursorPtr, matchBuf)) {
        val captureCount = !(matchBuf + 6).asInstanceOf[Ptr[CUnsignedShort]]
        val capturesPtr  = !(matchBuf + 8).asInstanceOf[Ptr[Ptr[Byte]]]
        val capCount     = captureCount.toInt
        var c            = 0
        while (c < capCount) {
          val captureBase = capturesPtr + (c.toLong * 40)
          val nodePtr     = captureBase
          val captureIdx  = !(captureBase + 32).asInstanceOf[Ptr[CUnsignedInt]]
          val startByte   = Extern.ts_node_start_byte_p(nodePtr).toInt
          val endByte     = Extern.ts_node_end_byte_p(nodePtr).toInt
          val idx         = captureIdx.toInt
          if (idx >= 0 && idx < captureNames.length) {
            spans += HighlightSpan(startByte, endByte, captureNames(idx))
          }
          c += 1
        }
      }
      spans.toSeq
    } finally
      Extern.ts_query_cursor_delete(cursorPtr)
  }
}
