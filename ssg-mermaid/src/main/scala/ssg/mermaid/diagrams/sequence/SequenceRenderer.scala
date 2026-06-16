/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sequence/sequenceRenderer.ts
 *              mermaid/packages/mermaid/src/diagrams/sequence/svgDraw.js
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces D3 + browser DOM rendering with SvgBuilder-based server-side rendering
 *   Idiom: Pure function from SequenceDb + config → SVG string; custom vertical layout (no dagre)
 *   Renames: sequenceRenderer.draw() → SequenceRenderer.render()
 *            svgDraw helper functions → inline in SequenceRenderer
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sequence

import lowlevel.Nullable
import ssg.mermaid.Accessibility
import ssg.mermaid.{ MermaidConfig, SequenceConfig }
import ssg.mermaid.render.text.TextMetrics
import ssg.graphs.commons.svg.SvgBuilder
import ssg.mermaid.theme.{ CssGenerator, Theme }

import scala.collection.mutable

/** Renders a sequence diagram to SVG.
  *
  * Takes a populated [[SequenceDb]] and produces a complete SVG string. The rendering pipeline:
  *   1. Calculate actor widths from text measurement
  *   1. Space actors horizontally
  *   1. Draw actor boxes/lifelines at top
  *   1. Process messages top to bottom, advancing y position
  *   1. Handle activation boxes (stacking)
  *   1. Handle notes (positioned relative to actors)
  *   1. Handle loop/alt/opt/par/critical/break boxes (nested rectangles)
  *   1. Draw actor boxes at bottom (if mirrored)
  *   1. Return SVG string
  *
  * This renderer does NOT use dagre — sequence diagrams have their own vertical layout algorithm.
  */
object SequenceRenderer {

  /** Width of the stick-figure actor type. */
  private val ActorTypeWidth: Double = 36.0

  /** Default activation width. */
  private val ActivationWidth: Double = 10.0

  /** Default label box height for loop/alt titles. */
  private val LabelBoxHeight: Double = 20.0

