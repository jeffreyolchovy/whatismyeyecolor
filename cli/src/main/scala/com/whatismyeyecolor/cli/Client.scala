package com.whatismyeyecolor
package cli

import java.io.{File, FileNotFoundException}
import java.net.URL
import java.nio.file.Paths
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal
import org.opencv.core.Core
import org.rogach.scallop._
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
        case Some(command @ conf.exec) =>
          execute(
            command.input(),
            command.outputTarget(),
            command.width(),
            command.faceClassifier(),
            command.eyeClassifier()
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
          detectWithClassifier(command.input(), command.outputTarget(), "detected-face-", command.classifier())

        case Some(command @ conf.eyes) =>
          detectWithClassifier(command.input(), command.outputTarget(), "detected-eye-", command.classifier())

        case Some(command @ conf.pupil) =>
          detectPupil(command.input(), new File(command.outputTarget(), "detected-pupil.png"))

        case Some(command @ conf.iris) =>
          detectIris(command.input(), new File(command.outputTarget(), "detected-iris.png"))

        case Some(command @ conf.colors) =>
          detectColors(command.input(), command.outputTarget(), "detected-colors-")

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

  def execute(input: File, outputTarget: File, width: Int, faceClassifier: File, eyeClassifier: File): Try[Result] = {
    for {
      resizeResult <- resizeImage(input, outputTarget, "resized-" + input.getName, width)
      resizeOutput = resizeResult.resources.head
      detectFaceResult <- detectWithClassifier(resizeOutput, outputTarget, "detected-face-", faceClassifier)
      detectFaceOutput = detectFaceResult.resources.head
      detectEyeResult <- detectWithClassifier(detectFaceOutput, outputTarget, "detected-eye-", eyeClassifier)
      detectEyeOutput = detectEyeResult.resources
    } yield {
      val detectIrisOutputs = for {
        (irisInput, i) <- detectEyeOutput.zipWithIndex
        detectIrisResult = detectIris(irisInput, new File(outputTarget, s"detected-iris-$i.png"))
        detectIrisOutput <- detectIrisResult.map(_.resources).getOrElse(Seq.empty[File])
      } yield {
        detectIrisOutput
      }
      val detectColorsOutputs = for {
        (colorInput, i) <- detectEyeOutput.zipWithIndex
        detectColorsResult = detectColors(colorInput, outputTarget, s"detected-color-$i-")
        detectColorsOutput <- detectColorsResult.map(_.resources).getOrElse(Seq.empty[File])
      } yield {
        detectColorsOutput
      }
      val outputs = Seq(resizeOutput, detectFaceOutput) ++ detectEyeOutput ++ detectColorsOutputs ++ detectIrisOutputs
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

  def detectWithClassifier(input: File, outputTarget: File, outputPrefix: String, classifier: File): Try[Result] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val outputMats = ClassifierDetection(inputMat, classifier)
      val outputs = for {
        (outputMat, i) <- outputMats.zipWithIndex
        output = new File(outputTarget, outputPrefix + i + ".png")
      } yield {
        MatUtils.toFile(outputMat, output)
      }
      Result(outputs: _*)
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

  def detectColors(input: File, outputTarget: File, outputPrefix: String): Try[Result] = {
    Try {
      val inputMat = MatUtils.fromFile(input)
      val (pupilCenter, pupilRadius, irisRadius) = IrisDetection(inputMat)
      val outputs = for {
        result <- ColorDetection(inputMat, pupilCenter, pupilRadius, irisRadius, numColors = 64).toSeq
        color = result.color
        area = result.area if area > 0
        outputMat = result.mat
        output = new File(outputTarget, outputPrefix + color.name + ".png")
      } yield {
        MatUtils.toFile(outputMat, output)
      }
      Result(outputs: _*)
    }
  }

  private def resolveResource(resourceName: String): File = {
    getClass.getResource(resourceName) match {
      case null if resourceName.startsWith("/") => new File(resourceName)
      case url: URL => Paths.get(url.toURI).toFile
      case _ => throw new FileNotFoundException(resourceName)
    }
  }

  implicit val fileConverter = singleArgConverter[File](resolveResource _)

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

  private class Conf(args: Seq[String]) extends ScallopConf(args) {

    import Conf._

    banner(
      """
      |Usage: wimec [GLOBAL_OPTIONS] [SUBCOMMAND] [OPTION]...
      |
      |What is my eye color?
      |
      |Global Options:
      """.stripMargin.trim
    )

    // flags
    val debug = opt[Boolean](short = 'd', default = Some(false))
    val verbose = opt[Boolean](short = 'v', default = Some(false))
    val window = opt[Boolean](short = 'W', default = Some(false))

    // subcommands
    val exec = new ImageProcSubcommand("exec") {
      descr("Perform facial, eye, pupil, iris, and eye color detection on a given image")
      val width = opt[Int]("width", default = Some(600))
      val faceClassifierType = choice(Seq("haar", "lbp"), short = 'F', default = Some("lbp"))
      val faceClassifier = faceClassifierType.map {
        case "lbp" => resolveResource("/lbpcascades/lbpcascade_frontalface_improved.xml")
        case "haar" => resolveResource("/haarcascades/haarcascade_frontalface_default.xml")
        case name => throw new IllegalArgumentException("Illegal face classifier type: " + name)
      }
      val eyeClassifierType = choice(Seq("haar"), short = 'E', default = Some("haar"))
      val eyeClassifier = eyeClassifierType.map {
        case "haar" => resolveResource("/haarcascades/haarcascade_eye.xml")
        case name => throw new IllegalArgumentException("Illegal eye classifier type: " + name)
      }
      validateFileExists(faceClassifier)
      validateFileExists(eyeClassifier)
    }

    val resize = new ImageProcSubcommand("resize") {
      descr("Resize an image to the given width")
      val width = opt[Int]("width", default = Some(600))
      val outputBasename = opt[String](noshort = true)
    }

    val recolor = new ImageProcSubcommand("recolor") {
      descr("Reduce the number of colors used in an image to k")
      val numColors = opt[Int](short = 'k', default = Some(16))
      val outputBasename = opt[String](noshort = true)
    }

    val face = new ImageProcSubcommand("face") {
      descr("Perform facial detection on a given image")
      val classifierType = choice(Seq("haar", "lbp"), short = 'C', default = Some("lbp"))
      val classifier = classifierType.map {
        case "lbp" => resolveResource("/lbpcascades/lbpcascade_frontalface_improved.xml")
        case "haar" => resolveResource("/haarcascades/haarcascade_frontalface_default.xml")
        case name => throw new IllegalArgumentException("Illegal classifier type: " + name)
      }
      validateFileExists(classifier)
    }

    val eyes = new ImageProcSubcommand("eyes") {
      descr("Perform eye detection on a given image of a face")
      val classifierType = choice(Seq("haar"), short = 'C', default = Some("haar"))
      val classifier = classifierType.map {
        case "haar" => resolveResource("/haarcascades/haarcascade_eye.xml")
        case name => throw new IllegalArgumentException("Illegal classifier type: " + name)
      }
      validateFileExists(classifier)
    }

    val pupil = new ImageProcSubcommand("pupil") {
      descr("Perform pupil detection on a given image of an eye")
    }

    val iris = new ImageProcSubcommand("iris") {
      descr("Perform iris detection on a given image of an eye")
    }

    val colors = new ImageProcSubcommand("colors") {
      descr("Perform eye color detection on a given image of an eye")
    }

    addSubcommand(exec)
    addSubcommand(resize)
    addSubcommand(recolor)
    addSubcommand(face)
    addSubcommand(eyes)
    addSubcommand(pupil)
    addSubcommand(iris)
    addSubcommand(colors)

    errorMessageHandler = { msg: String =>
      printHelp()
      throw new IllegalArgumentException(msg)
    }

    verify()
  }

  object Conf {
    class ImageProcSubcommand(name: String) extends Subcommand(name) {
      val input = opt[File]("input", required = true)
      val outputTarget = opt[File]("outputTarget", short = 'O', default = Some(new File(".").getCanonicalFile))
      validateFileExists(input)
      validateFileIsDirectory(outputTarget)
    }
  }
}
