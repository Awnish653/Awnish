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
import kotlin.math.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(passwordHash: String, onVaultUnlock: () -> Unit, onChangePassword: () -> Unit) {
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("0") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var historyOpen by remember { mutableStateOf(false) }
    var degrees by rememberSaveable { mutableStateOf(true) }
    val clip = LocalClipboardManager.current

    fun input(value: String) { error = null; expression += value }
    fun equals() {
        val trimmed = expression.trim()
        if (trimmed.isNotEmpty() && sha256(trimmed) == passwordHash) {
            expression = ""; result = "0"; error = null; onVaultUnlock(); return
        }
        if (trimmed == "1234+1234" || trimmed == "1234 + 1234") {
            expression = ""; result = "0"; error = null; onChangePassword(); return
        }
        try {
            val value = ExpressionParser(expression, degrees).parse()
            val formatted = formatNumber(value)
            result = formatted
            if (expression.isNotBlank()) history = (listOf("@@{expression} = @@{formatted}") + history).take(50)
            error = null
        } catch (e: Exception) { error = e.message ?: "Invalid expression" }
    }

    val keys = listOf(
        listOf("AC", "⌫", "(", ")"),
        listOf("sin", "cos", "tan", "√"),
        listOf("ln", "log", "x²", "xʸ"),
        listOf("π", "e", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AWNISH", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { degrees = !degrees }) { Text(if (degrees) "DEG" else "RAD") }
            TextButton(onClick = { historyOpen = !historyOpen }) { Text("History") }
        }
        if (historyOpen) {
            Card(Modifier.fillMaxWidth().heightIn(max = 125.dp)) {
                LazyColumn(Modifier.padding(8.dp)) {
                    items(history) { item ->
                        Text(item, Modifier.fillMaxWidth().combinedClickable(onClick = { expression = item.substringBefore(" = ") }, onLongClick = { clip.setText(AnnotatedString(item)) }).padding(7.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(expression.ifEmpty { "0" }, Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(targetState = result, modifier = Modifier.weight(1f), label = "result") { value ->
                Text(value, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp))
            }
            IconButton(onClick = { clip.setText(AnnotatedString(result)) }) { Icon(Icons.Default.ContentCopy, "Copy result") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) }

        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "AC" -> { expression = ""; result = "0"; error = null }
                                "⌫" -> { expression = expression.dropLast(1); error = null }
                                "=" -> equals()
                                "sin", "cos", "tan", "ln", "log" -> input("\${key}(")
                                "√" -> input("sqrt(")
                                "x²" -> input("^2")
                                "xʸ" -> input("^")
                                "π" -> input("pi")
                                "e" -> input("e")
                                "±" -> if (expression.isBlank()) input("-") else input("(-1)*")
                                "%" -> input("%")
                                else -> input(key)
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                        colors = if (key == "=") ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        if (key == "⌫") Icon(Icons.Default.Backspace, "Backspace") else Text(key, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private class ExpressionParser(private val source: String, private val degrees: Boolean) {
    private var pos = 0
    fun parse(): Double {
        val value = parseExpression()
        skipSpaces()
        if (pos != source.length) throw IllegalArgumentException("Unexpected character '\${source[pos]}'")
        if (!value.isFinite()) throw IllegalArgumentException("Invalid result")
        return value
    }
    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            value = when { take('+') -> value + parseTerm(); take('-') -> value - parseTerm(); else -> return value }
        }
    }
    private fun parseTerm(): Double {
        var value = parsePower()
        while (true) {
            skipSpaces()
            value = when {
                take('*') || take('×') -> value * parsePower()
                take('/') || take('÷') -> { val divisor = parsePower(); if (abs(divisor) < 1e-15) throw IllegalArgumentException("Cannot divide by zero"); value / divisor }
                take('%') -> value / 100.0
                else -> return value
            }
        }
    }
    private fun parsePower(): Double {
        var value = parseUnary()
        skipSpaces()
        if (take('^')) value = value.pow(parsePower())
        return value
    }
    private fun parseUnary(): Double {
        skipSpaces()
        return when { take('+') -> parseUnary(); take('-') -> -parseUnary(); else -> parsePrimary() }
    }
    private fun parsePrimary(): Double {
        skipSpaces()
        if (take('(')) { val value = parseExpression(); expect(')'); return value }
        val start = pos
        while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++
        if (start != pos) return source.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number")
        val nameStart = pos
        while (pos < source.length && source[pos].isLetter()) pos++
        if (nameStart != pos) {
            val name = source.substring(nameStart, pos).lowercase()
            return when (name) {
                "pi" -> Math.PI
                "e" -> Math.E
                else -> { expect('('); val arg = parseExpression(); expect(')'); applyFunction(name, arg) }
            }
        }
        throw IllegalArgumentException("Expected number")
    }
    private fun applyFunction(name: String, raw: Double): Double {
        val angle = if (degrees) Math.toRadians(raw) else raw
        return when (name) {
            "sin" -> sin(angle)
            "cos" -> cos(angle)
            "tan" -> tan(angle)
            "sqrt" -> if (raw >= 0) sqrt(raw) else throw IllegalArgumentException("Square root needs a non-negative value")
            "ln" -> if (raw > 0) ln(raw) else throw IllegalArgumentException("ln needs a positive value")
            "log" -> if (raw > 0) log10(raw) else throw IllegalArgumentException("log needs a positive value")
            else -> throw IllegalArgumentException("Unknown function: $name")
        }
    }
    private fun skipSpaces() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
    private fun take(ch: Char): Boolean {
        skipSpaces()
        if (pos < source.length && source[pos] == ch) { pos++; return true }
        return false
    }
    private fun expect(ch: Char) { if (!take(ch)) throw IllegalArgumentException("Missing '$ch'") }
}
private fun evaluate(expression: String, degrees: Boolean): Double = if (expression.isBlank()) 0.0 else ExpressionParser(expression, degrees).parse()
private fun formatNumber(value: Double): String {
    if (!value.isFinite()) throw IllegalArgumentException("Invalid result")
    if (abs(value) < 1e-12) return "0"
    val rounded = round(value * 1_000_000_000_000.0) / 1_000_000_000_000.0
    return rounded.toString()
}
private fun sha256(value: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
