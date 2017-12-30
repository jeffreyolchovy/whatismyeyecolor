package com.whatismyeyecolor

class ColorDetectionSpec extends BaseSpec {

  behavior of "ColorDetection"

  it should "detect brown colors (ignoring true reds)" in {
    val mat = MatUtils.fromFile(fileFromClasspath("/images/misc/9x9-red-on-brown.png"))
    val result = ColorDetection.detectColorPresence(mat, ColorRange.Brown)
    result.area shouldBe 36
  }

  it should "detect brown colors (ignoring grays)" in {
    val mat = MatUtils.fromFile(fileFromClasspath("/images/misc/9x9-gray-on-brown.png"))
    val result = ColorDetection.detectColorPresence(mat, ColorRange.Brown)
    result.area shouldBe 36
  }

  it should "detect blue colors (ignoring browns)" in {
    val mat = MatUtils.fromFile(fileFromClasspath("/images/misc/9x9-brown-on-blue.png"))
    val result = ColorDetection.detectColorPresence(mat, ColorRange.Blue)
    result.area shouldBe 36
  }

  it should "detect green colors (ignoring browns)" in {
    val mat = MatUtils.fromFile(fileFromClasspath("/images/misc/9x9-brown-on-green.png"))
    val result = ColorDetection.detectColorPresence(mat, ColorRange.Green)
    result.area shouldBe 36
  }

  it should "detect gray colors (ignoring browns)" in {
    val mat = MatUtils.fromFile(fileFromClasspath("/images/misc/9x9-brown-on-gray.png"))
    val result = ColorDetection.detectColorPresence(mat, ColorRange.CoolGray)
    result.area shouldBe 36
  }

  it should "detect amber colors (ignoring browns)" in {
    val mat = MatUtils.fromFile(fileFromClasspath("/images/misc/9x9-brown-on-amber.png"))
    val result = ColorDetection.detectColorPresence(mat, ColorRange.Amber)
    result.area shouldBe 36
  }
}
