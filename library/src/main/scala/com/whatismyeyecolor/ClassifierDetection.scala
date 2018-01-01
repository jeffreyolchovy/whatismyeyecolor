package com.whatismyeyecolor

import java.io.File
import org.opencv.core._
import org.opencv.objdetect.CascadeClassifier
import org.slf4j.LoggerFactory

object ClassifierDetection {

  val log = LoggerFactory.getLogger(getClass)

  def apply(input: Mat, classifierResource: File): Seq[Mat] = {
    ClassifierDetection(input, new CascadeClassifier(classifierResource.getAbsolutePath))
  }

  def apply(input: Mat, classifier: CascadeClassifier): Seq[Mat] = {
    val buffer = new MatOfRect
    classifier.detectMultiScale(input, buffer)
    val detections = buffer.toArray.toSeq
    log.debug(s"Detected ${detections.size} object(s)")
    for {
      rect <- detections
    } yield {
      input.submat(rect.y, rect.y + rect.height, rect.x, rect.x + rect.width)
    }
  }
}
