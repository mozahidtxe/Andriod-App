package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.TranslationStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "TranslationEngine"

class TranslationEngine {

    // Lazy initialization mandated by Firebase AI Logic
    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    /**
     * Translates a rendered PDF page Bitmap into Bengali using streaming.
     */
    fun translatePageStream(
        pageBitmap: Bitmap,
        style: TranslationStyle
    ): Flow<String> = flow {
        val prompt = buildPrompt(style)
        val inputContent = Content(
            role = "user",
            parts = listOf(ImagePart(pageBitmap), TextPart(prompt))
        )

        try {
            withTimeout(60_000L) {
                model.generateContentStream(inputContent).collect { chunk ->
                    val text = chunk.text
                    if (!text.isNullOrEmpty()) {
                        emit(text)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Page translation stream timed out after 60s", e)
            throw IllegalStateException("অনুবাদে বেশি সময় লাগছে। ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            throw e
        }
    }

    /**
     * Fallback unary generation if needed.
     */
    suspend fun translatePageUnary(
        pageBitmap: Bitmap,
        style: TranslationStyle
    ): String {
        val prompt = buildPrompt(style)
        val inputContent = Content(
            role = "user",
            parts = listOf(ImagePart(pageBitmap), TextPart(prompt))
        )

        return try {
            withTimeout(60_000L) {
                val response = model.generateContent(inputContent)
                val text = response.text
                if (!text.isNullOrBlank()) {
                    text.trim()
                } else {
                    throw IllegalStateException("মডেল থেকে কোনো অনুবাদ পাওয়া যায়নি।")
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Unary translation timed out", e)
            throw IllegalStateException("অনুবাদে বেশি সময় লাগছে। দয়া করে আবার চেষ্টা করুন।")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unary translation failed", e)
            throw e
        }
    }

    private fun buildPrompt(style: TranslationStyle): String {
        return """
            You are a master English-to-Bengali book translator.
            Carefully analyze all visible English text on this book page.
            
            Translate all of it into high quality Bengali (বাংলা).
            
            Translation Style:
            ${style.promptInstruction}
            
            Guidelines:
            1. Faithfully preserve formatting: paragraphs, chapter/section headings, dialogue quotes, and line breaks.
            2. Produce natural, elegant Bengali phrasing that reads like an authentic Bengali book.
            3. Transliterate names and locations accurately (e.g., 'Prince' -> 'রাজকুমার', 'Sahara Desert' -> 'সাহারা মরুভূমি').
            4. Do NOT output any preamble, commentary, greetings or markdown code fences. Output ONLY the pure Bengali translation.
        """.trimIndent()
    }
}
