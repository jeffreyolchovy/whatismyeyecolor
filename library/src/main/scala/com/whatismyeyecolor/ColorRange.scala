package com.whatismyeyecolor

import scala.reflect.runtime.universe._
import org.opencv.core.Scalar

sealed abstract class ColorRange(val lowerBound: Scalar, val upperBound: Scalar) {
  val name = runtimeMirror(getClass.getClassLoader)
    .classSymbol(getClass)
    .name
    .toString

  def this(saneHsvLowerBound: (Int, Int, Int), saneHsvUpperBound: (Int, Int, Int)) = {
    this(
      (ColorRange.fromSaneHsv _).tupled(saneHsvLowerBound),
      (ColorRange.fromSaneHsv _).tupled(saneHsvUpperBound)
    )
  }
}

object ColorRange {
  case object Blue extends ColorRange((166, 21, 50), (240, 100, 85))
  case object BlueGray extends ColorRange((166, 2, 25), (300, 20, 75))
  case object Brown extends ColorRange((2, 20, 20), (40, 100, 60))
  case object BrownGray extends ColorRange((20, 3, 30), (65, 60, 60))
  case object BrownBlack extends ColorRange((0, 10, 5), (40, 40, 25))
  case object Green extends ColorRange((60, 21, 50), (165, 100, 85))
  case object GreenGray extends ColorRange((60, 2, 25), (165, 20, 65))

  lazy val AllRanges = Set(Blue, BlueGray, Brown, BrownGray, BrownBlack, Green, GreenGray)

  /** Convert from the typical H:360,S:100,V:100 scale to the OpenCV H:180,S:255,V:255 scale */
  def fromSaneHsv(h: Int, s: Int, v: Int): Scalar = {
    val cvH = h / 2
    val cvS = (s * 255) / 100
    val cvV = (v * 255) / 100
    new Scalar(cvH, cvS, cvV)
  }
}