  /** Renders a sequence diagram to an SVG string.
    *
    * @param db
    *   the populated sequence database
    * @param config
    *   the Mermaid configuration
    * @return
    *   SVG markup string
    */
  def render(db: SequenceDb, config: MermaidConfig): String = {
    val conf = config.sequence

    // 1. Calculate actor dimensions and spacing
    calculateActorDimensions(db, conf)

    // 2. Space actors horizontally
    spaceActors(db, conf)

    // 3. Create SVG root
    val themeVars = Theme.getThemeByName(config.theme, config.themeVariables)

    // Track vertical position
    var yPos: Double = conf.height + conf.boxMargin * 2

    // Collect SVG elements to be built
    val messageElements    = mutable.ArrayBuffer.empty[MessageRenderInfo]
    val noteElements       = mutable.ArrayBuffer.empty[NoteRenderInfo]
    val loopElements       = mutable.ArrayBuffer.empty[LoopRenderInfo]
    val activationElements = mutable.ArrayBuffer.empty[ActivationRenderInfo]
    val backgroundElements = mutable.ArrayBuffer.empty[LoopRenderInfo]

    // Activation stack per actor
    val activationStack = mutable.ArrayBuffer.empty[ActivationData]

    // Loop stack for nested loop/alt/opt/par/critical/break
    val loopStack = mutable.ArrayBuffer.empty[LoopData]

    // Sequence number tracking
    var sequenceIndex     = 1
    var sequenceIndexStep = 1

    // 4. Process messages top to bottom
    for (msg <- db.messages) {
      msg.msgType match {
        case LineType.Note =>
          val noteInfo = buildNoteModel(msg, db, conf, yPos)
          noteElements += noteInfo
          yPos = noteInfo.stopy + conf.boxMargin

        case LineType.ActiveStart =>
          val actor     = msg.from.getOrElse("")
          val actorData = db.actors.get(actor)
          actorData.foreach { a =>
            val stackSize = activationStack.count(_.actor == actor)
            val x         = a.x + a.width / 2 + (stackSize * ActivationWidth) / 2
            activationStack += ActivationData(
              startx = x,
              starty = yPos + 2,
              stopx = x + ActivationWidth,
              stopy = 0,
              actor = actor
            )
          }

        case LineType.ActiveEnd =>
          val actor = msg.from.getOrElse("")
          val idx   = activationStack.lastIndexWhere(_.actor == actor)
          if (idx >= 0) {
            val activation = activationStack.remove(idx)
            activation.stopy = yPos
            activationElements += ActivationRenderInfo(
              startx = activation.startx,
              starty = activation.starty,
              stopx = activation.stopx,
              stopy = activation.stopy
            )
          }

        case LineType.LoopStart | LineType.AltStart | LineType.OptStart | LineType.ParStart | LineType.ParOverStart | LineType.CriticalStart | LineType.BreakStart =>
          yPos += conf.boxMargin + conf.boxTextMargin
          loopStack += LoopData(
            starty = yPos,
            title = msg.message,
            loopType = getLoopType(msg.msgType)
          )
          yPos += LabelBoxHeight

        case LineType.AltElse | LineType.ParAnd | LineType.CriticalOption =>
          if (loopStack.nonEmpty) {
            val current = loopStack.last
            current.sections += SectionData(y = yPos, title = msg.message)
          }
          yPos += conf.boxMargin + conf.boxTextMargin + LabelBoxHeight

        case LineType.LoopEnd | LineType.AltEnd | LineType.OptEnd | LineType.ParEnd | LineType.CriticalEnd | LineType.BreakEnd =>
          if (loopStack.nonEmpty) {
            val loopData = loopStack.remove(loopStack.length - 1)
            loopData.stopy = yPos + conf.boxMargin
            yPos = loopData.stopy
            loopElements += LoopRenderInfo(
              startx = loopData.startx,
              starty = loopData.starty,
              stopx = loopData.stopx,
              stopy = loopData.stopy,
              title = loopData.title,
              loopType = loopData.loopType,
              sections = loopData.sections.map(s => SectionRenderInfo(s.y, s.title)).toArray
            )
          }

        case LineType.RectStart =>
          yPos += conf.boxMargin
          loopStack += LoopData(
            starty = yPos,
            title = "",
            loopType = "rect",
            fill = msg.message
          )

        case LineType.RectEnd =>
          if (loopStack.nonEmpty) {
            val loopData = loopStack.remove(loopStack.length - 1)
            loopData.stopy = yPos + conf.boxMargin
            yPos = loopData.stopy
            backgroundElements += LoopRenderInfo(
              startx = loopData.startx,
              starty = loopData.starty,
              stopx = loopData.stopx,
              stopy = loopData.stopy,
              title = "",
              loopType = "rect",
              fill = loopData.fill
            )
          }

        case LineType.Autonumber =>
          // Parse autonumber message: "autonumber:start:step:visible"
          val parts = msg.message.split(":")
          if (parts.length >= 4) {
            sequenceIndex = parts(1).toIntOption.getOrElse(sequenceIndex)
            sequenceIndexStep = parts(2).toIntOption.getOrElse(sequenceIndexStep)
            val visible = parts(3).toBooleanOption.getOrElse(true)
            if (visible) db.enableSequenceNumbers() else db.disableSequenceNumbers()
          }

        case _ if LineType.isLineType(msg.msgType) =>
          // It's an actual message arrow
          val msgInfo = buildMessageModel(msg, db, conf, yPos, sequenceIndex)
          messageElements += msgInfo
          yPos = msgInfo.stopy + conf.messageMargin
          sequenceIndex += sequenceIndexStep

        case _ =>
          // Unknown type, skip
          ()
      }
    }

    // 5. Set loop bounds based on actors
    val firstActorX    = db.actors.values.headOption.map(_.x).getOrElse(0.0)
    val lastActor      = db.actors.values.lastOption
    val lastActorRight = lastActor.map(a => a.x + a.width).getOrElse(200.0)
    for (loop <- loopStack) {
      loop.startx = firstActorX - conf.boxMargin
      loop.stopx = lastActorRight + conf.boxMargin
    }

    // Apply bounds to completed loop elements
    for (loop <- loopElements)
      if (loop.startx == 0 && loop.stopx == 0) {
        loop.startx = firstActorX - conf.boxMargin
        loop.stopx = lastActorRight + conf.boxMargin
      }
    for (bg <- backgroundElements)
      if (bg.startx == 0 && bg.stopx == 0) {
        bg.startx = firstActorX - conf.boxMargin
        bg.stopx = lastActorRight + conf.boxMargin
      }

    // 6. Build the SVG
    val bottomActorY = if (conf.mirrorActors) yPos + conf.boxMargin * 2 else 0
    val totalHeight  = if (conf.mirrorActors) {
      bottomActorY + conf.height + conf.diagramMarginY * 2 + conf.bottomMarginAdj
    } else {
      yPos + conf.diagramMarginY * 2
    }
    val totalWidth = lastActorRight + conf.diagramMarginX * 2 + conf.actorMargin

    val viewBox = s"0 0 $totalWidth $totalHeight"
    val svg     = SvgBuilder.createSvg(viewBox)
    svg.attr("role", "img")
    svg.classed("mermaid", true)

    // Accessibility: role + aria-roledescription always; a11y title/desc when present.
    // Mirrors addA11yInfo in mermaidAPI.ts:521-529 (accessibility.ts setA11yDiagramInfo + addSVGa11yTitleDescription).
    Accessibility.applyTo(svg, "sequence", db.accTitle, db.accDescription)

    // Defs + styles
    val defs = svg.append("defs")
    addArrowMarkers(defs)

    val css     = SequenceStyles.generate(themeVars)
    val baseCss = CssGenerator.generateBaseStyles(themeVars)
    val styleEl = defs.append("style")
    styleEl.attr("type", "text/css")
    // Append user themeCSS when configured (mermaidAPI.ts:119-121 applies themeCSS to all diagrams)
    styleEl.text(baseCss + "\n" + css + (if (config.themeCSS.nonEmpty) "\n" + config.themeCSS else ""))

    val mainGroup = svg.append("g")
    mainGroup.attr("transform", s"translate(${conf.diagramMarginX}, ${conf.diagramMarginY})")

    // 7. Draw background rects
    for (bg <- backgroundElements)
      drawBackgroundRect(mainGroup, bg)

    // 8. Draw loop/alt/opt boxes
    for (loop <- loopElements)
      drawLoopBox(mainGroup, loop, conf)

    // 9. Draw actor lifelines
    for ((_, actor) <- db.actors) {
      val lifelineEnd = if (conf.mirrorActors) bottomActorY else yPos
      drawLifeline(mainGroup, actor, lifelineEnd)
    }

    // 10. Draw activations
    for (activation <- activationElements)
      drawActivation(mainGroup, activation)

    // 11. Draw actor boxes at top
    for ((_, actor) <- db.actors)
      drawActorBox(mainGroup, actor, conf, isBottom = false)

    // 12. Draw messages
    for (msgInfo <- messageElements)
      drawMessage(mainGroup, msgInfo, db, conf)

    // 13. Draw notes
    for (noteInfo <- noteElements)
      drawNote(mainGroup, noteInfo)

    // 14. Draw actor boxes at bottom (if mirrored)
    if (conf.mirrorActors) {
      for ((_, actor) <- db.actors)
        drawActorBox(mainGroup, actor, conf, isBottom = true, bottomY = bottomActorY)
    }

    // 15. Draw boxes (actor grouping boxes)
    for (box <- db.boxes)
      if (box.actorKeys.nonEmpty) {
        drawGroupBox(mainGroup, box, db, yPos, conf)
      }

    // 16. Title
    if (db.title.nonEmpty) {
      val titleGroup = mainGroup.append("g")
      val text       = titleGroup.append("text")
      text.attr("x", totalWidth / 2 - conf.diagramMarginX)
      text.attr("y", -5)
      text.attr("text-anchor", "middle")
      text.classed("sequenceTitleText", true)
      text.text(db.title)
    }

    svg.build().toMarkup()
  }

