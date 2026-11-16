package app.veneer.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

/**
 * A small calculator for the app search box: + - * / ^ %, parentheses and decimals.
 * Returns null for anything that isn't a complete arithmetic expression.
 */
object Calculator {

    /** Only treat the query as math when it has an operator and nothing but math characters. */
    fun looksLikeMath(text: String): Boolean {
        val t = text.trim()
        if (t.length < 3 || !t.any { it.isDigit() }) return false
        if (!t.all { it.isDigit() || it in " .,+-*/x×÷^%()" }) return false
        return t.drop(1).any { it in "+-*/x×÷^%" }
    }

    fun evaluate(text: String): Double? {
        val cleaned = text.replace('×', '*').replace('x', '*').replace('÷', '/').replace(',', '.').replace(" ", "")
        if (cleaned.isEmpty()) return null
        return runCatching {
            val parser = Parser(cleaned)
            val value = parser.expression()
            if (parser.pos != cleaned.length || value.isNaN() || value.isInfinite()) null else value
        }.getOrNull()
    }

    /** Formats a result without trailing zeros, e.g. 2.5, 1200, 0.3333333333. */
    fun format(value: Double): String {
        val bd = BigDecimal(value).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        return if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    }

    private class Parser(val s: String) {
        var pos = 0

        fun expression(): Double {
            var v = term()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; v += term() }
                    '-' -> { pos++; v -= term() }
                    else -> return v
                }
            }
            return v
        }

        fun term(): Double {
            var v = power()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; v *= power() }
                    '/' -> { pos++; v /= power() }
                    else -> return v
                }
            }
            return v
        }

        fun power(): Double {
            val base = unary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                return base.pow(power())
            }
            return base
        }

        fun unary(): Double {
            if (pos < s.length && s[pos] == '-') { pos++; return -unary() }
            if (pos < s.length && s[pos] == '+') { pos++; return unary() }
            var v = primary()
            while (pos < s.length && s[pos] == '%') { pos++; v /= 100.0 }
            return v
        }

        fun primary(): Double {
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = expression()
                require(pos < s.length && s[pos] == ')')
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start)
            return s.substring(start, pos).toDouble()
        }
    }
}
