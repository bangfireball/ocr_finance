package com.example.ocr_finace.network

import com.example.ocr_finace.settings.LmStudioConfig
import com.example.ocr_finace.data.OcrPromptType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LmStudioApi {
    suspend fun canConnect(config: LmStudioConfig): Boolean = runCatching {
        listModels(config.baseUrl, config.apiToken)
    }.isSuccess

    suspend fun listModels(baseUrl: String, apiToken: String): List<String> =
        withContext(Dispatchers.IO) {
            require(baseUrl.isNotBlank()) { "LM Studio server URL is not configured" }
            val connection = URL("${baseUrl.trimEnd('/')}/v1/models")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                if (apiToken.isNotBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer $apiToken")
                }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    error("LM Studio returned HTTP $status: ${body.take(300)}")
                }
                val models = JSONObject(body).getJSONArray("data")
                buildList {
                    for (index in 0 until models.length()) {
                        models.optJSONObject(index)?.optString("id")
                            ?.takeIf(String::isNotBlank)
                            ?.let(::add)
                    }
                }.sorted()
            } finally {
                connection.disconnect()
            }
        }

    suspend fun analyze(
        config: LmStudioConfig,
        imageDataUrl: String,
        promptType: OcrPromptType,
    ): String =
        withContext(Dispatchers.IO) {
            require(config.baseUrl.isNotBlank()) { "LM Studio server URL is not configured" }
            require(config.model.isNotBlank()) { "Select a vision model in Settings first" }
            val connection = URL("${config.baseUrl.trimEnd('/')}/v1/chat/completions")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 180_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                if (config.apiToken.isNotBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer ${config.apiToken}")
                }
                val content = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", promptFor(promptType)))
                    .put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put("url", imageDataUrl),
                        ),
                    )
                val payload = JSONObject()
                    .put("model", config.model)
                    .put("temperature", 0)
                    .put(
                        "messages",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", content),
                        ),
                    )
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    error("LM Studio returned HTTP $status: ${body.take(300)}")
                }
                JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        internal fun promptFor(promptType: OcrPromptType): String = when (promptType) {
            OcrPromptType.STANDARD -> PROMPT
            OcrPromptType.SECOND_ATTEMPT -> "$PROMPT\n\n$SECOND_ATTEMPT_INSTRUCTIONS"
        }

        private val PROMPT = """
            Analyze this receipt image carefully. First inspect the entire image, including logos,
            stylized headings, large text, and faint text. Then transcribe the receipt and extract
            its financial fields.

            Return only one valid JSON object with exactly these string fields:
            rawText, merchant, date, subtotal, tax, total, currency.

            Extraction rules:
            - merchant: Identify the business that issued the receipt. Prefer the store logo,
              brand name, or prominent business name near the top. A logo may be stylized or less
              OCR-readable than the address below it, so use visible branding and receipt context.
              Do not use a city, state, street, shopping-center name, cashier name, or store number
              as the merchant. For example, a city such as "Hartford" is a location, not the
              merchant, when the receipt branding identifies a retailer such as "The Home Depot".
            - date: Use the transaction date printed on the receipt, not a return deadline or
              promotional date. Preserve the printed date format.
            - subtotal, tax, and total: Match values to their printed labels. Prefer the final
              amount charged for total; do not use cash tendered, change, savings, or account balance.
            - currency: Return the ISO 4217 currency code, such as USD, only when it is printed or
              clearly established by an unambiguous currency symbol and receipt context.
            - rawText: Transcribe all legible receipt text in reading order and preserve line breaks
              using JSON newline escapes.

            Recheck the merchant against the logo and header, and recheck all monetary fields before
            answering. Use an empty string for a field that is not visible or cannot be determined
            reliably. Do not invent text or values. Do not include Markdown fences, commentary, or
            any content outside the JSON object.
        """.trimIndent()

        private val SECOND_ATTEMPT_INSTRUCTIONS = """
            This is a second independent reading because the first result may have missed or
            misread details. Start again from the image rather than trusting a likely first answer.
            Examine small and low-contrast print at high attention, distinguish branding from the
            address, and trace each monetary label to the value on the same line. Cross-check that
            subtotal plus tax is consistent with the printed final total when those values are
            visible. Do not force agreement when the image does not support it, and do not guess.
        """.trimIndent()
    }
}
