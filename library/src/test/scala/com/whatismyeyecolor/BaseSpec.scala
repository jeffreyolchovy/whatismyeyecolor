package com.whatismyeyecolor

import java.io.File
import org.opencv.core.Core
import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}

trait BaseSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll() = {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
  }

  def fileFromClasspath(resourcePath: String): File = {
    new File(getClass.getResource(resourcePath).toURI.getPath)
  }
}
