package com.whatismyeyecolor

import scala.collection.JavaConverters._
import org.opencv.core._
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory

object PupilDetection {

  val log = LoggerFactory.getLogger(getClass)

  def apply(input: Mat): (Point, Int) = {
    val center = getPupilCenter(input)
    val radius = getPupilRadius(input, center)
    (center, radius)
  }

  def mark(input: Mat, center: Point, radius: Int): Mat = {
    val output = input.clone()
    Imgproc.circle(output, center, 1, new Scalar(0, 0, 255))
    Imgproc.circle(output, center, radius, new Scalar(0, 255, 0))
    output
  }

  def getPupilCenter(input: Mat): Point = {
    // restrict the pupil search space to a circular region in the center of the image
    val inputCenter = new Point(input.width / 2, input.height / 2)
    val inputSearchRadius = input.width / 2
    val mask = new Mat(input.size, input.`type`, Scalar.all(255))
    Imgproc.circle(mask, new Point(inputSearchRadius, inputSearchRadius), inputSearchRadius, Scalar.all(0), -1)
    val circularInput = mask.clone()
    Core.bitwise_or(input, mask, circularInput)

    // convert to grayscale and compute gradients
    val grayscaleInput = MatUtils.grayscale(circularInput)
    val gradientX = MatUtils.horizontalGradient(grayscaleInput)
    val gradientY = MatUtils.verticalGradient(grayscaleInput)
    val distanceMat = MatUtils.distance(gradientX, gradientY)
    val threshold = computeDynamicThreshold(distanceMat)

    // replace gradient values with 0 if they are not greater than computed threshold
    for {
      i <- 0 until distanceMat.rows
      j <- 0 until distanceMat.cols
      d = distanceMat.get(i, j)(0)
    } {
      if (d > threshold) {
        val x = gradientX.get(i, j)(0)
        val y = gradientY.get(i, j)(0)
        gradientX.put(i, j, x / d)
        gradientY.put(i, j, y / d)
      } else {
        gradientX.put(i, j, 0D)
        gradientY.put(i, j, 0D)
      }
    }

    // evaluate every possible center for each gradient
    val acc = Mat.zeros(input.rows, input.cols, CvType.CV_64F)
    for {
      i <- 0 until input.rows
      j <- 0 until input.cols
      x = gradientX.get(i, j)(0)
      y = gradientY.get(i, j)(0)
      if ((y, x) != (0, 0))
      ii <- 0 until acc.rows
      jj <- 0 until acc.cols
      if ((i, j) != (ii, jj))
    } {
      // create a vector from the possible center to the gradient origin
      val dj = j - jj
      val di = i - ii
      val d = math.sqrt(math.pow(dj, 2) + math.pow(di, 2))
      val nj = dj / d
      val ni = di / d
      // calculate, square, and accumulate the dot product (or replace with zero if negative)
      val dotProductOrZero = math.max(0D, (nj * x) + (ni * y))
      val currentValue = acc.get(ii, jj)(0)
      val nextValue = currentValue + math.pow(dotProductOrZero, 2)
      acc.put(ii, jj, nextValue)
    }

    // scale down all values in the accumulated results
    val result = acc.clone()
    acc.convertTo(result, CvType.CV_32F, 1D / (input.rows * input.cols))

    // return the maximum point in the output
    Core.minMaxLoc(result).maxLoc
  }

  def getPupilRadius(input: Mat, center: Point): Int = {
    var attempt = 0
    val maxAttempts = 10
    var maxRadius = math.min(input.cols, input.rows) / 3
    val minRadius = maxRadius / 5
    var currentRadius = maxRadius
    var thresholdWeight = 10
    val initBlurRadius = 3
    val maxBlurRadius = maxRadius
    do {
      currentRadius = findBestRadius(
        input,
        center,
        currentRadius,
        thresholdWeight,
        initBlurRadius,
        maxBlurRadius
      )
      attempt += 1
      thresholdWeight -= 1
      log.info(s"Current detected pupil radius is $currentRadius (of [$minRadius,$maxRadius])")
    } while (currentRadius > minRadius && attempt < maxAttempts)

    if (attempt == maxAttempts) {
      log.warn(s"Max attempts ($maxAttempts) for finding ideal pupil radius exhausted")
    }

    if (currentRadius < minRadius) {
      log.info(s"Using minimum value $minRadius as pupil radius (best detected radius was $currentRadius)")
    }

    math.max(currentRadius, minRadius)
  }