  // --- Layout calculations ---

  /** Calculates dimensions for each actor based on text measurement. */
  private def calculateActorDimensions(db: SequenceDb, conf: SequenceConfig): Unit =
    for ((_, actor) <- db.actors) {
      val bbox = TextMetrics.measureText(actor.description, 14, "sans-serif")
      actor.width = math.max(conf.width.toDouble, bbox.width + 20)
      actor.height = math.max(conf.height.toDouble, bbox.height + 20)
      if (actor.actorType == "actor") {
        actor.width = math.max(actor.width, ActorTypeWidth + 20)
      }
    }

  /** Spaces actors horizontally with margins. */
  private def spaceActors(db: SequenceDb, conf: SequenceConfig): Unit = {
    var x: Double = 0
    for ((_, actor) <- db.actors) {
      actor.x = x
      actor.starty = 0
      x += actor.width + conf.actorMargin
    }
  }

  // --- Model building ---

  /** Builds a note rendering model.
    *
    * When the placement is `Over` and the message references two actors, the note spans from the first actor to the second actor, covering the space between them.
    */
  private def buildNoteModel(
    msg:  SeqMessage,
    db:   SequenceDb,
    conf: SequenceConfig,
    yPos: Double
  ): NoteRenderInfo = {
    val fromId    = msg.from.getOrElse("")
    val toId      = msg.to.getOrElse("")
    val fromActor = db.actors.get(fromId)
    val toActor   = db.actors.get(toId)

    val bbox         = TextMetrics.measureText(msg.message, 12, "sans-serif")
    val minNoteWidth = math.max(conf.width.toDouble, bbox.width + conf.noteMargin * 2)
    val noteHeight   = bbox.height + conf.noteMargin * 2

    val (startx, noteWidth) = msg.placement match {
      case Placement.RightOf =>
        val sx = fromActor.map(a => a.x + a.width + conf.actorMargin / 2.0).getOrElse(0.0)
        (sx, minNoteWidth)
      case Placement.LeftOf =>
        val sx = fromActor.map(a => a.x - minNoteWidth - conf.actorMargin / 2.0).getOrElse(0.0)
        (sx, minNoteWidth)
      case Placement.Over =>
        // Check if the note spans two actors (Note over A,B)
        if (fromId != toId && fromActor.isDefined && toActor.isDefined) {
          val fa = fromActor.get
          val ta = toActor.get
          // Span from the left edge of the leftmost actor to the right edge of the rightmost
          val leftX     = math.min(fa.x, ta.x)
          val rightX    = math.max(fa.x + fa.width, ta.x + ta.width)
          val spanWidth = math.max(minNoteWidth, rightX - leftX + conf.noteMargin * 2)
          val sx        = leftX + (rightX - leftX - spanWidth) / 2.0
          (sx, spanWidth)
        } else {
          val sx = fromActor.map(a => a.x + (a.width - minNoteWidth) / 2.0).getOrElse(0.0)
          (sx, minNoteWidth)
        }
      case _ =>
        val sx = fromActor.map(_.x).getOrElse(0.0)
        (sx, minNoteWidth)
    }

    NoteRenderInfo(
      startx = startx,
      starty = yPos,
      stopx = startx + noteWidth,
      stopy = yPos + noteHeight,
      message = msg.message,
      width = noteWidth,
      height = noteHeight
    )
  }

