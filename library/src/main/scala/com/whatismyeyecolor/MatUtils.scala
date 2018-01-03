package com.whatismyeyecolor

import java.io.File
import org.opencv.core._
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory

object MatUtils {

  private val log = LoggerFactory.getLogger(getClass)

  def fromFile(file: File): Mat = {
    log.debug(s"Reading image from $file")
    Imgcodecs.imread(file.getAbsolutePath)
  }

  def toFile(mat: Mat, target: File): File = {
    log.debug(s"Writing image to $target")
    Imgcodecs.imwrite(target.getAbsolutePath, mat)
    target
  }

  def containsPoint(input: Mat, point: Point): Boolean = {
    point.x >= 0 &&
    point.y >= 0 &&
    point.x < input.cols &&
    point.y < input.rows
  }

  def containsRect(input: Mat, rect: Rect): Boolean = {
    rect.x > 0 &&
    rect.y > 0 &&
    rect.x + rect.width < input.cols &&
    rect.y + rect.height < input.rows
  }

  def circleContainsPoint(center: Point, radius: Int, point: Point): Boolean = {
    math.pow(point.x - center.x, 2) +
    math.pow(point.y - center.y, 2) <
    math.pow(radius, 2)
  }

  def resize(input: Mat, cols: Int): Mat = {
    if (input.width <= cols) {
      log.debug(s"Image of width ${input.width} is already less than or equal to the desired size ($cols)")
      input
    } else {
      log.debug(s"Resizing image with size ${input.size} to $cols columns, preserving aspect ratio")
      val output = input.clone()
      val rows = math.ceil((cols * input.height) / input.width.toDouble).toInt
      val newSize = new Size(cols, rows)
      log.debug(s"Resized image has size ${newSize}")
      Imgproc.resize(input, output, newSize)
      output
    }
  }

  def invert(input: Mat): Mat = {
    val output = new Mat(input.rows, input.cols, input.`type`, new Scalar(255, 255, 255))
    Core.subtract(output, input, output)
    output
  }

  def quantizeColors(input: Mat, k: Int, attempts: Int = 5): Mat = {
    val samples = new Mat(input.rows * input.cols, input.channels, CvType.CV_32F)
    for {
      y <- 0 until input.rows
      x <- 0 until input.cols
      z <- 0 until input.channels
    } {
      samples.put(y + x * input.rows, z, input.get(y, x)(z))
    }

    val labels = new Mat
    val centers = new Mat
    Core.kmeans(
      samples,
      k,
      labels,
      new TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, 10000, 0.0001),
      attempts,
      Core.KMEANS_PP_CENTERS,
      centers
    )

    val output = input.clone()
    for {
      y <- 0 until input.rows
      x <- 0 until input.cols
      i = labels.get(y + x * input.rows, 0)(0).toInt
      b = centers.get(i, 0)(0)
      g = centers.get(i, 1)(0)
      r = centers.get(i, 2)(0)
    } {
      output.put(y, x, b, g, r)
    }
    output
  }

  def grayscale(input: Mat): Mat = {
    val output = new Mat
    Imgproc.cvtColor(input, output, Imgproc.COLOR_BGR2GRAY)
    output
  }

  def horizontalGradient(input: Mat): Mat = {
    val output = new Mat
    Imgproc.Sobel(input, output, CvType.CV_16S, 1, 0)
    output
  }

  def verticalGradient(input: Mat): Mat = {
    val output = new Mat
    Imgproc.Sobel(input, output, CvType.CV_16S, 0, 1)
    output
  }

  def distance(input1: Mat, input2: Mat): Mat = {
    require(input1.size == input2.size)
    val output = new Mat(input1.rows, input1.cols, CvType.CV_64F)
    for {
      i <- 0 until input1.rows
      j <- 0 until input1.cols
    } {
      val x = input1.get(i, j)(0)
      val y = input2.get(i, j)(0)
      output.put(i, j, math.sqrt(math.pow(x, 2) + math.pow(y, 2)))
    }
    output
  }

  def findRectEnclosingCircle(center: Point, radius: Int, maxWidth: Int, maxHeight: Int): Rect = {
    val centerX = center.x.toInt
    val centerY = center.y.toInt
    val rectX = math.max(centerX - radius, 0)
    val rectY = math.max(centerY - radius, 0)
    val rectW = math.min(radius * 2, maxWidth - rectX)
    val rectH = math.min(radius * 2, maxHeight - rectY)
    new Rect(rectX, rectY, rectW, rectH)
  }

  def fillPointsOutsideOfCircle(input: Mat, center: Point, radius: Int, data: Double*): Mat = {
    val output = input.clone()
    for {
      i <- 0 until input.rows
      j <- 0 until input.cols
      p = new Point(j, i)
      if !circleContainsPoint(center, radius, p)
    } {
      output.put(i, j, data: _*)
    }
    output
  }
}
