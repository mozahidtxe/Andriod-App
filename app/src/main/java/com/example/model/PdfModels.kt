package com.example.model

enum class ReadingTheme(val displayNameBn: String, val displayNameEn: String) {
    LIGHT("দিন (সাদা)", "Light"),
    SEPIA("বই (সেপিয়া)", "Sepia Book"),
    DARK("রাত (ডার্ক)", "Dark Night")
}

enum class TranslationStyle(
    val titleBn: String,
    val descriptionBn: String,
    val promptInstruction: String
) {
    NATURAL(
        titleBn = "সাবলীল ও প্রাঞ্জল",
        descriptionBn = "দৈনন্দিন ও স্বাভাবিক পড়ার উপযোগী বাংলা ভাষা",
        promptInstruction = "Translate into fluent, natural, contemporary Bengali that is easy and enjoyable to read."
    ),
    LITERARY(
        titleBn = "সাহিত্যিক ও নান্দনিক",
        descriptionBn = "উপন্যাস ও গল্পের উপযোগী সমৃদ্ধ বাংলা শব্দচয়ন",
        promptInstruction = "Translate into expressive, literary, high-quality Bengali prose suitable for classic literature and storytelling."
    ),
    SIMPLE(
        titleBn = "সহজ ও সরল",
        descriptionBn = "সহজবোধ্য ছোট বাক্য ও সহজ বাংলা শব্দ",
        promptInstruction = "Translate into very clear, plain, and straightforward Bengali using simple sentences and easy vocabulary."
    )
}

enum class ViewMode(val titleBn: String, val iconDescription: String) {
    SPLIT("দ্বৈত মোড (মূল ও অনুবাদ)", "Dual Split View"),
    TRANSLATION_ONLY("কেবল বাংলা অনুবাদ", "Bengali Translation Only"),
    ORIGINAL_ONLY("কেবল মূল ইংরেজি", "Original PDF Only")
}

data class BookInfo(
    val id: String,
    val title: String,
    val fileName: String,
    val totalPages: Int,
    val lastReadPage: Int = 0,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val isSample: Boolean = false,
    val translatedPagesCount: Int = 0
)

data class PageTranslation(
    val pageIndex: Int,
    val bengaliText: String,
    val style: TranslationStyle = TranslationStyle.NATURAL,
    val timestamp: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false
)

sealed interface TranslationState {
    data object Idle : TranslationState
    data object Loading : TranslationState
    data class Streaming(val partialText: String) : TranslationState
    data class Success(val fullText: String) : TranslationState
    data class Error(val message: String) : TranslationState
}