  /** Builds a message rendering model. */
  private def buildMessageModel(
    msg:      SeqMessage,
    db:       SequenceDb,
    conf:     SequenceConfig,
    yPos:     Double,
    seqIndex: Int
  ): MessageRenderInfo = {
    val fromId    = msg.from.getOrElse("")
    val toId      = msg.to.getOrElse("")
    val fromActor = db.actors.get(fromId)
    val toActor   = db.actors.get(toId)

    val startx = fromActor.map(a => a.x + a.width / 2).getOrElse(0.0)
    val stopx  = toActor.map(a => a.x + a.width / 2).getOrElse(0.0)

    val bbox       = TextMetrics.measureText(msg.message, 12, "sans-serif")
    val textHeight = bbox.height + 10

    val isSelfRef = fromId == toId
    val msgHeight = if (isSelfRef) textHeight + 30 else textHeight + 10

    MessageRenderInfo(
      startx = startx,
      stopx = stopx,
      starty = yPos,
      stopy = yPos + msgHeight,
      message = msg.message,
      msgType = msg.msgType,
      wrap = msg.wrap,
      sequenceIndex = seqIndex,
      showSequenceNumbers = db.showSequenceNumbers
    )
  }

  // --- SVG drawing methods ---

  /** Adds arrowhead markers to defs. */
  private def addArrowMarkers(defs: SvgBuilder): Unit = {
    // Normal arrowhead
    val marker = defs.append("marker")
    marker.attr("id", "arrowhead")
    marker.attr("refX", "9")
    marker.attr("refY", "5")
    marker.attr("markerUnits", "userSpaceOnUse")
    marker.attr("markerWidth", "12")
    marker.attr("markerHeight", "12")
    marker.attr("orient", "auto")
    val path = marker.append("path")
    path.attr("d", "M 0 0 L 10 5 L 0 10 z")

    // Crosshead
    val crossMarker = defs.append("marker")
    crossMarker.attr("id", "crosshead")
    crossMarker.attr("markerWidth", "15")
    crossMarker.attr("markerHeight", "8")
    crossMarker.attr("orient", "auto")
    crossMarker.attr("refX", "4")
    crossMarker.attr("refY", "5")
    val crossPath = crossMarker.append("path")
    crossPath.attr("d", "M 1,2 L 6,7 M 6,2 L 1,7")
    crossPath.attr("style", "stroke-width:2; stroke:currentColor;")

    // Filled head (for point/async arrows)
    val filledMarker = defs.append("marker")
    filledMarker.attr("id", "filled-head")
    filledMarker.attr("refX", "18")
    filledMarker.attr("refY", "7")
    filledMarker.attr("markerWidth", "20")
    filledMarker.attr("markerHeight", "28")
    filledMarker.attr("orient", "auto")
    val filledPath = filledMarker.append("path")
    filledPath.attr("d", "M 18,7 L9,13 L14,7 L9,1 Z")

    // Sequence number circle
    val seqMarker = defs.append("marker")
    seqMarker.attr("id", "sequencenumber")
    seqMarker.attr("refX", "15")
    seqMarker.attr("refY", "15")
    seqMarker.attr("markerWidth", "60")
    seqMarker.attr("markerHeight", "40")
    seqMarker.attr("orient", "auto")
    val seqCircle = seqMarker.append("circle")
    seqCircle.attr("cx", "15")
    seqCircle.attr("cy", "15")
    seqCircle.attr("r", "6")
  }

