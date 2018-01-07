package com.whatismyeyecolor
package training

import java.io.{File, FileWriter}
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal
import org.opencv.core.Core
import org.slf4j.LoggerFactory

object Client {

  private val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    try {
      log.info("Attempting to load native library " + Core.NATIVE_LIBRARY_NAME)
      System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
    } catch {
      case _: UnsatisfiedLinkError =>
        log.error("Unable to load library " + Core.NATIVE_LIBRARY_NAME)
        sys.exit(1)
    }

    val conf = try {
      new Conf(args)
    } catch {
      case NonFatal(e) =>
        log.error("Error parsing arguments", e)
        sys.exit(1)
    }

    try {
      conf.subcommand match {
        case Some(command @ conf.eyes) =>
          extractEyes(
            command.input(),
            command.outputTarget(),
            command.width(),
            command.faceClassifier(),
            command.eyeClassifier()
          )

        case Some(command @ conf.colors) =>
          detectColors(
            command.input(),
            command.outputTarget(),
            command.numColors()
          )

        case Some(_) | None => /* no-op */
      }
    } catch {
      case NonFatal(e) =>
        log.error("Unexpected exception encountered", e)
        sys.exit(2)
    }
  }

  def extractEyes(colorsInputDir: File, colorsOutputDir: File, resizeWidth: Int, faceClassifier: File, eyeClassifier: File): Unit = {
    for {
      eyeColor <- EyeColors.values.toSeq
      eyeColorName = eyeColor.toString.toLowerCase
      inputDir = new File(colorsInputDir, eyeColorName) if inputDir.exists
      outputTarget = new File(colorsOutputDir, eyeColorName)
      _ = outputTarget.mkdirs()
      input <- getFiles(inputDir)
      outputPrefix = getNameWithoutExt(input.getName)
    } {
      for {
        resized <- resizeImage(input, outputTarget, s"$outputPrefix-resized.png", resizeWidth)
        faces <- detectWithClassifier(resized, outputTarget, s"$outputPrefix-face-", faceClassifier, 1)
        _ <- detectWithClassifier(faces.head, outputTarget, s"$outputPrefix-eye-", eyeClassifier, 2)
      } yield {
        resized.delete()
        faces.head.delete()
      }
    }
  }

  def detectColors(colorsInputDir: File, colorsOutputDir: File, numColors: Int): Unit = {
    for {
      eyeColor <- EyeColors.values.toSeq
      eyeColorName = eyeColor.toString.toLowerCase
      inputDir = new File(colorsInputDir, eyeColorName) if inputDir.exists
      outputTarget = new File(colorsOutputDir, eyeColorName)
      _ = outputTarget.mkdirs()
      output = new File(outputTarget, s"results-k-$numColors.csv")
      _ = output.delete() && output.createNewFile()
      input <- getFiles(inputDir)
    } {
      val writer = new FileWriter(output, true)
      for {
        entry <- detectColors(input, numColors)
      } yield {
        writer.write(entry + "\n")
      }
      writer.close()
    }
  }

  def resizeImage(input: File, outputTarget: File, outputBasename: String, width: Int): Try[File] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val outputMat = MatUtils.resize(inputMat, width)
      val output = MatUtils.toFile(outputMat, new File(outputTarget, outputBasename))
      output
    }
  }

  def detectWithClassifier(input: File, outputTarget: File, outputPrefix: String, classifier: File, numDetections: Int): Try[Seq[File]] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val outputMats = ClassifierDetection.largestN(inputMat, classifier, numDetections)
      val outputs = for {
        (outputMat, i) <- outputMats.zipWithIndex
        output = new File(outputTarget, outputPrefix + i + ".png")
      } yield {
        MatUtils.toFile(outputMat, output)
      }
      if (outputs.size < numDetections) {
        throw new RuntimeException(s"Unable to detect $numDetections object(s) using $classifier on $input")
      } else {
        outputs
      }
    }
  }

  def detectColors(input: File, numColors: Int): Try[String] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val (pupilCenter, pupilRadius, irisRadius) = IrisDetection(inputMat)
      val results = ColorDetection(inputMat, pupilCenter, pupilRadius, irisRadius, numColors)
        .toList
        .sortBy(_.colorRange.name)
      val columns = input.getAbsolutePath :: results.map(_.percent.toString)
      columns.mkString(",")
    }
  }

  private def getFiles(dir: File): Seq[File] = {
    dir.listFiles().toSeq.filter { file =>
      val fileName = file.getName
      file.isFile && !fileName.startsWith(".")
    }
  }

  private def getNameWithoutExt(fileName: String): String = {
    fileName.lastIndexOf('.') match {
      case -1 => fileName
      case n => fileName.substring(0, n)
    }
  }
}
