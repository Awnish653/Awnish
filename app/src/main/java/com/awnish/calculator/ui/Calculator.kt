package com.awnish.calculator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(passwordHash: String, onVaultUnlock: () -> Unit, onChangePassword: () -> Unit) {
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("0") }
    var lastAnswer by rememberSaveable { mutableStateOf(0.0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var scientificOpen by rememberSaveable { mutableStateOf(false) }
    var degrees by rememberSaveable { mutableStateOf(true) }
    val clip = LocalClipboardManager.current

    fun append(value: String) { error = null; expression += value }
    fun clearAll() { expression = ""; result = "0"; error = null }
    fun deleteLast() { if (expression.isNotEmpty()) expression = expression.dropLast(1); error = null }

    fun calculate() {
        val trimmed = expression.trim()
        if (trimmed.isNotEmpty() && sha256(trimmed) == passwordHash) {
            clearAll(); onVaultUnlock(); return
        }
        if (trimmed == "1234+1234" || trimmed == "1234 + 1234") {
            clearAll(); onChangePassword(); return
        }
        if (trimmed.isEmpty()) return
        try {
            val value = ExpressionParser(trimmed, degrees, lastAnswer).parse()
            val formatted = formatNumber(value)
            lastAnswer = value
            result = formatted
            history = (listOf(trimmed + " = " + formatted) + history).take(50)
            error = null
        } catch (e: IllegalArgumentException) {
            error = e.message ?: "Invalid expression"
        } catch (_: Exception) {
            error = "Could not calculate this expression"
        }
    }

    val scientificKeys = listOf(
        listOf("sin", "cos", "tan", "asin"),
        listOf("acos", "atan", "ln", "log"),
        listOf("√", "∛", "abs", "exp"),
        listOf("x²", "xʸ", "!", "π")
    )
    val numberKeys = listOf(
        listOf("AC", "⌫", "(", ")"),
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf("±", "0", ".", "=")
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AWNISH", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { degrees = !degrees }) { Text(if (degrees) "DEG" else "RAD") }
            TextButton(onClick = { historyOpen = !historyOpen }) { Text("History") }
        }

        if (historyOpen) {
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                if (history.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No calculations yet") }
                } else {
                    LazyColumn(Modifier.padding(8.dp)) {
                        items(history) { item ->
                            Text(
                                item,
                                modifier = Modifier.fillMaxWidth().combinedClickable(
                                    onClick = { expression = item.substringBefore(" = "); error = null },
                                    onLongClick = { clip.setText(AnnotatedString(item)) }
                                ).padding(7.dp),
                                textAlign = TextAlign.End,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(expression.ifEmpty { "0" }, Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(targetState = result, modifier = Modifier.weight(1f), label = "calculator_result") { value ->
                Text(value, Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp), maxLines = 2)
            }
            IconButton(onClick = { clip.setText(AnnotatedString(result)) }) { Icon(Icons.Default.ContentCopy, "Copy result") }
        }
        error?.let { Text(it, Modifier.fillMaxWidth(), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.error, maxLines = 2) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scientificOpen = !scientificOpen }, modifier = Modifier.weight(1f)) {
                Text(if (scientificOpen) "Scientific  ↑" else "Scientific  ↓")
            }
            OutlinedButton(onClick = {
                if (result != "0" || lastAnswer != 0.0) { expression = "ans"; error = null }
            }, modifier = Modifier.weight(1f)) { Text("Ans") }
        }

        if (scientificOpen) {
            scientificKeys.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        FilledTonalButton(
                            onClick = {
                                when (key) {
                                    "sin", "cos", "tan", "asin", "acos", "atan", "ln", "log", "abs", "exp" -> append(key + "(")
                                    "√" -> append("sqrt(")
                                    "∛" -> append("cbrt(")
                                    "x²" -> append("^2")
                                    "xʸ" -> append("^")
                                    "!" -> append("!")
                                    "π" -> append("pi")
                                    else -> append(key)
                                }
                            },
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) { Text(key, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }

        numberKeys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "AC" -> clearAll()
                                "⌫" -> deleteLast()
                                "±" -> if (expression.isBlank()) append("-") else append("(-1)*")
                                "=" -> calculate()
                                else -> append(when (key) {
                                    "×" -> "*"
                                    "÷" -> "/"
                                    "−" -> "-"
                                    else -> key
                                })
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                    ) {
                        if (key == "⌫") Icon(Icons.Default.Backspace, "Backspace")
                        else Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("=", style = MaterialTheme.typography.titleLarge)
        }
    }
}

private class ExpressionParser(
    private val source: String,
    private val degrees: Boolean,
    private val ans: Double
) {
    private var pos = 0

    fun parse(): Double {
        skipSpaces()
        if (pos == source.length) fail("Enter an expression")
        val value = parseExpression()
        skipSpaces()
        if (pos != source.length) fail("Unexpected character '" + source[pos] + "'")
        checkFinite(value)
        return value
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            when {
                take('+') -> value += parseTerm()
                take('-') -> value -= parseTerm()
                else -> return value
            }
            checkFinite(value)
        }
    }

    private fun parseTerm(): Double {
        var value = parseUnary()
        while (true) {
            skipSpaces()
            when {
                take('*') || take('×') -> value *= parseUnary()
                take('/') || take('÷') -> {
                    val divisor = parseUnary()
                    if (abs(divisor) < 1e-15) fail("Cannot divide by zero")
                    value /= divisor
                }
                startsImplicitMultiplication() -> value *= parseUnary()
                else -> return value
            }
            checkFinite(value)
        }
    }

    private fun parseUnary(): Double {
        skipSpaces()
        return when {
            take('+') -> parseUnary()
            take('-') -> -parseUnary()
            else -> parsePower()
        }
    }

    private fun parsePower(): Double {
        var value = parsePostfix()
        skipSpaces()
        if (take('^')) value = value.pow(parseUnary())
        checkFinite(value)
        return value
    }

    private fun parsePostfix(): Double {
        var value = parsePrimary()
        while (true) {
            skipSpaces()
            when {
                take('%') -> value /= 100.0
                take('!') -> value = factorial(value)
                else -> return value
            }
            checkFinite(value)
        }
    }

    private fun parsePrimary(): Double {
        skipSpaces()
        if (take('(')) {
            val value = parseExpression()
            expect(')')
            return value
        }
        if (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) return parseNumber()

        val nameStart = pos
        while (pos < source.length && source[pos].isLetter()) pos++
        if (nameStart == pos) fail("Expected a number, constant, or function")
        val name = source.substring(nameStart, pos).lowercase()

        return when (name) {
            "pi" -> Math.PI
            "e" -> Math.E
            "ans" -> ans
            else -> {
                expect('(')
                val argument = parseExpression()
                expect(')')
                applyFunction(name, argument)
            }
        }
    }

    private fun parseNumber(): Double {
        val start = pos
        var hasDigits = false
        while (pos < source.length && source[pos].isDigit()) { hasDigits = true; pos++ }
        if (pos < source.length && source[pos] == '.') {
            pos++
            while (pos < source.length && source[pos].isDigit()) { hasDigits = true; pos++ }
        }
        if (!hasDigits) fail("Invalid number")

        if (pos < source.length && (source[pos] == 'e' || source[pos] == 'E')) {
            val exponentStart = pos
            pos++
            if (pos < source.length && (source[pos] == '+' || source[pos] == '-')) pos++
            val exponentDigits = pos
            while (pos < source.length && source[pos].isDigit()) pos++
            if (exponentDigits == pos) { pos = exponentStart; fail("Invalid scientific notation") }
        }

        return source.substring(start, pos).toDoubleOrNull() ?: fail("Invalid number")
    }

    private fun applyFunction(name: String, raw: Double): Double {
        val angle = if (degrees) Math.toRadians(raw) else raw
        return when (name) {
            "sin" -> sin(angle)
            "cos" -> cos(angle)
            "tan" -> {
                if (abs(cos(angle)) < 1e-12) fail("tan is undefined at this angle")
                tan(angle)
            }
            "asin" -> {
                if (raw !in -1.0..1.0) fail("asin domain is −1 to 1")
                val value = asin(raw)
                if (degrees) Math.toDegrees(value) else value
            }
            "acos" -> {
                if (raw !in -1.0..1.0) fail("acos domain is −1 to 1")
                val value = acos(raw)
                if (degrees) Math.toDegrees(value) else value
            }
            "atan" -> {
                val value = atan(raw)
                if (degrees) Math.toDegrees(value) else value
            }
            "sqrt" -> if (raw >= 0) sqrt(raw) else fail("√ needs a non-negative value")
            "cbrt" -> cbrt(raw)
            "ln" -> if (raw > 0) ln(raw) else fail("ln needs a positive value")
            "log" -> if (raw > 0) log10(raw) else fail("log needs a positive value")
            "abs" -> abs(raw)
            "exp" -> exp(raw)
            else -> fail("Unknown function: " + name)
        }
    }

    private fun factorial(value: Double): Double {
        if (value < 0 || value > 170 || value != floor(value)) fail("Factorial needs a whole number from 0 to 170")
        var result = 1.0
        var i = 2
        while (i <= value.toInt()) { result *= i; i++ }
        return result
    }

    private fun startsImplicitMultiplication(): Boolean {
        skipSpaces()
        if (pos >= source.length) return false
        val ch = source[pos]
        return ch == '(' || ch == '.' || ch.isDigit() || ch.isLetter()
    }

    private fun checkFinite(value: Double) {
        if (!value.isFinite()) fail("Result is too large or undefined")
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
    private fun skipSpaces() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
    private fun take(ch: Char): Boolean {
        skipSpaces()
        if (pos < source.length && source[pos] == ch) { pos++; return true }
        return false
    }
    private fun expect(ch: Char) { if (!take(ch)) fail("Missing '" + ch + "'") }
}

private fun formatNumber(value: Double): String {
    if (!value.isFinite()) throw IllegalArgumentException("Invalid result")
    if (abs(value) < 1e-12) return "0"
    return BigDecimal.valueOf(value).setScale(12, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

private fun sha256(value: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}