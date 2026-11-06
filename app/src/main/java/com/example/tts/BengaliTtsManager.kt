package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class BengaliTtsManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try Bangladesh Bengali first, then India Bengali, then generic Bengali
                val bnBd = Locale("bn", "BD")
                val bnIn = Locale("bn", "IN")
                val bn = Locale("bn")

                val result = when {
                    tts?.isLanguageAvailable(bnBd) == TextToSpeech.LANG_AVAILABLE ||
                    tts?.isLanguageAvailable(bnBd) == TextToSpeech.LANG_COUNTRY_AVAILABLE -> {
                        tts?.language = bnBd
                        TextToSpeech.LANG_AVAILABLE
                    }
                    tts?.isLanguageAvailable(bnIn) == TextToSpeech.LANG_AVAILABLE ||
                    tts?.isLanguageAvailable(bnIn) == TextToSpeech.LANG_COUNTRY_AVAILABLE -> {
                        tts?.language = bnIn
                        TextToSpeech.LANG_AVAILABLE
                    }
                    tts?.isLanguageAvailable(bn) == TextToSpeech.LANG_AVAILABLE -> {
                        tts?.language = bn
                        TextToSpeech.LANG_AVAILABLE
                    }
                    else -> TextToSpeech.LANG_NOT_SUPPORTED
                }

                if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) {
                    Log.w("BengaliTts", "Bengali language is not supported or voice data missing in TTS engine.")
                    _statusMessage.value = "বাংলা ভয়েস ডাটা ডিভাইসে পাওয়া যায়নি।"
                } else {
                    isInitialized = true
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(0.95f) // Natural reading speed
                }
            } else {
                Log.e("BengaliTts", "TextToSpeech initialization failed: $status")
                _statusMessage.value = "TTS ইঞ্জিন চালু করা সম্ভব হয়নি।"
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isPlaying.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isPlaying.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isPlaying.value = false
                Log.e("BengaliTts", "TTS Error code: $errorCode")
            }
        })
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (tts == null || !isInitialized) {
            initTts()
        }

        tts?.stop()
        _isPlaying.value = true
        // Split into chunks if text is long to prevent TTS max length cutoffs
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "PageRead_${System.currentTimeMillis()}"

        val cleanText = text.replace(Regex("[*#_`~]"), "")
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "utterance_page")
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e("BengaliTts", "Error shutting down TTS", e)
        }
    }
}
