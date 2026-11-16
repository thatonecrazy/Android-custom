package app.veneer

import app.veneer.util.Calculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    private fun calc(text: String) = Calculator.evaluate(text)?.let(Calculator::format)

    @Test
    fun arithmetic() {
        assertEquals("40", calc("12*3+4"))
        assertEquals("14", calc("2 + 3 × 4"))
        assertEquals("20", calc("(2+3)x4"))
        assertEquals("2.5", calc("5 ÷ 2"))
        assertEquals("1024", calc("2^10"))
        assertEquals("-3", calc("-1-2"))
        assertEquals("15", calc("150*10%"))
        assertEquals("0.333333333333", calc("1/3"))
        assertEquals("3.5", calc("3,5*1"))
    }

    @Test
    fun rejectsNonsense() {
        assertNull(calc("2+"))
        assertNull(calc("1/0"))
        assertNull(calc("(1+2"))
        assertNull(calc(""))
    }

    @Test
    fun onlyMathLooksLikeMath() {
        assertTrue(Calculator.looksLikeMath("12*3"))
        assertTrue(Calculator.looksLikeMath("(4 + 4) / 2"))
        assertFalse(Calculator.looksLikeMath("maps"))
        assertFalse(Calculator.looksLikeMath("2048"))
        assertFalse(Calculator.looksLikeMath("-5"))
        assertFalse(Calculator.looksLikeMath("x"))
    }
}
