package com.whatismyeyecolor.training

import java.io.{File, FileNotFoundException}
import java.net.URL
import org.rogach.scallop._

class Conf(args: Seq[String]) extends ScallopConf(args) {

  import Conf._

  banner(
    """
    |Usage: wimec-training [OPTION]...
    |
    |Train the 'What is my eye color' eye color classifier.
    |
    """.stripMargin.trim
  )

  val eyes = new Subcommand("eyes") {
    descr("Detect and extract eye images from a face image")

    val input = opt[File](
      required = true,
      descr = "The local file or classpath resource that will be processed"
    )

    val outputTarget = opt[File](
      short = 'O',
      default = Some(new File(".").getCanonicalFile),
      descr = "The directory where resulting resources will be written"
    )

    val width = opt[Int]("width", default = Some(600))

    val faceClassifierType = choice(
      Seq("haar", "lbp"),
      short = 'F',
      default = Some("lbp"),
      descr = "The type of classifier to use for face detection"
    )

    val faceClassifier = faceClassifierType.map {
      case "lbp" => resolveResource("/lbpcascades/lbpcascade_frontalface_improved.xml")
      case "haar" => resolveResource("/haarcascades/haarcascade_frontalface_default.xml")
      case name => throw new IllegalArgumentException("Illegal face classifier type: " + name)
    }

    val eyeClassifierType = choice(
      Seq("haar"),
      short = 'E',
      default = Some("haar"),
      descr = "The type of classifier to use for eye detection"
    )

    val eyeClassifier = eyeClassifierType.map {
      case "haar" => resolveResource("/haarcascades/haarcascade_eye.xml")
      case name => throw new IllegalArgumentException("Illegal eye classifier type: " + name)
    }

    validateFileExists(input)
    validateFileExists(faceClassifier)
    validateFileExists(eyeClassifier)
  }

  val colors = new Subcommand("colors") {
    descr("Detect and report eye color and color quantities in eye images")

    val input = opt[File](
      required = true,
      descr = "The local file or classpath resource that will be processed"
    )

    val outputTarget = opt[File](
      short = 'O',
      default = Some(new File(".").getCanonicalFile),
      descr = "The directory where resulting resources will be written"
    )

    val numColors = opt[Int](short = 'k', default = Some(128))

    validateFileExists(input)
  }

  addSubcommand(eyes)
  addSubcommand(colors)

  errorMessageHandler = { msg: String =>
    println(msg)
    printHelp()
    throw new RuntimeException("Please review program arguments before continuing: " + msg)
  }

  verify()
}

object Conf {

  implicit val fileConverter = singleArgConverter[File](resolveResource _)

  private def resolveResource(resourceName: String): File = {
    getClass.getResource(resourceName) match {
      case null if resourceName.startsWith("/") => new File(resourceName)
      case url: URL => new File(url.toURI.getPath)
      case _ => throw new FileNotFoundException(resourceName)
    }
  }
}