  private def computeDynamicThreshold(input: Mat, stdDevFactor: Double = 50D): Double = {
    val meanMat = new MatOfDouble
    val stdDevMat = new MatOfDouble
    Core.meanStdDev(input, meanMat, stdDevMat)
    val meanValue = meanMat.get(0, 0)(0)
    val stdDevValue = stdDevMat.get(0, 0)(0)
    val stdDev = stdDevValue / math.sqrt(input.rows * input.cols)
    stdDevFactor * stdDev + meanValue
  }

  private def findBestRadius(
    input: Mat,
    center: Point,
    maxRadius: Int,
    thresholdWeight: Int,
    initBlurRadius: Int,
    maxBlurRadius: Int
  ): Int = {
    val rectRoi = MatUtils.findRectEnclosingCircle(center, maxRadius, input.width, input.height)
    var bestRadius = maxRadius
    var currentBlurRadius = initBlurRadius
    while (bestRadius >= maxRadius && currentBlurRadius <= maxBlurRadius) {
      val blurredInput = input.clone()
      Imgproc.blur(input, blurredInput, new Size(currentBlurRadius, currentBlurRadius))

      // enclose the circular roi in a rectangular mat
      val roi = new Mat(blurredInput, rectRoi)

      // place the circular roi on a white mask
      val mask = new Mat(roi.size, roi.`type`, Scalar.all(255))
      Imgproc.circle(mask, new Point(maxRadius, maxRadius), maxRadius, Scalar.all(0), -1)
      val circularInput = mask.clone()
      Core.bitwise_or(roi, mask, circularInput)

      val grayscaleCircularInput = MatUtils.grayscale(circularInput)
      val minMaxResult = Core.minMaxLoc(grayscaleCircularInput)
      val tmp = grayscaleCircularInput.clone()
      val element = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3), new Point(1, 1))
      Imgproc.erode(tmp, tmp, element)
      Imgproc.dilate(tmp, tmp, element)
      Imgproc.threshold(
        tmp,
        tmp,
        math.min(minMaxResult.minVal + thresholdWeight, 225),
        255,
        Imgproc.THRESH_BINARY_INV
      )

      val contours = findContours(tmp)
      val contoursWithArea = contours.map { contour =>
        contour -> Imgproc.contourArea(contour)
      }.filter {
        case (_, area) => area > 0
      }

      if (contoursWithArea.isEmpty) {
        log.debug(s"Circle radius could not be found when using blur radius $currentBlurRadius")
      } else {
        val (maxContour, _) = contoursWithArea.maxBy(_._2)
        val (_, maxContourRadius) = findCircleEnclosingContour(maxContour)
        bestRadius = maxContourRadius
      }

      currentBlurRadius += 1
    }

    bestRadius = math.min(bestRadius, maxRadius)

    if (bestRadius == maxRadius) {
      log.warn(s"The best found circle radius ($bestRadius) is the same as the upperbound of the search space")
    }

    bestRadius
  }

  private def findContours(input: Mat, retrievalType: Int = Imgproc.RETR_LIST): Seq[MatOfPoint] = {
    val buffer = new java.util.ArrayList[MatOfPoint]
    Imgproc.findContours(input, buffer, new Mat, retrievalType, Imgproc.CHAIN_APPROX_SIMPLE)
    buffer.asScala.toSeq
  }

  private def findCircleEnclosingContour(contour: MatOfPoint): (Point, Int) = {
    val contour2f = new MatOfPoint2f
    contour.convertTo(contour2f, CvType.CV_32FC2)
    val center = new Point
    val radiusArray = new Array[Float](1)
    Imgproc.minEnclosingCircle(contour2f, center, radiusArray)
    val radius = math.ceil(radiusArray(0)).toInt
    (center, radius)
  }
}