  /** Draws an actor box (participant or actor stick figure). */
  private def drawActorBox(
    parent:   SvgBuilder,
    actor:    SeqActor,
    conf:     SequenceConfig,
    isBottom: Boolean,
    bottomY:  Double = 0
  ): Unit = {
    val y     = if (isBottom) bottomY else 0.0
    val group = parent.append("g")
    group.classed("actor", true)

    if (actor.actorType == "actor") {
      // Draw stick figure
      drawStickFigure(group, actor, y, conf)
    } else {
      // Draw box
      val rect = group.append("rect")
      rect.attr("x", actor.x)
      rect.attr("y", y)
      rect.attr("width", actor.width)
      rect.attr("height", actor.height)
      rect.attr("rx", "3")
      rect.attr("ry", "3")
      rect.classed("actor", true)

      // Actor label
      val text = group.append("text")
      text.attr("x", actor.x + actor.width / 2)
      text.attr("y", y + actor.height / 2 + 5)
      text.attr("text-anchor", "middle")
      text.attr("dominant-baseline", "central")
      text.classed("actor", true)

      val tspan = text.append("tspan")
      tspan.attr("x", actor.x + actor.width / 2)
      tspan.attr("dy", "0")
      tspan.text(actor.description)
    }
  }

  /** Draws a stick figure actor. */
  private def drawStickFigure(
    parent: SvgBuilder,
    actor:  SeqActor,
    y:      Double,
    conf:   SequenceConfig
  ): Unit = {
    val centerX = actor.x + actor.width / 2

    // Head circle
    val circle = parent.append("circle")
    circle.attr("cx", centerX)
    circle.attr("cy", y + 14)
    circle.attr("r", "10")
    circle.classed("actor-man", true)

    // Body line
    val body = parent.append("line")
    body.attr("x1", centerX)
    body.attr("y1", y + 24)
    body.attr("x2", centerX)
    body.attr("y2", y + 44)
    body.classed("actor-man", true)

    // Arms
    val arms = parent.append("line")
    arms.attr("x1", centerX - 18)
    arms.attr("y1", y + 32)
    arms.attr("x2", centerX + 18)
    arms.attr("y2", y + 32)
    arms.classed("actor-man", true)

    // Legs
    val leftLeg = parent.append("line")
    leftLeg.attr("x1", centerX)
    leftLeg.attr("y1", y + 44)
    leftLeg.attr("x2", centerX - 16)
    leftLeg.attr("y2", y + 60)
    leftLeg.classed("actor-man", true)

    val rightLeg = parent.append("line")
    rightLeg.attr("x1", centerX)
    rightLeg.attr("y1", y + 44)
    rightLeg.attr("x2", centerX + 16)
    rightLeg.attr("y2", y + 60)
    rightLeg.classed("actor-man", true)

    // Label
    val text = parent.append("text")
    text.attr("x", centerX)
    text.attr("y", y + 77)
    text.attr("text-anchor", "middle")
    text.classed("actor", true)
    text.text(actor.description)
  }

