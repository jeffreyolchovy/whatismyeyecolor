package com.whatismyeyecolor
package cli

import java.io.File
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
      (conf.subcommand match {
        case Some(command @ conf.all) =>
          executeAll(
            command.input(),
            command.outputTarget(),
            command.width(),
            command.faceClassifier(),
            command.eyeClassifier(),
            command.numColors()
          )

        case Some(command @ conf.resize) =>
          val input = command.input()
          val outputBasename = command.outputBasename.getOrElse("resized-" + input.getName)
          resizeImage(input, command.outputTarget(), outputBasename, command.width())

        case Some(command @ conf.recolor) =>
          val input = command.input()
          val outputBasename = command.outputBasename.getOrElse("recolored-" + input.getName)
          recolorImage(input, command.outputTarget(), outputBasename, command.numColors())

        case Some(command @ conf.face) =>
          detectWithClassifier(command.input(), command.outputTarget(), "detected-face-", command.classifier(), 1)

        case Some(command @ conf.eyes) =>
          detectWithClassifier(command.input(), command.outputTarget(), "detected-eye-", command.classifier(), 2)

        case Some(command @ conf.pupil) =>
          detectPupil(command.input(), new File(command.outputTarget(), "detected-pupil.png"))

        case Some(command @ conf.iris) =>
          detectIris(command.input(), new File(command.outputTarget(), "detected-iris.png"))

        case Some(command @ conf.colors) =>
          detectColors(command.input(), command.outputTarget(), "detected-colors-", command.numColors())

        case Some(_) | None =>
          conf.printHelp()
          Failure(new IllegalArgumentException("A valid subcommand is required"))
      }) match {
        case Success(result) if conf.window() => result.display()
        case Success(result) => /* no-op */
        case Failure(e) => throw e
      }
    } catch {
      case NonFatal(e) =>
        log.error("Unexpected exception encountered", e)
        sys.exit(2)
    }
  }

  def executeAll(input: File, outputTarget: File, width: Int, faceClassifier: File, eyeClassifier: File, numColors: Int): Try[Result] = {
    for {
      resizeResult <- resizeImage(input, outputTarget, "resized-" + input.getName, width)
      resizeOutput = resizeResult.resources.head
      detectFaceResult <- detectWithClassifier(resizeOutput, outputTarget, "detected-face-", faceClassifier, 1)
      detectFaceOutput = detectFaceResult.resources.head
      detectEyeResult <- detectWithClassifier(detectFaceOutput, outputTarget, "detected-eye-", eyeClassifier, 2)
      detectEyeOutput = detectEyeResult.resources
    } yield {
      val detectColorsOutputs = for {
        (colorInput, i) <- detectEyeOutput.zipWithIndex
        detectColorsResult = detectColors(colorInput, outputTarget, s"detected-color-$i-", numColors)
        detectColorsOutput <- detectColorsResult.map(_.resources).getOrElse(Seq.empty[File])
      } yield {
        detectColorsOutput
      }
      val outputs = Seq(resizeOutput, detectFaceOutput) ++ detectEyeOutput ++ detectColorsOutputs
      Result(outputs: _*)
    }
  }

  def resizeImage(input: File, outputTarget: File, outputBasename: String, width: Int): Try[Result] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val outputMat = MatUtils.resize(inputMat, width)
      val output = MatUtils.toFile(outputMat, new File(outputTarget, outputBasename))
      Result(output)
    }
  }

  def recolorImage(input: File, outputTarget: File, outputBasename: String, numColors: Int): Try[Result] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val outputMat = MatUtils.quantizeColors(inputMat, k = numColors)
      val output = MatUtils.toFile(outputMat, new File(outputTarget, outputBasename))
      Result(output)
    }
  }

  def detectWithClassifier(input: File, outputTarget: File, outputPrefix: String, classifier: File, numDetections: Int): Try[Result] = {
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
        Result(outputs: _*)
      }
    }
  }

  def detectPupil(input: File, output: File): Try[Result] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val (center, radius) = PupilDetection(inputMat)
      val outputMat = PupilDetection.mark(inputMat, center, radius)
      Result(MatUtils.toFile(outputMat, output))
    }
  }

  def detectIris(input: File, output: File): Try[Result] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val (pupilCenter, pupilRadius, irisRadius) = IrisDetection(inputMat)
      val outputMat = IrisDetection.mark(inputMat, pupilCenter, pupilRadius, irisRadius)
      Result(MatUtils.toFile(outputMat, output))
    }
  }

  def detectColors(input: File, outputTarget: File, outputPrefix: String, numColors: Int): Try[Result] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val (pupilCenter, pupilRadius, irisRadius) = IrisDetection(inputMat)
      val irisOutputMat = IrisDetection.mark(inputMat, pupilCenter, pupilRadius, irisRadius)
      val irisOutput = MatUtils.toFile(irisOutputMat, new File(outputTarget, outputPrefix + "iris.png"))
      val outputs = for {
        result <- ColorDetection(inputMat, pupilCenter, pupilRadius, irisRadius, numColors).toSeq
        color = result.colorRange
        area = result.area if area > 0
        outputMat = result.mat
        output = new File(outputTarget, outputPrefix + color.name.toLowerCase + ".png")
      } yield {
        MatUtils.toFile(outputMat, output)
      }
      Result(outputs :+ irisOutput: _*)
    }
  }

  case class Result(resources: File*) {
    import javax.imageio.ImageIO
    import javax.swing.{JFrame, JLabel, ImageIcon}
    def display(): Unit = {
      for (resource <- resources) {
        val buf = ImageIO.read(resource)
        val frame = new JFrame
        frame.getContentPane.add(new JLabel(new ImageIcon(buf)))
        frame.pack()
        frame.setVisible(true)
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
      }
    }
  }
}
