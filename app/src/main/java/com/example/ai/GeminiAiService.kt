package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiAiService {

    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun Bitmap.toBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private suspend fun callGeminiApi(payload: JSONObject): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Please configure your GEMINI_API_KEY in the AI Studio Secrets panel to enable real-time Gemini AI features."
        }

        val url = "$BASE_URL?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errObj = JSONObject(body).optJSONObject("error")
                        errObj?.optString("message") ?: "HTTP ${response.code}"
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $body"
                    }
                    return@withContext "AI Request error: $errorMsg"
                }

                val jsonResponse = JSONObject(body)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "")
                        return@withContext text.trim()
                    }
                }
                "No response received from AI model."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Network error connecting to Gemini AI: ${e.localizedMessage}"
        }
    }

    suspend fun askDocument(documentText: String, question: String): String {
        val prompt = """
            You are Jahur PDF AI Assistant, a powerful and polite PDF intelligence expert.
            Here is the context of the user's PDF document:
            \"\"\"
            $documentText
            \"\"\"

            User Question:
            $question

            Answer accurately, clearly, and concisely based on the document text. You may reply in Hindi, English, or Arabic as requested or in the same language as the question.
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }
        return callGeminiApi(payload)
    }

    suspend fun summarizeDocument(documentText: String): String {
        return askDocument(
            documentText,
            "Provide a crisp, bulleted summary of this PDF document. Highlight the key purpose, main entities, amounts/figures, and conclusions."
        )
    }

    suspend fun explainInHindi(documentText: String): String {
        return askDocument(
            documentText,
            "इस PDF दस्तावेज़ को सरल और स्पष्ट हिंदी (Hindi) में समझाएं: इसमें क्या लिखा है, इसका मुख्य उद्देश्य क्या है और इसमें किन महत्वपूर्ण बातों का ध्यान रखना है।"
        )
    }

    suspend fun extractDatesAndDeadlines(documentText: String): String {
        return askDocument(
            documentText,
            "Extract all dates, deadlines, milestones, and timelines mentioned in this document. List them chronologically with their description."
        )
    }

    suspend fun extractNamesAndNumbers(documentText: String): String {
        return askDocument(
            documentText,
            "Extract all personal names, organization names, phone numbers, email addresses, and key monetary figures/amounts mentioned in this document."
        )
    }

    suspend fun translateDocument(documentText: String, targetLanguage: String): String {
        return askDocument(
            documentText,
            "Translate the content of this document into $targetLanguage. Keep formatting and structure intact."
        )
    }

    suspend fun explainSelectedText(selectedText: String): String {
        val prompt = """
            You are Jahur PDF AI. Explain the meaning, context, or legal/technical definition of the following selected text excerpt in simple terms:
            \"\"\"$selectedText\"\"\"
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }
        return callGeminiApi(payload)
    }

    suspend fun performOcrOnBitmap(bitmap: Bitmap): String {
        val base64Data = bitmap.toBase64()
        val prompt = "Transcribe all visible text from this document image with highest accuracy. Preserve lines, headings, tables, dates, and names. Support English, Hindi (हिंदी), Arabic (العربية), and numbers accurately."

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Data)
                            })
                        })
                    })
                })
            })
        }
        return callGeminiApi(payload)
    }
}
