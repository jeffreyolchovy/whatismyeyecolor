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
  case object Amber extends ColorRange((30, 50, 60), (55, 100, 90))
  case object Blue extends ColorRange((155, 15, 20), (255, 100, 100))
  case object Brown extends ColorRange((2, 20, 20), (40, 100, 55))
  case object DarkBrown extends ColorRange((0, 10, 5), (40, 40, 25))
  case object LightBrown extends ColorRange((30, 15, 30), (50, 40, 60))
  case object CoolGray extends ColorRange((100, 5, 5), (255, 60, 40))
  case object WarmGray extends ColorRange((20, 5, 30), (65, 60, 60))
  case object Green extends ColorRange((60, 15, 20), (155, 100, 100))

  lazy val AllRanges = Set(Amber, Blue, Brown, DarkBrown, LightBrown, CoolGray, WarmGray, Green)

  /** Convert from the typical H:360,S:100,V:100 scale to the OpenCV H:180,S:255,V:255 scale */
  def fromSaneHsv(h: Int, s: Int, v: Int): Scalar = {
    val cvH = h / 2
    val cvS = (s * 255) / 100
    val cvV = (v * 255) / 100
    new Scalar(cvH, cvS, cvV)
  }
}
