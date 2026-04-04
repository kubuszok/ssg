/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/internal/EmojiOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package emoji
package internal

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class EmojiOptions(options: DataHolder) {

  val rootImagePath:       String            = EmojiExtension.ROOT_IMAGE_PATH.get(options)
  val useShortcutType:     EmojiShortcutType = EmojiExtension.USE_SHORTCUT_TYPE.get(options)
  val useImageType:        EmojiImageType    = EmojiExtension.USE_IMAGE_TYPE.get(options)
  val attrImageSize:       String            = EmojiExtension.ATTR_IMAGE_SIZE.get(options)
  val attrAlign:           String            = EmojiExtension.ATTR_ALIGN.get(options)
  val attrImageClass:      String            = EmojiExtension.ATTR_IMAGE_CLASS.get(options)
  val useUnicodeFileNames: Boolean           = EmojiExtension.USE_UNICODE_FILE_NAMES.get(options)
}
