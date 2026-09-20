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

@Composable fun CalculatorScreen() { var expression by rememberSaveable { mutableStateOf("") }; var result by rememberSaveable { mutableStateOf("0") }; var error by rememberSaveable { mutableStateOf<String?>(null) }; var history by remember { mutableStateOf(listOf<String>()) }; var historyOpen by remember { mutableStateOf(false) }; val clip = LocalClipboardManager.current
    fun input(v: String) { error = null; expression = if (v in listOf("+", "−", "×", "÷") && expression.isNotBlank() && expression.last().toString() in listOf("+", "−", "×", "÷")) expression.dropLast(1) + v else expression + v }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Calculator", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClick = { historyOpen = !historyOpen }) { Text("History") } }
        if (historyOpen) LazyColumn(Modifier.heightIn(max = 120.dp)) { items(history) { Text(it, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { expression = it.substringBefore(" = ") }, onLongClick = { clip.setText(AnnotatedString(it)) }.padding(8.dp)) } }
        Text(expression.ifEmpty { " " }, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { AnimatedContent(result, modifier = Modifier.weight(1f), label = "result") { Text(it, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayMedium) }; IconButton(onClick = { clip.setText(AnnotatedString(result)) }) { Icon(Icons.Default.ContentCopy, "Copy result") } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) }
        val keys = listOf(listOf("C", "±", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf(".", "0", "⌫", "="))
        keys.forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { key -> val prominent = key == "="; FilledTonalButton(onClick = { when(key) { "C" -> { expression=""; result="0" }; "⌫" -> expression=expression.dropLast(1); "±" -> expression = if (expression.startsWith("-")) expression.drop(1) else "-$expression"; "%" -> input("÷100"); "=" -> try { val r = evaluate(expression); result = r; history = (listOf("$expression = $r") + history).take(50) } catch(e: Exception) { error = e.message ?: "Invalid expression" }; else -> input(key) } }, modifier = Modifier.weight(1f).height(62.dp), colors = if(prominent) ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else ButtonDefaults.filledTonalButtonColors()) { if(key == "⌫") Icon(Icons.Default.Backspace, "Backspace") else Text(key, style = MaterialTheme.typography.titleLarge) } } } }
    }
}
private fun evaluate(input: String): String { if(input.isBlank()) return "0"; val s=input.replace('×','*').replace('÷','/').replace('−','-'); val tokens=Regex("-?\\d+(?:\\.\\d+)?|[+*/-]").findAll(s).map{it.value}.toList(); if(tokens.isEmpty()) error("Invalid expression"); val ops=Stack<String>(); val out=mutableListOf<String>(); fun p(x:String)=if(x=="*"||x=="/")2 else 1; tokens.forEach { t -> if(t.first().isDigit() || (t.length>1&&t[0]=='-')) out+=t else { while(ops.isNotEmpty()&&p(ops.peek())>=p(t)) out+=ops.pop(); ops.push(t) } }; while(ops.isNotEmpty())out+=ops.pop(); val numbers=Stack<BigDecimal>(); out.forEach { t -> if(t.first().isDigit()||(t.length>1&&t[0]=='-')) numbers.push(BigDecimal(t)) else { val b=numbers.pop(); val a=numbers.pop(); numbers.push(when(t){"+"->a+b;"-"->a-b;"*"->a*b;"/"->{if(b.compareTo(BigDecimal.ZERO)==0)error("Cannot divide by zero");a.divide(b,MathContext.DECIMAL64)};else->error("Invalid")}) } }; return numbers.pop().stripTrailingZeros().toPlainString() }