  /** Draws a lifeline (vertical dashed line) for an actor. */
  private def drawLifeline(parent: SvgBuilder, actor: SeqActor, endY: Double): Unit = {
    val line    = parent.append("line")
    val centerX = actor.x + actor.width / 2
    line.attr("x1", centerX)
    line.attr("y1", actor.height)
    line.attr("x2", centerX)
    line.attr("y2", endY)
    line.classed("actor-line", true)
    line.attr("stroke-dasharray", "2,2")
    line.attr("stroke", "currentColor")
  }

  /** Draws a message arrow between actors. */
  private def drawMessage(
    parent:  SvgBuilder,
    msgInfo: MessageRenderInfo,
    db:      SequenceDb,
    conf:    SequenceConfig
  ): Unit = {
    val group = parent.append("g")

    // Draw message text
    val textX = (msgInfo.startx + msgInfo.stopx) / 2
    val textY = msgInfo.starty + 10
    val text  = group.append("text")
    text.attr("x", textX)
    text.attr("y", textY)
    text.attr("text-anchor", "middle")
    text.classed("messageText", true)
    text.text(msgInfo.message)

    // Draw the line/arrow
    val lineY    = msgInfo.starty + 20
    val isDotted = LineType.isDotted(msgInfo.msgType)

    if (msgInfo.startx == msgInfo.stopx) {
      // Self-referencing message
      val selfLine = group.append("path")
      if (conf.rightAngles) {
        val dx = math.max(conf.width / 2.0, 50)
        selfLine.attr("d", s"M ${msgInfo.startx},$lineY H ${msgInfo.startx + dx} V ${lineY + 25} H ${msgInfo.startx}")
      } else {
        val sx = msgInfo.startx
        selfLine.attr("d", s"M $sx,$lineY C ${sx + 60},${lineY - 10} ${sx + 60},${lineY + 30} $sx,${lineY + 20}")
      }
      if (isDotted) selfLine.attr("stroke-dasharray", "3, 3")
      selfLine.classed(if (isDotted) "messageLine1" else "messageLine0", true)
      selfLine.attr("stroke-width", "1.5")
      selfLine.attr("fill", "none")
      selfLine.attr("marker-end", "url(#arrowhead)")
    } else {
      val line = group.append("line")
      line.attr("x1", msgInfo.startx)
      line.attr("y1", lineY)
      line.attr("x2", msgInfo.stopx)
      line.attr("y2", lineY)
      line.attr("stroke-width", "2")

      if (isDotted) {
        line.attr("stroke-dasharray", "3, 3")
        line.classed("messageLine1", true)
      } else {
        line.classed("messageLine0", true)
      }

      // Marker based on type
      msgInfo.msgType match {
        case LineType.Solid | LineType.Dotted =>
          line.attr("marker-end", "url(#arrowhead)")
        case LineType.BidirectionalSolid | LineType.BidirectionalDotted =>
          line.attr("marker-start", "url(#arrowhead)")
          line.attr("marker-end", "url(#arrowhead)")
        case LineType.SolidPoint | LineType.DottedPoint =>
          line.attr("marker-end", "url(#filled-head)")
        case LineType.SolidCross | LineType.DottedCross =>
          line.attr("marker-end", "url(#crosshead)")
        case _ =>
          () // Open arrows have no marker
      }
    }

    // Sequence number
    if (msgInfo.showSequenceNumbers) {
      val seqText = group.append("text")
      seqText.attr("x", msgInfo.startx)
      seqText.attr("y", lineY + 4)
      seqText.attr("font-family", "sans-serif")
      seqText.attr("font-size", "12px")
      seqText.attr("text-anchor", "middle")
      seqText.classed("sequenceNumber", true)
      seqText.text(msgInfo.sequenceIndex.toString)
    }
  }

