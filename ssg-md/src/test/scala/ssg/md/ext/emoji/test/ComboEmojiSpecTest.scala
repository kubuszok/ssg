/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/.../ComboEmojiSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package emoji
package test

import ssg.md.Nullable
import ssg.md.ext.emoji.{EmojiExtension, EmojiImageType, EmojiShortcutType}
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{DataHolder, MutableDataSet}

import java.util.{Collections, HashMap}
import scala.language.implicitConversions

final class ComboEmojiSpecTest extends RendererSpecTestSuite {
  override def specResource: ResourceLocation = ComboEmojiSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboEmojiSpecTest.OPTIONS)
  override def optionsMap: java.util.Map[String, ? <: DataHolder] = ComboEmojiSpecTest.OPTIONS_MAP
}

object ComboEmojiSpecTest {
  val SPEC_RESOURCE: String = "/ssg/md/ext/emoji/test/ext_emoji_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboEmojiSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(EmojiExtension.create()))
    .set(EmojiExtension.ROOT_IMAGE_PATH, "/img/")
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("use-github", new MutableDataSet().set(EmojiExtension.USE_SHORTCUT_TYPE, EmojiShortcutType.GITHUB).toImmutable)
    map.put("use-cheat", new MutableDataSet().set(EmojiExtension.USE_SHORTCUT_TYPE, EmojiShortcutType.EMOJI_CHEAT_SHEET).toImmutable)
    map.put("prefer-github", new MutableDataSet().set(EmojiExtension.USE_SHORTCUT_TYPE, EmojiShortcutType.ANY_GITHUB_PREFERRED).toImmutable)
    map.put("prefer-cheat", new MutableDataSet().set(EmojiExtension.USE_SHORTCUT_TYPE, EmojiShortcutType.ANY_EMOJI_CHEAT_SHEET_PREFERRED).toImmutable)
    map.put("unicode", new MutableDataSet().set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_FALLBACK_TO_IMAGE).toImmutable)
    map.put("unicode-only", new MutableDataSet().set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_ONLY).toImmutable)
    map.put("unicode-file", new MutableDataSet().set(EmojiExtension.USE_UNICODE_FILE_NAMES, true).toImmutable)
    map.put("size", new MutableDataSet().set(EmojiExtension.ATTR_IMAGE_SIZE, "40").toImmutable)
    map.put("no-size", new MutableDataSet().set(EmojiExtension.ATTR_IMAGE_SIZE, "").toImmutable)
    map.put("no-align", new MutableDataSet().set(EmojiExtension.ATTR_ALIGN, "").toImmutable)
    map
  }
}
