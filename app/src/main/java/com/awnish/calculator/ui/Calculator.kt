package com.awnish.calculator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext
import java.util.Stack

@Composable
fun CalculatorScreen() {
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("0") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var historyOpen by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current

    fun input(value: String) {
        error = null
        val operators = listOf("+", "−", "×", "÷")
        expression =
            if (
                value in operators &&
                expression.isNotBlank() &&
                expression.last().toString() in operators
            ) {
                expression.dropLast(1) + value
            } else {
                expression + value
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Calculator",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { historyOpen = !historyOpen }) {
                Text("History")
            }
        }

        if (historyOpen) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 120.dp)
            ) {
                items(history) { item ->
                    Text(
                        text = item,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    expression = item.substringBefore(" = ")
                                },
                                onLongClick = {
                                    clip.setText(AnnotatedString(item))
                                }
                            )
                            .padding(8.dp)
                    )
                }
            }
        }

        Text(
            text = expression.ifEmpty { " " },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = result,
                modifier = Modifier.weight(1f),
                label = "result"
            ) { value ->
                Text(
                    text = value,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.displayMedium
                )
            }

            IconButton(onClick = { clip.setText(AnnotatedString(result)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy result")
            }
        }

        error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }

        val keys = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf(".", "0", "⌫", "=")
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { key ->
                    val prominent = key == "="

                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "C" -> {
                                    expression = ""
                                    result = "0"
                                    error = null
                                }

                                "⌫" -> {
                                    expression = expression.dropLast(1)
                                    error = null
                                }

                                "±" -> {
                                    expression =
                                        if (expression.startsWith("-")) {
                                            expression.drop(1)
                                        } else {
                                            "-$expression"
                                        }
                                    error = null
                                }

                                "%" -> input("÷100")

                                "=" -> {
                                    try {
                                        val evaluated = evaluate(expression)
                                        result = evaluated
                                        history =
                                            (listOf("$expression = $evaluated") + history).take(50)
                                        error = null
                                    } catch (exception: Exception) {
                                        error = exception.message ?: "Invalid expression"
                                    }
                                }

                                else -> input(key)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp),
                        colors = if (prominent) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        }
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.Default.Backspace, contentDescription = "Backspace")
                        } else {
                            Text(
                                key,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun evaluate(input: String): String {
    if (input.isBlank()) return "0"

    val normalized = input
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')

    val tokens = Regex("-?\\d+(?:\\.\\d+)?|[+*/-]")
        .findAll(normalized)
        .map { it.value }
        .toList()

    if (tokens.isEmpty()) error("Invalid expression")

    val operators = Stack<String>()
    val output = mutableListOf<String>()

    fun precedence(operator: String): Int =
        if (operator == "*" || operator == "/") 2 else 1

    tokens.forEach { token ->
        if (token.first().isDigit() || (token.length > 1 && token[0] == '-')) {
            output += token
        } else {
            while (
                operators.isNotEmpty() &&
                precedence(operators.peek()) >= precedence(token)
            ) {
                output += operators.pop()
            }
            operators.push(token)
        }
    }

    while (operators.isNotEmpty()) {
        output += operators.pop()
    }

    val numbers = Stack<BigDecimal>()

    output.forEach { token ->
        if (token.first().isDigit() || (token.length > 1 && token[0] == '-')) {
            numbers.push(BigDecimal(token))
        } else {
            val b = numbers.pop()
            val a = numbers.pop()

            numbers.push(
                when (token) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> {
                        if (b.compareTo(BigDecimal.ZERO) == 0) {
                            error("Cannot divide by zero")
                        }
                        a.divide(b, MathContext.DECIMAL64)
                    }
                    else -> error("Invalid")
                }
            )
        }
    }

    return numbers.pop()
        .stripTrailingZeros()
        .toPlainString()
}
