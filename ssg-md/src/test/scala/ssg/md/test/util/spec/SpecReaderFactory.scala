/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/spec/SpecReaderFactory.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util
package spec

import java.io.InputStream

trait SpecReaderFactory[S <: SpecReader] {

  def create(inputStream: InputStream, location: ResourceLocation): S
}
