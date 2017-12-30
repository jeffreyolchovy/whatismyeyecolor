package com.whatismyeyecolor

import org.opencv.core._
import org.opencv.imgproc.Imgproc
import org.opencv.ml.{KNearest, Ml}
import org.slf4j.LoggerFactory
import EyeColors._

object EyeColorClassification {

  type Input = Map[ColorRange, Int]

  val log = LoggerFactory.getLogger(getClass)

  def apply(model: KNearest, input: Input, k: Int = 1): Result = {
    val inputMat = new Mat(1, input.size, CvType.CV_32F)
    for {
      ((_, weight), i) <- weightsByColorRange(input).zipWithIndex
    } {
      inputMat.put(0, i, weight)
    }

    val resultsMat = new Mat
    val neighborsMat = new Mat
    val distancesMat = new Mat
    model.findNearest(inputMat, k, new Mat, neighborsMat, distancesMat)

    val neighbors = for {
      i <- 0 until neighborsMat.rows
      j <- 0 until neighborsMat.cols
      id <- neighborsMat.get(i, j).toSeq
    } yield {
      EyeColors(id.toInt)
    }

    val distances = for {
      i <- 0 until distancesMat.rows
      j <- 0 until distancesMat.cols
      n <- distancesMat.get(i, j).toSeq
    } yield {
      n
    }

    log.debug("The nearest neighbor(s) are " + neighbors.mkString(", "))
    log.debug("The distances to each neighbor are " + distances.mkString("[", ", ", "]"))

    Result(neighbors.groupBy(identity).mapValues(_.length))
  }

  def train(samples: Seq[(Input, EyeColor)]): KNearest = {
    require(samples.nonEmpty, "Samples must not be empty")
    val sampleInputs = samples.map(_._1)
    require(sampleInputs.map(_.keySet.size).toSet.size == 1, "Sample input must be congruent in features")
    val knn = KNearest.create()
    val numRows = samples.size
    val numCols = sampleInputs.head.keySet.size
    val samplesMat = new Mat(numRows, numCols, CvType.CV_32F)
    val labelsMat = new Mat(numRows, 1, CvType.CV_32FC1)
    for {
      ((input, eyeColor), i) <- samples.zipWithIndex
      ((_, weight), j) <- weightsByColorRange(input).zipWithIndex
      _ = if (j == 0) labelsMat.put(i, 0, eyeColor.id) else ()
    } {
      samplesMat.put(i, j, weight)
    }

    knn.train(samplesMat, Ml.ROW_SAMPLE, labelsMat)
    knn
  }

  private def weightsByColorRange(input: Input): Seq[(ColorRange, Int)] = {
    input.toSeq.sortBy(_._1.name)
  }

  case class Result(scoredColors: Map[EyeColor, Int]) {
    val bestGuess: EyeColor = scoredColors.toSeq.maxBy(_._2)._1
  }
}
