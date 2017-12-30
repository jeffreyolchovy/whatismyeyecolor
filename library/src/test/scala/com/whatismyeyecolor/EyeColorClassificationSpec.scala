package com.whatismyeyecolor

import java.io.File
import scala.io.Source
import org.opencv.core._
import org.opencv.ml.KNearest

class EyeColorClassificationSpec extends BaseSpec {

  behavior of "EyeColorClassification"

  it should "classify eye color given a trivial training dataset and inputs" in {
    val samples = Seq(
      mkInput(ColorRange.Blue -> 3) -> EyeColors.Blue,
      mkInput(ColorRange.Blue -> 2) -> EyeColors.Blue,
      mkInput(ColorRange.Brown -> 4) -> EyeColors.Brown,
      mkInput(ColorRange.Brown -> 1) -> EyeColors.Brown,
      mkInput(ColorRange.Green -> 5) -> EyeColors.Green
    )

    val model = EyeColorClassification.train(samples)

    val result1 = EyeColorClassification(model, mkInput(ColorRange.Blue -> 4))
    result1.scoredColors should have size (1)
    result1.scoredColors should contain (EyeColors.Blue, 1)

    val result2 = EyeColorClassification(model, mkInput(ColorRange.Brown -> 3))
    result2.scoredColors should have size (1)
    result2.scoredColors should contain (EyeColors.Brown, 1)

    val result3 = EyeColorClassification(model, mkInput(ColorRange.Green -> 4))
    result3.scoredColors should have size (1)
    result3.scoredColors should contain (EyeColors.Green, 1)
  }

  it should "classify eye color given a trained dataset and actual inputs" in {
    val samples = mkInputsFromDataFile(fileFromClasspath("/data/celeb-eye-colors.csv"))
    val model = EyeColorClassification.train(samples)

    val result1 = detectEyeColors(model, resourcePath = "/images/lena.png")
    result1 should have size (1)
    result1 should contain (EyeColors.Brown)

    val result2 = detectEyeColors(model, resourcePath = "/images/stock-male-face.jpg")
    result2 should have size (1)
    result2 should contain (EyeColors.Blue)

    val result3 = detectEyeColors(model, resourcePath = "/images/stock-female-face.jpg")
    result3 should contain atLeastOneOf (EyeColors.Brown, EyeColors.Hazel)

    val result4 = detectEyeColors(model, resourcePath = "/images/stock-female-face-2.jpg")
    result4 should have size (1)
    result4 should contain (EyeColors.Blue)

    val result5 = detectEyeColors(model, resourcePath = "/images/stock-female-face-3.jpg")
    result5 should have size (1)
    result5 should contain (EyeColors.Green)
  }

  private def mkInput(inputs: (ColorRange, Int)*): Map[ColorRange, Int] = {
    val givenRanges = Map(inputs: _*)
    ColorRange.AllRanges.map { range =>
      range -> givenRanges.getOrElse(range, 0)
    }(scala.collection.breakOut)
  }

  private def mkInputsFromDataFile(file: File): Seq[(Map[ColorRange, Int], EyeColors.EyeColor)] = {
    val sortedColorRanges = ColorRange.AllRanges.toSeq.sortBy(_.name)
    Source.fromFile(file).getLines.toSeq.map { line =>
      line.split(',').map(_.toInt) match {
        case Array(eyeColorId, colorRangeValues @ _*) =>
          val key = sortedColorRanges.zip(colorRangeValues).toMap[ColorRange, Int]
          val value = EyeColors(eyeColorId)
          key -> value
      }
    }
  }

  private def mkInputsFromImage(input: File): Seq[Map[ColorRange, Int]] = {
    val faceClassifier = fileFromClasspath("/lbpcascades/lbpcascade_frontalface_improved.xml")
    val eyeClassifier = fileFromClasspath("/haarcascades/haarcascade_eye.xml")
    val inputMat = MatUtils.fromFile(input)
    val resized = MatUtils.resize(inputMat, cols = 600)
    val face = ClassifierDetection.largest(resized, faceClassifier)
    val eyes = ClassifierDetection.largestN(face, eyeClassifier, n = 2)
    eyes.map(detectColorPresence(_, numColors = 128))
  }

  private def detectColorPresence(inputMat: Mat, numColors: Int): Map[ColorRange, Int] = {
    val (pupilCenter, pupilRadius, irisRadius) = IrisDetection(inputMat)
    val results = ColorDetection(inputMat, pupilCenter, pupilRadius, irisRadius, numColors).toSeq
    results.map { result =>
      result.colorRange -> result.percent
    }(scala.collection.breakOut)
  }

  private def detectEyeColors(model: KNearest, resourcePath: String): Set[EyeColors.EyeColor] = {
    mkInputsFromImage(fileFromClasspath(resourcePath))
      .map(EyeColorClassification(model, _, k = 5))
      .map(_.bestGuess)(scala.collection.breakOut)
  }
}
