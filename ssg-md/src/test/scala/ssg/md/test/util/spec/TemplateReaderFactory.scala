/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/TemplateReaderFactory.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util
package spec

import java.io.InputStream

trait TemplateReaderFactory {

  def create(inputStream: InputStream): TemplateReader
}
