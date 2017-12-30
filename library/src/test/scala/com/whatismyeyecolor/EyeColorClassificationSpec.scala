package com.whatismyeyecolor

class EyeColorClassificationSpec extends BaseSpec {

  behavior of "EyeColorClassification"

  it should "classify eye color given a trivial training dataset and inputs" in {
    val samples = Seq(
      mkInput(ColorRange.Blue -> 3) -> EyeColors.Blue,
      mkInput(ColorRange.Blue -> 2) -> EyeColors.Blue,
      mkInput(ColorRange.Brown -> 4) -> EyeColors.Brown,
      mkInput(ColorRange.Brown -> 1) -> EyeColors.Brown,
      mkInput(ColorRange.Green -> 5) -> EyeColors.Green
    )

    val model = EyeColorClassification.train(samples)

    val result1 = EyeColorClassification(model, mkInput(ColorRange.Blue -> 4))
    result1.scoredColors should have size (1)
    result1.scoredColors should contain (EyeColors.Blue, 1)

    val result2 = EyeColorClassification(model, mkInput(ColorRange.Brown -> 3))
    result2.scoredColors should have size (1)
    result2.scoredColors should contain (EyeColors.Brown, 1)

    val result3 = EyeColorClassification(model, mkInput(ColorRange.Green -> 4))
    result3.scoredColors should have size (1)
    result3.scoredColors should contain (EyeColors.Green, 1)
  }

  private def mkInput(inputs: (ColorRange, Int)*): Map[ColorRange, Int] = {
    val givenRanges = Map(inputs: _*)
    ColorRange.AllRanges.map { range =>
      range -> givenRanges.getOrElse(range, 0)
    }(scala.collection.breakOut)
  }
}
