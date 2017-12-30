package com.whatismyeyecolor

import java.io.File
import org.opencv.core._

class MatUtilsSpec extends BaseSpec {

  behavior of "MatUtils"

  val ClasspathResource16x16 = fileFromClasspath("/images/misc/16x16-red.png")

  it should "provide utilities for loading Mat instances from the classpath" in {
    val mat = MatUtils.fromFile(ClasspathResource16x16)
    mat.size shouldBe new Size(16, 16)
  }

  it should "provide utilities for saving Mat instances to the file system" in {
    val mat = MatUtils.fromFile(ClasspathResource16x16)
    val tmp = File.createTempFile("matutils-", ".png")
    MatUtils.toFile(mat, tmp)
    tmp.exists shouldBe true
  }

  it should "provide utilities for loading Mat instances from the file system" in {
    val matFromClasspath = MatUtils.fromFile(ClasspathResource16x16)
    val tmp = File.createTempFile("matutils-", ".png")
    MatUtils.toFile(matFromClasspath, tmp)
    val matFromFs = MatUtils.fromFile(tmp)
    matFromClasspath.size shouldBe matFromFs.size
  }

  it should "provide utilities for resizing Mat instances to a given width" in {
    val mat = MatUtils.fromFile(ClasspathResource16x16)
    val resizedMat = MatUtils.resize(mat, cols = 8)
    resizedMat.size shouldBe new Size(8, 8)
  }

  it should "no-op if asked to resize a Mat instance that is less than or equal to the given width" in {
    val mat = MatUtils.fromFile(ClasspathResource16x16)
    val resizedMat = MatUtils.resize(mat, cols = 16)
    resizedMat shouldBe mat
  }

  it should "provide utilities for converting Mat instances to grayscale" in {
    val mat = MatUtils.fromFile(ClasspathResource16x16)
    val grayscaleMat = MatUtils.grayscale(mat)
    Core.countNonZero(grayscaleMat) shouldBe 16 * 16
  }

  it should "provide utilities for inverting the color of a Mat instance" in {
    val mat = MatUtils.fromFile(ClasspathResource16x16)
    val grayscaleMat = MatUtils.grayscale(mat)
    val invertedGrayscaleMat = MatUtils.invert(grayscaleMat)
    Core.countNonZero(invertedGrayscaleMat) shouldBe 0
  }
}
