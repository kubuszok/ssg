/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-builder/src/main/java/com/vladsch/flexmark/util/builder/BuilderBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-builder/src/main/java/com/vladsch/flexmark/util/builder/BuilderBase.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package build

import ssg.md.Nullable
import ssg.md.util.data.*
import ssg.md.util.data.SharedDataKeys.EXTENSIONS
import ssg.md.util.misc.Extension

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

abstract class BuilderBase[T <: BuilderBase[T]](options: Nullable[DataHolder]) extends MutableDataSet(options) {

  def this() =
    this(Nullable.empty[DataHolder])

  // loaded extensions
  private val loadedExtensions: mutable.HashSet[Class[?]] = mutable.HashSet.empty

  // map of which api points were loaded by which extensions
  private val extensionApiPoints: mutable.HashMap[Class[?], mutable.HashSet[AnyRef]] = mutable.HashMap.empty
  private var currentExtension:   Nullable[Extension]                                = Nullable.empty

  /** Remove apiPoint from state information
    *
    * @param apiPoint
    *   api point object
    */
  protected def removeApiPoint(apiPoint: AnyRef): Unit

  /** Preload operation for extension, perform any data config and other operation needed for loading extension
    *
    * @param extension
    *   to preload
    */
  protected def preloadExtension(extension: Extension): Unit

  /** Load extension if it is valid
    *
    * @param extension
    *   to load
    * @return
    *   true if extension was loaded
    */
  protected def loadExtension(extension: Extension): Boolean

  /** @param extensions
    *   extensions to load
    * @return
    *   `this`
    */
  final def extensions(extensions: java.util.Collection[? <: Extension]): T = {
    val addedExtensions = new ArrayBuffer[Extension](EXTENSIONS.get(Nullable(this.asInstanceOf[DataHolder])).size + extensions.size)

    // first give extensions a chance to modify parser options
    val extIter = extensions.iterator()
    while (extIter.hasNext) {
      val extension = extIter.next()
      currentExtension = Nullable(extension)
      if (!loadedExtensions.contains(extension.getClass)) {
        preloadExtension(extension)
        addedExtensions += extension
      }
      currentExtension = Nullable.empty
    }

    val extIter2 = extensions.iterator()
    while (extIter2.hasNext) {
      val extension = extIter2.next()
      currentExtension = Nullable(extension)
      val extensionClass = extension.getClass
      if (!loadedExtensions.contains(extensionClass)) {
        if (loadExtension(extension)) {
          loadedExtensions += extensionClass
          addedExtensions += extension
        }
      }
      currentExtension = Nullable.empty
    }

    if (addedExtensions.nonEmpty) {
      // need to set extensions to options[EXTENSIONS] data key to make it all consistent
      val existing = EXTENSIONS.get(Nullable(this.asInstanceOf[DataHolder]))
      val merged   = new java.util.ArrayList[Extension](existing.size + addedExtensions.size)
      merged.addAll(existing)
      for (ext <- addedExtensions)
        merged.add(ext)
      set(EXTENSIONS, merged.asInstanceOf[java.util.Collection[Extension]])
    }

    this.asInstanceOf[T]
  }

  /** @return
    *   actual instance the builder is supposed to build
    */
  def build(): AnyRef

  /** Call to add extension API point to track
    *
    * @param apiPoint
    *   point registered
    */
  protected def addExtensionApiPoint(apiPoint: AnyRef): Unit =
    if (currentExtension.isDefined) {
      val extension      = currentExtension.get
      val extensionClass = extension.getClass
      val apiPoints      = extensionApiPoints.getOrElseUpdate(extensionClass, mutable.HashSet.empty)
      apiPoints += apiPoint
    }

  /** Tracks keys set by extension initialization
    *
    * @param key
    *   data key
    * @param value
    *   value for the key
    * @return
    *   builder
    */
  override def set[V](key: DataKey[V], value: V): MutableDataSet = {
    addExtensionApiPoint(key)
    super.set(key, value)
  }

  override def set[V](key: NullableDataKey[V], value: Nullable[V]): MutableDataSet = {
    addExtensionApiPoint(key)
    super.set(key, value)
  }

  /** Get the given key, if it does not exist then use the key's factory to create a new value and put it into the collection so that the following get of the same key will find a value
    *
    * @param key
    *   data key
    * @return
    *   return stored value or newly created value
    */
  @deprecated("use key.get(dataHolder) instead, which will do the same thing and carries nullable information for the data", "")
  override def get[V](key: DataKey[V]): V =
    key.get(Nullable(this.asInstanceOf[DataHolder]))

  protected def loadExtensions(): Unit =
    if (contains(EXTENSIONS)) {
      this.extensions(EXTENSIONS.get(Nullable(this.asInstanceOf[DataHolder])))
    }
}

object BuilderBase {

  /** Remove given extensions from options[EXTENSIONS] data key.
    *
    * @param options
    *   options where EXTENSIONS key is set
    * @param excludeExtensions
    *   collection of extension classes to remove from extensions
    * @return
    *   modified options if removed and options were immutable or the same options if nothing to remove or options were mutable.
    */
  def removeExtensions(options: DataHolder, excludeExtensions: java.util.Collection[Class[? <: Extension]]): DataHolder =
    if (options.contains(EXTENSIONS)) {
      val extensions = new java.util.ArrayList[Extension](EXTENSIONS.get(Nullable(options)))
      val removed    = extensions.removeIf(it => excludeExtensions.contains(it.getClass))
      if (removed) {
        options match {
          case mutable: MutableDataHolder =>
            mutable.set(EXTENSIONS, extensions.asInstanceOf[java.util.Collection[Extension]])
          case _ =>
            options.toMutable.set(EXTENSIONS, extensions.asInstanceOf[java.util.Collection[Extension]]).toImmutable
        }
      } else {
        options
      }
    } else {
      options
    }
}
