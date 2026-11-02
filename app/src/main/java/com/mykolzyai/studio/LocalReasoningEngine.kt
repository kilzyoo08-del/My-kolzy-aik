package com.mykolzyai.studio

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Локальный детерминированный решатель арифметики.
 * Это не языковая модель: он честно решает выражения, которые может разобрать.
 */
object LocalReasoningEngine {
    data class Result(val answer: String, val steps: List<String>)

    fun solveMathQuestion(input: String): Result? {
        val expression = extractExpression(input) ?: return null
        return try {
            val parser = Parser(expression)
            val value = parser.parse()
            val formatted = value.stripTrailingZeros().toPlainString()
            Result(
                answer = formatted,
                steps = parser.steps.toList() + "Получили результат: $formatted."
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun extractExpression(input: String): String? {
        val cleaned = input.trim()
            .replace(',', '.')
            .replace(Regex("(?i)^(?:посчитай|вычисли|реши|сколько будет|calculate|solve)\\s*"), "")
            .replace("?", "")
            .trim()
        if (cleaned.length !in 1..120) return null
        if (!cleaned.any { it.isDigit() }) return null
        if (cleaned.any { !(it.isDigit() || it in "+-*/(). " || it == '.') }) return null
        if (!cleaned.any { it in "+-*/" }) return null
        return cleaned
    }

    private class Parser(private val source: String) {
        private var index = 0
        val steps = mutableListOf<String>()

        fun parse(): BigDecimal {
            val result = expression()
            skipSpaces()
            require(index == source.length) { "Лишние символы" }
            return result
        }

        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '+' -> {
                        index++
                        val right = term()
                        val old = value
                        value = value.add(right)
                        steps += "${fmt(old)} + ${fmt(right)} = ${fmt(value)}"
                    }
                    '-' -> {
                        index++
                        val right = term()
                        val old = value
                        value = value.subtract(right)
                        steps += "${fmt(old)} − ${fmt(right)} = ${fmt(value)}"
                    }
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '*' -> {
                        index++
                        val right = factor()
                        val old = value
                        value = value.multiply(right)
                        steps += "${fmt(old)} × ${fmt(right)} = ${fmt(value)}"
                    }
                    '/' -> {
                        index++
                        val right = factor()
                        require(right.compareTo(BigDecimal.ZERO) != 0) { "Деление на ноль" }
                        val old = value
                        value = value.divide(right, MathContext(16, RoundingMode.HALF_UP)).stripTrailingZeros()
                        steps += "${fmt(old)} ÷ ${fmt(right)} ≈ ${fmt(value)}"
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            skipSpaces()
            return when (peek()) {
                '+' -> { index++; factor() }
                '-' -> { index++; factor().negate() }
                '(' -> {
                    index++
                    val value = expression()
                    skipSpaces()
                    require(peek() == ')') { "Нет закрывающей скобки" }
                    index++
                    value
                }
                else -> number()
            }
        }

        private fun number(): BigDecimal {
            skipSpaces()
            val start = index
            var dots = 0
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                if (source[index] == '.') dots++
                require(dots <= 1) { "Неверное число" }
                index++
            }
            require(index > start) { "Ожидалось число" }
            return source.substring(start, index).toBigDecimal()
        }

        private fun skipSpaces() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun peek(): Char? = source.getOrNull(index)
        private fun fmt(value: BigDecimal) = value.stripTrailingZeros().toPlainString()
    }
}
