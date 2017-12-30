package com.whatismyeyecolor

import org.opencv.core._
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory

object ColorDetection {

  val log = LoggerFactory.getLogger(getClass)

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
      colorRange <- ColorRange.AllRanges
    } yield {
      val result = detectColorPresence(circularInput, colorRange)
      log.debug(s"Detected ${result.area} ${result.colorRange.name.toLowerCase} pixel(s)")
      result
    }
  }

  def detectColorPresence(input: Mat, colorRange: ColorRange): Result = {
    val hsvInput = input.clone()
    Imgproc.cvtColor(input, hsvInput, Imgproc.COLOR_BGR2HSV)
    val mask = Mat.zeros(hsvInput.size, hsvInput.`type`)
    Core.inRange(hsvInput, colorRange.lowerBound, colorRange.upperBound, mask)
    val output = new Mat
    input.copyTo(output, mask)
    Result(colorRange, Core.countNonZero(mask), output)
  }

  case class Result(colorRange: ColorRange, area: Int, mat: Mat)
}
