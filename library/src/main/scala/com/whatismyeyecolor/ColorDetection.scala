package com.whatismyeyecolor

import org.opencv.core._
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory

// todo given vector with color amounts / ratios, find k nearest neighbors in pre-trained / cataloged set
// determine eye color by the nearest neighbors
object ColorDetection {

  val log = LoggerFactory.getLogger(getClass)

  case object Brown extends Color(fromSaneHsv(2, 15, 20), fromSaneHsv(30, 80, 40))

  case object Blue extends Color(fromSaneHsv(155, 15, 30), fromSaneHsv(255, 90, 90))

  case object Gray extends Color(fromSaneHsv(5, 5, 5), fromSaneHsv(275, 40, 25))

  case object Green extends Color(fromSaneHsv(70, 10, 20), fromSaneHsv(155, 90, 90))

  case object Yellow extends Color(fromSaneHsv(30, 70, 60), fromSaneHsv(65, 90, 90))

  val Colors = Set(Brown, Blue, Gray, Green, Yellow)

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

  private def fromSaneHsv(h: Int, s: Int, v: Int): Scalar = {
    val cvH = h / 2
    val cvS = math.max(math.min((s * 255) / 100, 255), 0)
    val cvV = math.max(math.min((v * 255) / 100, 255), 0)
    new Scalar(cvH, cvS, cvV)
  }

  abstract class Color(val lowerBound: Scalar, val upperBound: Scalar) {
    val name = getClass.getSimpleName.replace("$", "").toLowerCase
  }

  case class Result(color: Color, area: Int, mat: Mat)
}
