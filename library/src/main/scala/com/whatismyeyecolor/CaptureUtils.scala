package com.whatismyeyecolor

import java.io.File
import org.opencv.core.Mat
import org.opencv.videoio.VideoCapture
import org.slf4j.LoggerFactory

object CaptureUtils {

  val log = LoggerFactory.getLogger(getClass)

  // fixme for testing purposes only
  def captureImages(n: Int = 10): Unit = {
    val camera = new VideoCapture(0)
    try {
      if (!camera.isOpened) {
        throw new RuntimeException("Camera has not been opened!")
      } else for (i <- 0 until n) {
        val frame = new Mat
        Thread.sleep(10)
        camera.read(frame)
        MatUtils.toFile(frame, new File(s"/tmp/camera-capture-$i.png"))
      }
    } finally {
      camera.release()
    }
  }
}
