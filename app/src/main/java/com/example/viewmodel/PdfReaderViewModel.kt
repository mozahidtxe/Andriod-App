package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.TranslationEngine
import com.example.data.PdfRepository
import com.example.model.BookInfo
import com.example.model.PageTranslation
import com.example.model.ReadingTheme
import com.example.model.TranslationState
import com.example.model.TranslationStyle
import com.example.model.ViewMode
import com.example.tts.BengaliTtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PdfReaderViewModel"

class PdfReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PdfRepository(application)
    private val translationEngine = TranslationEngine()
    private val ttsManager = BengaliTtsManager(application)

    private val _currentBook = MutableStateFlow<BookInfo?>(null)
    val currentBook: StateFlow<BookInfo?> = _currentBook.asStateFlow()

    private val _recentBooks = MutableStateFlow<List<BookInfo>>(emptyList())
    val recentBooks: StateFlow<List<BookInfo>> = _recentBooks.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _currentPageBitmap = MutableStateFlow<Bitmap?>(null)
    val currentPageBitmap: StateFlow<Bitmap?> = _currentPageBitmap.asStateFlow()

    private val _translationState = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()

    private val _currentTranslation = MutableStateFlow<PageTranslation?>(null)
    val currentTranslation: StateFlow<PageTranslation?> = _currentTranslation.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.SPLIT)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _readingTheme = MutableStateFlow(ReadingTheme.SEPIA)
    val readingTheme: StateFlow<ReadingTheme> = _readingTheme.asStateFlow()

    private val _translationStyle = MutableStateFlow(TranslationStyle.NATURAL)
    val translationStyle: StateFlow<TranslationStyle> = _translationStyle.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(17.5f)
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    private val _autoTranslateOnPageTurn = MutableStateFlow(true)
    val autoTranslateOnPageTurn: StateFlow<Boolean> = _autoTranslateOnPageTurn.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Int>>(emptyList())
    val bookmarks: StateFlow<List<Int>> = _bookmarks.asStateFlow()

    val isTtsPlaying: StateFlow<Boolean> = ttsManager.isPlaying
    val ttsStatusMessage: StateFlow<String?> = ttsManager.statusMessage

    private var activeTranslationJob: Job? = null

    init {
        refreshRecentBooks()
    }

    fun refreshRecentBooks() {
        viewModelScope.launch {
            _recentBooks.value = repository.getRecentBooks()
        }
    }

    fun loadBookFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                _translationState.value = TranslationState.Loading
                val book = repository.loadBookFromUri(uri)
                _currentBook.value = book
                _currentPageIndex.value = 0
                loadPage(0, autoTranslate = _autoTranslateOnPageTurn.value)
                refreshRecentBooks()
                refreshBookmarks(book.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load PDF", e)
                _translationState.value = TranslationState.Error(
                    e.localizedMessage ?: "পিডিএফ লোড করা সম্ভব হয়নি।"
                )
            }
        }
    }

    fun openBook(book: BookInfo) {
        viewModelScope.launch {
            try {
                _translationState.value = TranslationState.Loading
                val loaded = repository.openBook(book)
                _currentBook.value = loaded
                val initialPage = loaded.lastReadPage.coerceIn(0, (loaded.totalPages - 1).coerceAtLeast(0))
                _currentPageIndex.value = initialPage
                loadPage(initialPage, autoTranslate = _autoTranslateOnPageTurn.value)
                refreshRecentBooks()
                refreshBookmarks(loaded.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open book", e)
                _translationState.value = TranslationState.Error(
                    e.localizedMessage ?: "বইটি খোলা সম্ভব হয়নি।"
                )
            }
        }
    }

    fun nextPage() {
        val book = _currentBook.value ?: return
        if (_currentPageIndex.value < book.totalPages - 1) {
            val nextIdx = _currentPageIndex.value + 1
            jumpToPage(nextIdx)
        }
    }

    fun previousPage() {
        if (_currentPageIndex.value > 0) {
            val prevIdx = _currentPageIndex.value - 1
            jumpToPage(prevIdx)
        }
    }

    fun jumpToPage(pageIndex: Int) {
        val book = _currentBook.value ?: return
        val clamped = pageIndex.coerceIn(0, (book.totalPages - 1).coerceAtLeast(0))
        if (clamped == _currentPageIndex.value && _currentPageBitmap.value != null) return

        _currentPageIndex.value = clamped
        repository.updateLastReadPage(book.id, clamped)
        loadPage(clamped, autoTranslate = _autoTranslateOnPageTurn.value)
    }

    private fun loadPage(pageIndex: Int, autoTranslate: Boolean) {
        stopTts()
        activeTranslationJob?.cancel()

        viewModelScope.launch {
            val book = _currentBook.value ?: return@launch
            try {
                // 1. Render page bitmap
                val bitmap = repository.renderPageBitmap(pageIndex)
                _currentPageBitmap.value = bitmap

                // 2. Check cached translation
                val cached = repository.getCachedTranslation(book.id, pageIndex, _translationStyle.value)
                _currentTranslation.value = cached
                _isBookmarked.value = cached?.isBookmarked ?: false

                if (cached != null && cached.bengaliText.isNotBlank()) {
                    _translationState.value = TranslationState.Success(cached.bengaliText)
                } else if (autoTranslate) {
                    translatePage(bitmap, force = false)
                } else {
                    _translationState.value = TranslationState.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering page", e)
                _translationState.value = TranslationState.Error("পৃষ্ঠা রেন্ডার করতে সমস্যা হয়েছে: ${e.localizedMessage}")
            }
        }
    }

    fun translateCurrentPage(force: Boolean = true) {
        val bitmap = _currentPageBitmap.value ?: return
        translatePage(bitmap, force = force)
    }

    private fun translatePage(bitmap: Bitmap, force: Boolean) {
        val book = _currentBook.value ?: return
        val pageIdx = _currentPageIndex.value
        val style = _translationStyle.value

        activeTranslationJob?.cancel()
        activeTranslationJob = viewModelScope.launch {
            _translationState.value = TranslationState.Loading

            val accumulatedText = StringBuilder()
            try {
                translationEngine.translatePageStream(bitmap, style).collect { chunk ->
                    accumulatedText.append(chunk)
                    _translationState.value = TranslationState.Streaming(accumulatedText.toString())
                }

                val finalTranslation = accumulatedText.toString().trim()
                if (finalTranslation.isNotEmpty()) {
                    val pageTranslation = PageTranslation(
                        pageIndex = pageIdx,
                        bengaliText = finalTranslation,
                        style = style,
                        isBookmarked = _isBookmarked.value
                    )
                    repository.savePageTranslation(book.id, pageTranslation)
                    _currentTranslation.value = pageTranslation
                    _translationState.value = TranslationState.Success(finalTranslation)

                    // Update book stats
                    val updatedCount = repository.getCachedTranslationsCount(book.id)
                    _currentBook.value = _currentBook.value?.copy(translatedPagesCount = updatedCount)
                    refreshRecentBooks()
                } else {
                    _translationState.value = TranslationState.Error("অনুবাদ পাওয়া যায়নি। অনুগ্রহ করে আবার চেষ্টা করুন।")
                }
            } catch (e: CancellationException) {
                // Rethrow CancellationException to preserve cooperative coroutine cancellation
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed for page $pageIdx", e)
                _translationState.value = TranslationState.Error(
                    e.localizedMessage ?: "অনুবাদ সম্পন্ন করা যায়নি।"
                )
            }
        }
    }

    fun toggleBookmark() {
        val book = _currentBook.value ?: return
        val pageIdx = _currentPageIndex.value

        viewModelScope.launch {
            val newState = repository.toggleBookmark(book.id, pageIdx)
            _isBookmarked.value = newState
            _currentTranslation.value = _currentTranslation.value?.copy(isBookmarked = newState)
            refreshBookmarks(book.id)
        }
    }

    private fun refreshBookmarks(bookId: String) {
        viewModelScope.launch {
            _bookmarks.value = repository.getBookmarks(bookId)
        }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun setReadingTheme(theme: ReadingTheme) {
        _readingTheme.value = theme
    }

    fun setTranslationStyle(style: TranslationStyle) {
        if (_translationStyle.value != style) {
            _translationStyle.value = style
            // If already on page, re-translate with new style
            if (_currentPageBitmap.value != null) {
                translateCurrentPage(force = true)
            }
        }
    }

    fun increaseFontSize() {
        if (_fontSizeSp.value < 28f) {
            _fontSizeSp.value += 1.5f
        }
    }

    fun decreaseFontSize() {
        if (_fontSizeSp.value > 12f) {
            _fontSizeSp.value -= 1.5f
        }
    }

    fun toggleAutoTranslate() {
        _autoTranslateOnPageTurn.value = !_autoTranslateOnPageTurn.value
    }

    fun playTts() {
        val currentText = when (val state = _translationState.value) {
            is TranslationState.Success -> state.fullText
            is TranslationState.Streaming -> state.partialText
            else -> _currentTranslation.value?.bengaliText ?: ""
        }
        if (currentText.isNotBlank()) {
            ttsManager.speak(currentText)
        }
    }

    fun stopTts() {
        ttsManager.stop()
    }

    fun closeBook() {
        stopTts()
        activeTranslationJob?.cancel()
        repository.closeCurrentRenderer()
        _currentBook.value = null
        _currentPageBitmap.value = null
        _translationState.value = TranslationState.Idle
        _currentTranslation.value = null
        refreshRecentBooks()
    }

    override fun onCleared() {
        super.onCleared()
        stopTts()
        ttsManager.shutdown()
        repository.closeCurrentRenderer()
    }
}