  /** Draws a note box. */
  private def drawNote(parent: SvgBuilder, note: NoteRenderInfo): Unit = {
    val group = parent.append("g")

    val rect = group.append("rect")
    rect.attr("x", note.startx)
    rect.attr("y", note.starty)
    rect.attr("width", note.width)
    rect.attr("height", note.height)
    rect.classed("note", true)

    val text = group.append("text")
    text.attr("x", note.startx + note.width / 2)
    text.attr("y", note.starty + note.height / 2)
    text.attr("text-anchor", "middle")
    text.attr("dominant-baseline", "central")
    text.classed("noteText", true)
    text.text(note.message)
  }

  /** Draws a loop/alt/opt/par/critical/break frame. */
  private def drawLoopBox(parent: SvgBuilder, loop: LoopRenderInfo, conf: SequenceConfig): Unit = {
    val group = parent.append("g")

    // Main rectangle
    val rect = group.append("rect")
    rect.attr("x", loop.startx)
    rect.attr("y", loop.starty - LabelBoxHeight)
    rect.attr("width", loop.stopx - loop.startx)
    rect.attr("height", loop.stopy - loop.starty + LabelBoxHeight)
    rect.classed("loopLine", true)

    // Label box
    val labelRect = group.append("rect")
    labelRect.attr("x", loop.startx)
    labelRect.attr("y", loop.starty - LabelBoxHeight)
    labelRect.attr("width", math.max(50, loop.loopType.length * 8.0 + 10))
    labelRect.attr("height", LabelBoxHeight)
    labelRect.classed("labelBox", true)

    // Label text
    val labelText = group.append("text")
    labelText.attr("x", loop.startx + 5)
    labelText.attr("y", loop.starty - LabelBoxHeight / 2 + 5)
    labelText.classed("labelText", true)
    labelText.text(loop.loopType)

    // Title text (the condition/description)
    if (loop.title.nonEmpty) {
      val titleText = group.append("text")
      titleText.attr("x", loop.startx + loop.loopType.length * 8.0 + 15)
      titleText.attr("y", loop.starty - LabelBoxHeight / 2 + 5)
      titleText.classed("loopText", true)
      titleText.text(s"[${loop.title}]")
    }

    // Section dividers
    for (section <- loop.sections) {
      val divider = group.append("line")
      divider.attr("x1", loop.startx)
      divider.attr("y1", section.y)
      divider.attr("x2", loop.stopx)
      divider.attr("y2", section.y)
      divider.attr("stroke-dasharray", "3, 3")
      divider.classed("loopLine", true)

      if (section.title.nonEmpty) {
        val sectionText = group.append("text")
        sectionText.attr("x", loop.startx + 5)
        sectionText.attr("y", section.y + 15)
        sectionText.classed("loopText", true)
        sectionText.text(s"[${section.title}]")
      }
    }
  }

  /** Draws a background rect. */
  private def drawBackgroundRect(parent: SvgBuilder, bg: LoopRenderInfo): Unit = {
    val rect = parent.append("rect")
    rect.attr("x", bg.startx)
    rect.attr("y", bg.starty)
    rect.attr("width", bg.stopx - bg.startx)
    rect.attr("height", bg.stopy - bg.starty)
    if (bg.fill.nonEmpty) {
      rect.attr("fill", bg.fill)
    }
    rect.attr("opacity", "0.1")
  }

