package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HomeScreen
import com.example.ui.ReaderScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck
import com.example.viewmodel.PdfReaderViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: PdfReaderViewModel = viewModel()

                    val currentBook by viewModel.currentBook.collectAsState()
                    val recentBooks by viewModel.recentBooks.collectAsState()
                    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
                    val currentPageBitmap by viewModel.currentPageBitmap.collectAsState()
                    val translationState by viewModel.translationState.collectAsState()
                    val currentTranslation by viewModel.currentTranslation.collectAsState()
                    val viewMode by viewModel.viewMode.collectAsState()
                    val readingTheme by viewModel.readingTheme.collectAsState()
                    val translationStyle by viewModel.translationStyle.collectAsState()
                    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
                    val isBookmarked by viewModel.isBookmarked.collectAsState()
                    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()

                    Crossfade(
                        targetState = currentBook,
                        label = "ScreenTransition"
                    ) { book ->
                        if (book == null) {
                            HomeScreen(
                                recentBooks = recentBooks,
                                onSelectPdfUri = { uri ->
                                    viewModel.loadBookFromUri(uri)
                                },
                                onOpenBook = { selected ->
                                    viewModel.openBook(selected)
                                }
                            )
                        } else {
                            ReaderScreen(
                                book = book,
                                currentPageIndex = currentPageIndex,
                                currentPageBitmap = currentPageBitmap,
                                translationState = translationState,
                                currentTranslation = currentTranslation,
                                viewMode = viewMode,
                                readingTheme = readingTheme,
                                translationStyle = translationStyle,
                                fontSizeSp = fontSizeSp,
                                isBookmarked = isBookmarked,
                                isTtsPlaying = isTtsPlaying,
                                onBackClick = { viewModel.closeBook() },
                                onNextPage = { viewModel.nextPage() },
                                onPreviousPage = { viewModel.previousPage() },
                                onJumpToPage = { viewModel.jumpToPage(it) },
                                onTranslateCurrentPage = { viewModel.translateCurrentPage(it) },
                                onSetViewMode = { viewModel.setViewMode(it) },
                                onSetReadingTheme = { viewModel.setReadingTheme(it) },
                                onSetTranslationStyle = { viewModel.setTranslationStyle(it) },
                                onIncreaseFontSize = { viewModel.increaseFontSize() },
                                onDecreaseFontSize = { viewModel.decreaseFontSize() },
                                onToggleBookmark = { viewModel.toggleBookmark() },
                                onPlayTts = { viewModel.playTts() },
                                onStopTts = { viewModel.stopTts() }
                            )
                        }
                    }
                }
            }
        }
    }
}
