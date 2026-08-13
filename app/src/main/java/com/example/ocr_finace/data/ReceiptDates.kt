package com.example.ocr_finace.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECEIPT_DATE_PATTERN = "yyyy-MM-dd"

fun formatAddedDate(timestamp: Long): String = SimpleDateFormat(
    RECEIPT_DATE_PATTERN,
    Locale.US,
).format(Date(timestamp))

fun parseAddedDate(value: String): Long {
    val normalized = value.trim()
    require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(normalized)) {
        "Added date must use YYYY-MM-DD"
    }
    val formatter = SimpleDateFormat(RECEIPT_DATE_PATTERN, Locale.US).apply {
        isLenient = false
    }
    return runCatching { formatter.parse(normalized)?.time }
        .getOrNull()
        ?: throw IllegalArgumentException("Added date must use YYYY-MM-DD")
}
