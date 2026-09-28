package com.example

import com.example.util.FormulaSanitizer
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun formulaSanitizer_removesChineseCharactersAndRestoresWords() {
    val input = "РСФСР — Российская Советская Федеративная Социалисти的发ская Республика"
    val cleaned = FormulaSanitizer.cleanFormulasAndText(input)
    assertEquals("РСФСР — Российская Советская Федеративная Социалистическая Республика", cleaned)
  }

  @Test
  fun formulaSanitizer_cleansFormulasAndLatex() {
    val input = "Температура \$\$T = +3,8\\ ^\\circ\\text{C}\$\$, коэффициент \$\$K = \\frac{R}{E_0} \\approx 1,35\$\$"
    val cleaned = FormulaSanitizer.cleanFormulasAndText(input)
    assertTrue("Should contain °C", cleaned.contains("°C"))
    assertTrue("Should contain /", cleaned.contains("R / E₀"))
    assertTrue("Should contain ≈", cleaned.contains("≈"))
    assertFalse("Should not contain LaTeX frac", cleaned.contains("\\frac"))
    assertFalse("Should not contain dollar signs", cleaned.contains("$"))
  }
}
