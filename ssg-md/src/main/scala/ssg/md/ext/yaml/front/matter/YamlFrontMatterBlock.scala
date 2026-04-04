/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/YamlFrontMatterBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package yaml
package front
package matter

import ssg.md.util.ast.{ Block, Node }
import ssg.md.util.sequence.BasedSequence

class YamlFrontMatterBlock extends Block {

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS
}
