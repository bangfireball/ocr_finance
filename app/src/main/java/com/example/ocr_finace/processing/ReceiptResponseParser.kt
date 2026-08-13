package com.example.ocr_finace.processing

import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale

private val receiptFields = setOf("rawText", "merchant", "date", "subtotal", "tax", "total", "currency")

internal fun parseReceiptResponse(response: String): ProcessedReceipt {
    val values = jsonObjectCandidates(response)
        .mapNotNull { candidate -> runCatching { FlatJsonParser(candidate).parse() }.getOrNull() }
        .firstOrNull { values -> values.keys.any(receiptFields::contains) }
        ?: error("LM Studio response did not contain a recognizable receipt JSON object")

    val date = normalizeReceiptDate(values["date"].orEmpty())
    val subtotal = normalizeReceiptAmount(values["subtotal"].orEmpty())
    val tax = normalizeReceiptAmount(values["tax"].orEmpty())
    val total = normalizeReceiptAmount(values["total"].orEmpty())
    val currency = normalizeReceiptCurrency(values["currency"].orEmpty())
    val invalid = buildList {
        if (values["date"].orEmpty().isNotBlank() && date == null) add("date")
        if (values["subtotal"].orEmpty().isNotBlank() && subtotal == null) add("subtotal")
        if (values["tax"].orEmpty().isNotBlank() && tax == null) add("tax")
        if (values["total"].orEmpty().isNotBlank() && total == null) add("total")
        if (values["currency"].orEmpty().isNotBlank() && currency == null) add("currency")
    }
    require(invalid.isEmpty()) {
        "LM Studio returned invalid receipt fields: ${invalid.joinToString()}. Try OCR again or edit the receipt manually."
    }
    return ProcessedReceipt(
        rawText = values["rawText"].orEmpty().trim(),
        merchant = values["merchant"].orEmpty().trim(),
        date = date.orEmpty(),
        subtotal = subtotal.orEmpty(),
        tax = tax.orEmpty(),
        total = total.orEmpty(),
        currency = currency.orEmpty(),
    )
}

internal fun normalizeReceiptDate(value: String): String? {
    val input = value.trim()
    if (input.isEmpty()) return ""
    val formats = listOf(
        "yyyy-MM-dd", "yyyy/MM/dd", "MM/dd/yyyy", "M/d/yyyy", "MM-dd-yyyy", "M-d-yyyy",
        "MMM d, yyyy", "MMMM d, yyyy", "MMM d yyyy", "MMMM d yyyy",
    )
    for (pattern in formats) {
        val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsed = parser.parse(input, position)
        if (parsed != null && position.index == input.length) {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
        }
    }
    return null
}

internal fun normalizeReceiptAmount(value: String): String? {
    var input = value.trim()
    if (input.isEmpty()) return ""
    val negative = input.startsWith('(') && input.endsWith(')')
    if (negative) input = input.substring(1, input.lastIndex).trim()
    input = input
        .replace(Regex("(?i)\\b[A-Z]{3}\\b"), "")
        .replace(Regex("[\\s'$€£¥₹₩₽₺₫₴₪₱]"), "")
    if (!Regex("[+-]?\\d[\\d.,]*").matches(input)) return null
    if (negative && input.startsWith('-')) return null

    val decimalSeparator = when {
        '.' in input && ',' in input -> if (input.lastIndexOf('.') > input.lastIndexOf(',')) '.' else ','
        '.' in input -> singleSeparatorMeaning(input, '.')
        ',' in input -> singleSeparatorMeaning(input, ',')
        else -> null
    }
    val canonical = buildString {
        input.forEach { character ->
            when {
                character.isDigit() || character == '-' || character == '+' -> append(character)
                character == decimalSeparator -> append('.')
            }
        }
    }
    return runCatching {
        var amount = BigDecimal(canonical)
        if (negative) amount = amount.negate()
        val scale = maxOf(2, amount.scale()).coerceAtMost(3)
        amount.setScale(scale).toPlainString()
    }.getOrNull()
}

private fun singleSeparatorMeaning(value: String, separator: Char): Char? {
    val occurrences = value.count { it == separator }
    val trailingDigits = value.length - value.lastIndexOf(separator) - 1
    return if (trailingDigits in 1..2 && occurrences == 1) separator else null
}

internal fun normalizeReceiptCurrency(value: String): String? {
    val input = value.trim().uppercase(Locale.US)
    if (input.isEmpty()) return ""
    val alias = when (input) {
        "$", "US$", "USD$", "US DOLLAR", "US DOLLARS" -> "USD"
        "€", "EURO", "EUROS" -> "EUR"
        "£", "POUND", "POUNDS", "POUND STERLING" -> "GBP"
        "C$", "CAD$", "CANADIAN DOLLAR", "CANADIAN DOLLARS" -> "CAD"
        "A$", "AUD$", "AUSTRALIAN DOLLAR", "AUSTRALIAN DOLLARS" -> "AUD"
        else -> input
    }
    return alias.takeIf { code ->
        code.length == 3 && runCatching { Currency.getInstance(code) }.isSuccess
    }
}

private fun jsonObjectCandidates(value: String): Sequence<String> = sequence {
    value.indices.filter { value[it] == '{' }.forEach { start ->
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until value.length) {
            val character = value[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else {
                when (character) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            yield(value.substring(start, index + 1))
                            break
                        }
                    }
                }
            }
        }
    }
}

private class FlatJsonParser(private val source: String) {
    private var position = 0

    fun parse(): Map<String, String> {
        expect('{')
        val result = linkedMapOf<String, String>()
        skipWhitespace()
        if (take('}')) return result
        while (true) {
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            result[key] = parseValue()
            skipWhitespace()
            if (take('}')) break
            expect(',')
            skipWhitespace()
        }
        skipWhitespace()
        require(position == source.length) { "Unexpected content after JSON object" }
        return result
    }

    private fun parseValue(): String = when {
        peek() == '"' -> parseString()
        source.startsWith("null", position) -> "".also { position += 4 }
        else -> {
            val start = position
            while (position < source.length && source[position] !in charArrayOf(',', '}')) position++
            source.substring(start, position).trim().also { value ->
                require(value.matches(Regex("-?\\d+(\\.\\d+)?|true|false"))) { "Unsupported JSON value" }
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        return buildString {
            while (position < source.length) {
                when (val character = source[position++]) {
                    '"' -> return@buildString
                    '\\' -> append(parseEscape())
                    else -> append(character)
                }
            }
            error("Unterminated JSON string")
        }
    }

    private fun parseEscape(): Char {
        require(position < source.length) { "Unterminated JSON escape" }
        return when (val escaped = source[position++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                require(position + 4 <= source.length) { "Invalid Unicode escape" }
                source.substring(position, position + 4).toInt(16).toChar().also { position += 4 }
            }
            else -> error("Invalid JSON escape")
        }
    }

    private fun peek(): Char? = source.getOrNull(position)
    private fun skipWhitespace() { while (source.getOrNull(position)?.isWhitespace() == true) position++ }
    private fun take(character: Char): Boolean = if (peek() == character) {
        position++
        true
    } else false
    private fun expect(character: Char) { require(take(character)) { "Expected $character" } }
}
