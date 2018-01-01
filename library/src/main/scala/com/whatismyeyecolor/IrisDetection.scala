package com.whatismyeyecolor

import org.opencv.core._
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory

object IrisDetection {

  val log = LoggerFactory.getLogger(getClass)

  def apply(input: Mat): (Point, Int, Int) = {
    val (pupilCenter, pupilRadius) = PupilDetection(input)
    val irisRadius = getIrisRadius(input, pupilCenter, pupilRadius)
    (pupilCenter, pupilRadius, irisRadius)
  }

  def mark(input: Mat, center: Point, radius1: Int, radius2: Int): Mat = {
    val output = input.clone()
    Imgproc.circle(output, center, 1, new Scalar(255, 0, 0))
    Imgproc.circle(output, center, radius1, new Scalar(0, 0, 255))
    Imgproc.circle(output, center, radius2, new Scalar(0, 255, 0))
    output
  }

  def getIrisRadius(input: Mat, pupilCenter: Point, pupilRadius: Int): Int = {
    val minRadius = math.max(pupilRadius, 7)
    val maxRadius = {
      val tmp = math.ceil(pupilRadius * 3.5).toInt
      if (tmp <= minRadius) minRadius + 2 else tmp
    }
    val maxAttempts = math.min(10, maxRadius - minRadius)
    val threshold = 128
    val blurRadius = 2
    val areaAtRadii = for {
      attempt <- 0 until maxAttempts
      searchRadius = minRadius + attempt
    } yield {
      val area = findCircularArea(input, pupilCenter, searchRadius, threshold, blurRadius)
      (area, searchRadius)
    }
    val radius = {
      val i = findIndexOfMaxDelta(areaAtRadii.map(_._1))
      areaAtRadii(i)._2
    }
    log.info(s"Detected iris radius is $radius (of [$minRadius,$maxRadius])")
    radius
  }

  private def findCircularArea(input: Mat, center: Point, radius: Int, minThreshold: Int, blurRadius: Int): Int = {
    val blurredInput = input.clone()
    Imgproc.blur(input, blurredInput, new Size(blurRadius, blurRadius))

    // enclose the circular roi in a rectangular mat
    val rectRoi = MatUtils.findRectEnclosingCircle(center, radius, input.width, input.height)
    val roi = new Mat(blurredInput, rectRoi)

    // create a black mask
    val mask = Mat.zeros(roi.size, roi.`type`)
    Imgproc.circle(mask, new Point(radius, radius), radius, Scalar.all(255), -1)

    // place the circular roi on the black mask
    val circularInput = mask.clone()
    Core.bitwise_and(roi, mask, circularInput)

    val grayscaleCircularInput = MatUtils.grayscale(circularInput)
    Imgproc.threshold(
      grayscaleCircularInput,
      grayscaleCircularInput,
      minThreshold,
      255,
      Imgproc.THRESH_BINARY
    )

    Core.countNonZero(grayscaleCircularInput)
  }

  private def findIndexOfMaxDelta(input: Seq[Int]): Int = {
    def helper(xs: Seq[Int]) = xs.sliding(2).map { ys => ys.max - ys.min }.toList
    val deltas = helper(input)
    val deltas2 = helper(deltas)
    deltas2.lastIndexOf(deltas2.max) + 1
  }
}
