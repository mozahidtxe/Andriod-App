package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BookInfo
import com.example.model.PageTranslation
import com.example.model.ReadingTheme
import com.example.model.TranslationState
import com.example.model.TranslationStyle
import com.example.model.ViewMode
import com.example.ui.theme.DarkReaderBackground
import com.example.ui.theme.DarkReaderSurface
import com.example.ui.theme.DarkReaderText
import com.example.ui.theme.LightReaderBackground
import com.example.ui.theme.LightReaderSurface
import com.example.ui.theme.LightReaderText
import com.example.ui.theme.SepiaBackground
import com.example.ui.theme.SepiaBorder
import com.example.ui.theme.SepiaSurface
import com.example.ui.theme.SepiaText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: BookInfo,
    currentPageIndex: Int,
    currentPageBitmap: Bitmap?,
    translationState: TranslationState,
    currentTranslation: PageTranslation?,
    viewMode: ViewMode,
    readingTheme: ReadingTheme,
    translationStyle: TranslationStyle,
    fontSizeSp: Float,
    isBookmarked: Boolean,
    isTtsPlaying: Boolean,
    onBackClick: () -> Unit,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onTranslateCurrentPage: (Boolean) -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onSetReadingTheme: (ReadingTheme) -> Unit,
    onSetTranslationStyle: (TranslationStyle) -> Unit,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onToggleBookmark: () -> Unit,
    onPlayTts: () -> Unit,
    onStopTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBackClick() }

    var showMenu by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showViewModeDialog by remember { mutableStateOf(false) }

    // Theme color mappings
    val (bgColor, surfaceColor, textColor, borderColor) = when (readingTheme) {
        ReadingTheme.SEPIA -> Quadruple(SepiaBackground, SepiaSurface, SepiaText, SepiaBorder)
        ReadingTheme.DARK -> Quadruple(DarkReaderBackground, DarkReaderSurface, DarkReaderText, Color(0xFF334155))
        ReadingTheme.LIGHT -> Quadruple(LightReaderBackground, LightReaderSurface, LightReaderText, Color(0xFFE2E8F0))
    }

    val displayedBengaliText = when (translationState) {
        is TranslationState.Success -> translationState.fullText
        is TranslationState.Streaming -> translationState.partialText
        else -> currentTranslation?.bengaliText ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = book.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "পৃষ্ঠা ${currentPageIndex + 1} / ${book.totalPages} • ${translationStyle.titleBn}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Quick Bookmark
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // View Mode Switcher
                    IconButton(onClick = { showViewModeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.VerticalSplit,
                            contentDescription = "View Mode"
                        )
                    }

                    // Reading Theme Switcher
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Theme"
                        )
                    }

                    // More actions menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("পৃষ্ঠায় যান (Jump)") },
                                onClick = {
                                    showMenu = false
                                    showJumpDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("অনুবাদ শৈলী পরিবর্তন (${translationStyle.titleBn})") },
                                onClick = {
                                    showMenu = false
                                    showStyleDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ফন্ট সাইজ বড় করুন (A+)") },
                                onClick = { onIncreaseFontSize() }
                            )
                            DropdownMenuItem(
                                text = { Text("ফন্ট সাইজ ছোট করুন (A-)") },
                                onClick = { onDecreaseFontSize() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("বাংলা অনুবাদ কপি করুন") },
                                onClick = {
                                    showMenu = false
                                    if (displayedBengaliText.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Bengali Translation", displayedBengaliText))
                                        Toast.makeText(context, "অনুবাদ ক্লিপবোর্ডে কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "কপি করার মতো অনুবাদ নেই", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("অনুবাদ শেয়ার করুন") },
                                onClick = {
                                    showMenu = false
                                    if (displayedBengaliText.isNotBlank()) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "${book.title} - পৃষ্ঠা ${currentPageIndex + 1} এর বাংলা অনুবাদ")
                                            putExtra(Intent.EXTRA_TEXT, displayedBengaliText)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "অনুবাদ শেয়ার করুন"))
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            ReaderBottomBar(
                currentPage = currentPageIndex,
                totalPages = book.totalPages,
                translationState = translationState,
                isTtsPlaying = isTtsPlaying,
                hasTranslation = displayedBengaliText.isNotBlank(),
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                onTranslateClick = { onTranslateCurrentPage(true) },
                onPlayTts = onPlayTts,
                onStopTts = onStopTts,
                onOpenJumpDialog = { showJumpDialog = true }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgColor)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth > 680.dp

                when (viewMode) {
                    ViewMode.SPLIT -> {
                        if (isWide) {
                            // Side-by-side split for tablets or landscape
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(12.dp)
                                ) {
                                    OriginalPageViewer(
                                        bitmap = currentPageBitmap,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(12.dp)
                                ) {
                                    BengaliTranslationViewer(
                                        state = translationState,
                                        bengaliText = displayedBengaliText,
                                        pageIndex = currentPageIndex,
                                        style = translationStyle,
                                        fontSizeSp = fontSizeSp,
                                        surfaceColor = surfaceColor,
                                        textColor = textColor,
                                        borderColor = borderColor,
                                        onRetry = { onTranslateCurrentPage(true) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            // Top/Bottom split for phone portrait
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.95f)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    OriginalPageViewer(
                                        bitmap = currentPageBitmap,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1.05f)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    BengaliTranslationViewer(
                                        state = translationState,
                                        bengaliText = displayedBengaliText,
                                        pageIndex = currentPageIndex,
                                        style = translationStyle,
                                        fontSizeSp = fontSizeSp,
                                        surfaceColor = surfaceColor,
                                        textColor = textColor,
                                        borderColor = borderColor,
                                        onRetry = { onTranslateCurrentPage(true) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                    ViewMode.TRANSLATION_ONLY -> {
                        BengaliTranslationViewer(
                            state = translationState,
                            bengaliText = displayedBengaliText,
                            pageIndex = currentPageIndex,
                            style = translationStyle,
                            fontSizeSp = fontSizeSp,
                            surfaceColor = surfaceColor,
                            textColor = textColor,
                            borderColor = borderColor,
                            onRetry = { onTranslateCurrentPage(true) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                    ViewMode.ORIGINAL_ONLY -> {
                        OriginalPageViewer(
                            bitmap = currentPageBitmap,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showJumpDialog) {
        JumpToPageDialog(
            currentPage = currentPageIndex,
            totalPages = book.totalPages,
            onDismiss = { showJumpDialog = false },
            onJump = { targetPage ->
                onJumpToPage(targetPage)
                showJumpDialog = false
            }
        )
    }

    if (showStyleDialog) {
        TranslationStyleDialog(
            currentStyle = translationStyle,
            onDismiss = { showStyleDialog = false },
            onSelectStyle = {
                onSetTranslationStyle(it)
                showStyleDialog = false
            }
        )
    }

    if (showThemeDialog) {
        ReadingThemeDialog(
            currentTheme = readingTheme,
            onDismiss = { showThemeDialog = false },
            onSelectTheme = {
                onSetReadingTheme(it)
                showThemeDialog = false
            }
        )
    }

    if (showViewModeDialog) {
        ViewModeDialog(
            currentMode = viewMode,
            onDismiss = { showViewModeDialog = false },
            onSelectMode = {
                onSetViewMode(it)
                showViewModeDialog = false
            }
        )
    }
}

@Composable
fun OriginalPageViewer(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3.5f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = state),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Original PDF page",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("পৃষ্ঠা লোড হচ্ছে...", fontSize = 13.sp)
                }
            }

            // Quick reset zoom badge if zoomed
            if (scale > 1.05f) {
                Surface(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "রিসেট জুম",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BengaliTranslationViewer(
    state: TranslationState,
    bengaliText: String,
    pageIndex: Int,
    style: TranslationStyle,
    fontSizeSp: Float,
    surfaceColor: Color,
    textColor: Color,
    borderColor: Color,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header bar for translation card
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "বাংলা অনুবাদ • পৃষ্ঠা ${pageIndex + 1}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                if (state is TranslationState.Streaming) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "অনুবাদ হচ্ছে...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = borderColor.copy(alpha = 0.6f)
            )

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopStart
            ) {
                when {
                    state is TranslationState.Loading && bengaliText.isBlank() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Gemini AI এই পৃষ্ঠাটি অনুবাদ করছে...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "পৃষ্ঠার ইংরেজি লেখা পড়ে বাংলায় রূপান্তর করা হচ্ছে",
                                fontSize = 12.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    state is TranslationState.Error && bengaliText.isBlank() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "অনুবাদে সমস্যা হয়েছে",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("পুনরায় অনুবাদ করুন")
                            }
                        }
                    }

                    bengaliText.isNotBlank() -> {
                        Column {
                            // Split by paragraphs for beautiful book readability
                            val paragraphs = bengaliText.split("\n\n").filter { it.isNotBlank() }
                            for (para in paragraphs) {
                                Text(
                                    text = para.trim(),
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * 1.55f).sp,
                                    fontFamily = FontFamily.Default,
                                    color = textColor,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height((fontSizeSp * 0.7f).dp))
                            }

                            if (state is TranslationState.Streaming) {
                                Text(
                                    text = " ▌",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = fontSizeSp.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "এই পৃষ্ঠাটি এখনও অনুবাদ করা হয়নি",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = onRetry) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("অনুবাদ শুরু করুন")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    translationState: TranslationState,
    isTtsPlaying: Boolean,
    hasTranslation: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onTranslateClick: () -> Unit,
    onPlayTts: () -> Unit,
    onStopTts: () -> Unit,
    onOpenJumpDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous Page Button
                IconButton(
                    onClick = onPreviousPage,
                    enabled = currentPage > 0,
                    modifier = Modifier.testTag("prev_page_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Page"
                    )
                }

                // Page indicator button
                Surface(
                    onClick = onOpenJumpDialog,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "পৃষ্ঠা ${currentPage + 1} / $totalPages",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // TTS Play/Stop Button
                if (hasTranslation) {
                    IconButton(
                        onClick = {
                            if (isTtsPlaying) onStopTts() else onPlayTts()
                        },
                        modifier = Modifier.testTag("tts_btn")
                    ) {
                        Icon(
                            imageVector = if (isTtsPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Listen Bengali",
                            tint = if (isTtsPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Quick Translate Button
                IconButton(
                    onClick = onTranslateClick,
                    enabled = translationState !is TranslationState.Loading,
                    modifier = Modifier.testTag("retranslate_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Translate Page",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Next Page Button
                IconButton(
                    onClick = onNextPage,
                    enabled = currentPage < totalPages - 1,
                    modifier = Modifier.testTag("next_page_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Page"
                    )
                }
            }
        }
    }
}

@Composable
fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var textInput by remember { mutableStateOf((currentPage + 1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নির্দিষ্ট পৃষ্ঠায় যান") },
        text = {
            Column {
                Text("১ থেকে $totalPages এর মধ্যে পৃষ্ঠা নম্বর দিন:")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it.filter { ch -> ch.isDigit() } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val target = textInput.toIntOrNull()
                            if (target != null && target in 1..totalPages) {
                                onJump(target - 1)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = textInput.toIntOrNull()
                    if (target != null && target in 1..totalPages) {
                        onJump(target - 1)
                    }
                }
            ) {
                Text("যান")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun TranslationStyleDialog(
    currentStyle: TranslationStyle,
    onDismiss: () -> Unit,
    onSelectStyle: (TranslationStyle) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("অনুবাদ শৈলী নির্বাচন করুন") },
        text = {
            Column {
                TranslationStyle.values().forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStyle(style) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = style == currentStyle,
                            onClick = { onSelectStyle(style) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = style.titleBn, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(text = style.descriptionBn, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        }
    )
}

@Composable
fun ReadingThemeDialog(
    currentTheme: ReadingTheme,
    onDismiss: () -> Unit,
    onSelectTheme: (ReadingTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পড়ার থিম বা আবহ") },
        text = {
            Column {
                ReadingTheme.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTheme(theme) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onSelectTheme(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = theme.displayNameBn, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ঠিক আছে")
            }
        }
    )
}

@Composable
fun ViewModeDialog(
    currentMode: ViewMode,
    onDismiss: () -> Unit,
    onSelectMode: (ViewMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ভিউ মোড নির্বাচন করুন") },
        text = {
            Column {
                ViewMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMode(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onSelectMode(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = mode.titleBn, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ঠিক আছে")
            }
        }
    )
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
