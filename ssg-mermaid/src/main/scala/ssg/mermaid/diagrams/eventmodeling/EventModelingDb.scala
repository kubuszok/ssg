/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SSG-native event modeling diagram (not an upstream Mermaid diagram type).
 */
package ssg
package mermaid
package diagrams
package eventmodeling

import scala.collection.mutable

/** An event in the event modeling diagram. */
final case class EmEvent(id: String, label: String, lane: String, eventType: String = "event")

/** Mutable database for event modeling diagram data. */
final class EventModelingDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val lanes:  mutable.ArrayBuffer[String]           = mutable.ArrayBuffer.empty
  val events: mutable.ArrayBuffer[EmEvent]          = mutable.ArrayBuffer.empty
  val flows:  mutable.ArrayBuffer[(String, String)] = mutable.ArrayBuffer.empty

  def addLane(name: String):                                                          Unit = if (!lanes.contains(name)) lanes += name
  def addEvent(id: String, label: String, lane: String, eventType: String = "event"): Unit = {
    addLane(lane); events += EmEvent(id, label, lane, eventType)
  }
  def addFlow(fromId: String, toId: String): Unit = flows += ((fromId, toId))

  def clear(): Unit = {
    title = ""; accTitle = ""; accDescription = ""; lanes.clear(); events.clear(); flows.clear()
  }
}