  /** Draws an activation box. */
  private def drawActivation(parent: SvgBuilder, activation: ActivationRenderInfo): Unit = {
    val rect = parent.append("rect")
    rect.attr("x", activation.startx)
    rect.attr("y", activation.starty)
    rect.attr("width", activation.stopx - activation.startx)
    rect.attr("height", math.max(activation.stopy - activation.starty, 18))
    rect.classed("activation0", true)
  }

  /** Draws a grouping box around actors. */
  private def drawGroupBox(
    parent: SvgBuilder,
    box:    SeqBox,
    db:     SequenceDb,
    yPos:   Double,
    conf:   SequenceConfig
  ): Unit =
    if (box.actorKeys.nonEmpty) {
      val firstActor = db.actors.get(box.actorKeys.head)
      val lastActor  = db.actors.get(box.actorKeys.last)
      if (firstActor.nonEmpty && lastActor.nonEmpty) {
        val startx = firstActor.get.x - conf.boxMargin
        val stopx  = lastActor.get.x + lastActor.get.width + conf.boxMargin
        val height = yPos + conf.boxMargin

        val group = parent.append("g")
        val rect  = group.append("rect")
        rect.attr("x", startx)
        rect.attr("y", 0)
        rect.attr("width", stopx - startx)
        rect.attr("height", height)
        rect.attr("fill", box.fill)
        rect.attr("opacity", "0.15")
        rect.attr("stroke", "rgb(0,0,0,0.5)")
        rect.attr("rx", "3")
        rect.attr("ry", "3")

        if (box.name.nonEmpty) {
          val text = group.append("text")
          text.attr("x", (startx + stopx) / 2)
          text.attr("y", 12)
          text.attr("text-anchor", "middle")
          text.classed("labelText", true)
          text.text(box.name)
        }
      }
    }

  /** Returns the loop type keyword for a given line type. */
  private def getLoopType(lineType: Int): String =
    lineType match {
      case LineType.LoopStart     => "loop"
      case LineType.AltStart      => "alt"
      case LineType.OptStart      => "opt"
      case LineType.ParStart      => "par"
      case LineType.ParOverStart  => "par"
      case LineType.CriticalStart => "critical"
      case LineType.BreakStart    => "break"
      case _                      => "loop"
    }
}

// --- Internal render info types ---

final private[sequence] case class ActorRenderInfo(
  x:         Double,
  y:         Double,
  width:     Double,
  height:    Double,
  name:      String,
  actorType: String
)

final private[sequence] case class MessageRenderInfo(
  startx:              Double,
  stopx:               Double,
  starty:              Double,
  stopy:               Double,
  message:             String,
  msgType:             Int,
  wrap:                Boolean,
  sequenceIndex:       Int,
  showSequenceNumbers: Boolean
)

final private[sequence] case class NoteRenderInfo(
  startx:  Double,
  starty:  Double,
  stopx:   Double,
  stopy:   Double,
  message: String,
  width:   Double,
  height:  Double
)

final private[sequence] case class LoopRenderInfo(
  var startx: Double = 0,
  var starty: Double = 0,
  var stopx:  Double = 0,
  var stopy:  Double = 0,
  title:      String = "",
  loopType:   String = "loop",
  fill:       String = "",
  sections:   Array[SectionRenderInfo] = Array.empty
)

final private[sequence] case class SectionRenderInfo(
  y:     Double,
  title: String
)

final private[sequence] case class ActivationRenderInfo(
  startx: Double,
  starty: Double,
  stopx:  Double,
  stopy:  Double
)

final private[sequence] case class ActivationData(
  startx:    Double,
  starty:    Double,
  stopx:     Double,
  var stopy: Double,
  actor:     String
)

final private[sequence] class LoopData(
  var startx:   Double = 0,
  var starty:   Double = 0,
  var stopx:    Double = 0,
  var stopy:    Double = 0,
  var title:    String = "",
  var loopType: String = "loop",
  var fill:     String = "",
  val sections: mutable.ArrayBuffer[SectionData] = mutable.ArrayBuffer.empty
)

final private[sequence] case class SectionData(
  y:     Double,
  title: String
)
