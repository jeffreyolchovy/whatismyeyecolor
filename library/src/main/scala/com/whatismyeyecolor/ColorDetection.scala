package com.whatismyeyecolor

import org.opencv.core._
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory

// todo given vector with color amounts / ratios, find k nearest neighbors in pre-trained / cataloged set
// determine eye color by the nearest neighbors
object ColorDetection {

  val log = LoggerFactory.getLogger(getClass)

  case object Brown extends Color((2, 20, 20), (40, 100, 55))

  case object Blue extends Color((155, 15, 20), (255, 100, 100))

  case object Gray extends Color((100, 5, 5), (255, 60, 40))

  case object Green extends Color((70, 15, 20), (155, 100, 100))

  case object Amber extends Color((30, 50, 60), (55, 100, 90))

  val Colors = Set(Brown, Blue, Gray, Green, Amber)

  def apply(input: Mat, pupilCenter: Point, pupilRadius: Int, irisRadius: Int, numColors: Int): Set[Result] = {
    // reduce the amount of colors used in the image
    val reducedInput = MatUtils.quantizeColors(input, k = numColors)

    // enclose the circular roi in a rectangular mat
    val rectRoi = MatUtils.findRectEnclosingCircle(pupilCenter, irisRadius, input.width, input.height)
    val roi = new Mat(reducedInput, rectRoi)

    // create a black mask
    val mask = Mat.zeros(roi.size, roi.`type`)
    Imgproc.circle(mask, new Point(irisRadius, irisRadius), irisRadius, Scalar.all(255), -1)

    // place the circular roi on the black mask
    val circularInput = mask.clone()
    Core.bitwise_and(roi, mask, circularInput)

    // draw a black circle on top of the pupil to avoid false positives due to glare, etc.
    Imgproc.circle(circularInput, new Point(rectRoi.width / 2, rectRoi.height / 2), pupilRadius, Scalar.all(0), -1)

    for {
      color <- Colors
    } yield {
      val result = detectColor(circularInput, color)
      log.debug(s"Detected ${result.area} ${result.color.name} pixel(s)")
      result
    }
  }

  def detectColor(input: Mat, color: Color): Result = {
    val hsvInput = input.clone()
    Imgproc.cvtColor(input, hsvInput, Imgproc.COLOR_BGR2HSV)
    val mask = Mat.zeros(hsvInput.size, hsvInput.`type`)
    Core.inRange(hsvInput, color.lowerBound, color.upperBound, mask)
    val output = new Mat
    input.copyTo(output, mask)
    Result(color, Core.countNonZero(mask), output)
  }

  /** Convert from the typical H:360,S:100,V:100 scale to the OpenCV H:180,S:255,V:255 scale */
  private def fromSaneHsv(h: Int, s: Int, v: Int): Scalar = {
    val cvH = h / 2
    val cvS = (s * 255) / 100
    val cvV = (v * 255) / 100
    new Scalar(cvH, cvS, cvV)
  }

  abstract class Color(val lowerBound: Scalar, val upperBound: Scalar) {
    val name = {
      val className = getClass.getSimpleName.toLowerCase
      if (className.endsWith("$")) className.replace("$", "") else className
    }

    def this(saneHsvLowerBound: (Int, Int, Int), saneHsvUpperBound: (Int, Int, Int)) = {
      this(
        (fromSaneHsv _).tupled(saneHsvLowerBound),
        (fromSaneHsv _).tupled(saneHsvUpperBound)
      )
    }
  }

  case class Result(color: Color, area: Int, mat: Mat)
}
