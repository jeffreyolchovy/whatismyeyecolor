package com.whatismyeyecolor.cli

import java.io.{File, FileNotFoundException}
import java.net.URL
import org.rogach.scallop._

class Conf(args: Seq[String]) extends ScallopConf(args) {

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
  val window = opt[Boolean](short = 'W', default = Some(false), descr = "Display graphical results in discrete windows")

  // subcommands
  val all = new ImageProcSubcommand("all") {
    descr("Perform face, eye, pupil, iris, and eye color detection on a given image")
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
    validateFileExists(faceClassifier)
    validateFileExists(eyeClassifier)
  }

  val resize = new ImageProcSubcommand("resize") {
    descr("Resize an image to the given width")
    val width = opt[Int]("width", default = Some(600))
    val outputBasename = opt[String](noshort = true, descr = "The basename of the resulting output file")
  }

  val recolor = new ImageProcSubcommand("recolor") {
    descr("Reduce the number of colors used in an image to k")
    val numColors = opt[Int](short = 'k', default = Some(16))
    val outputBasename = opt[String](noshort = true, descr = "The basename of the resulting output file")
  }

  val face = new ImageProcSubcommand("face") {
    descr("Perform face detection on a given image")
    val classifierType = choice(
      Seq("haar", "lbp"),
      short = 'C',
      default = Some("lbp"),
      descr = "The type of classifier to use for face detection"
    )
    val classifier = classifierType.map {
      case "lbp" => resolveResource("/lbpcascades/lbpcascade_frontalface_improved.xml")
      case "haar" => resolveResource("/haarcascades/haarcascade_frontalface_default.xml")
      case name => throw new IllegalArgumentException("Illegal classifier type: " + name)
    }
    validateFileExists(classifier)
  }

  val eyes = new ImageProcSubcommand("eyes") {
    descr("Perform eye detection on a given image of a face")
    val classifierType = choice(
      Seq("haar"),
      short = 'C',
      default = Some("haar"),
      descr = "The type of classifier to use for eye detection"
    )
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

  addSubcommand(all)
  addSubcommand(resize)
  addSubcommand(recolor)
  addSubcommand(face)
  addSubcommand(eyes)
  addSubcommand(pupil)
  addSubcommand(iris)
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

  class ImageProcSubcommand(name: String) extends Subcommand(name) {
    val input = opt[File](
      required = true,
      descr = "The local file or classpath resource that will be processed"
    )
    val outputTarget = opt[File](
      short = 'O',
      default = Some(new File(".").getCanonicalFile),
      descr = "The directory where resulting resources will be written"
    )
    validateFileExists(input)
    validateFileIsDirectory(outputTarget)
  }
}
