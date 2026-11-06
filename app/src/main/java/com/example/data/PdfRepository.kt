package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.BookInfo
import com.example.model.PageTranslation
import com.example.model.TranslationStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private const val TAG = "PdfRepository"
private const val PREFS_RECENT_BOOKS = "pdf_recent_books_prefs"
private const val KEY_SAVED_BOOKS = "saved_books_json"

class PdfRepository(private val context: Context) {

    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val translationsDir = File(context.filesDir, "translations").apply { mkdirs() }

    private var activePdfRenderer: PdfRenderer? = null
    private var activePfd: ParcelFileDescriptor? = null
    private var currentLoadedBookId: String? = null

    /**
     * Loads a PDF from a Uri, copies it locally, and initializes the renderer.
     */
    suspend fun loadBookFromUri(uri: Uri): BookInfo = withContext(Dispatchers.IO) {
        val fileName = getFileNameFromUri(uri)
        val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("পিডিএফ ফাইলটি খোলা সম্ভব হয়নি।")

        val bookId = generateFileHash(fileBytes)
        val destinationFile = File(booksDir, "$bookId.pdf")
        if (!destinationFile.exists() || destinationFile.length() == 0L) {
            destinationFile.writeBytes(fileBytes)
        }

        openRenderer(bookId, destinationFile)
        val pageCount = activePdfRenderer?.pageCount ?: 1
        val cleanTitle = fileName.removeSuffix(".pdf").replace('_', ' ')

        val bookInfo = BookInfo(
            id = bookId,
            title = cleanTitle,
            fileName = fileName,
            totalPages = pageCount,
            lastReadPage = 0,
            lastReadTimestamp = System.currentTimeMillis(),
            isSample = false,
            translatedPagesCount = getCachedTranslationsCount(bookId)
        )
        saveBookToRecent(bookInfo)
        bookInfo
    }

    /**
     * Loads a previously saved book by its BookInfo.
     */
    suspend fun openBook(book: BookInfo): BookInfo = withContext(Dispatchers.IO) {
        val file = File(booksDir, "${book.id}.pdf")
        if (!file.exists()) {
            if (book.isSample) {
                createSampleBookFile(file, book.id)
            } else {
                throw IllegalStateException("বইয়ের ফাইলটি পাওয়া যায়নি।")
            }
        }
        openRenderer(book.id, file)
        val updated = book.copy(
            totalPages = activePdfRenderer?.pageCount ?: book.totalPages,
            lastReadTimestamp = System.currentTimeMillis(),
            translatedPagesCount = getCachedTranslationsCount(book.id)
        )
        saveBookToRecent(updated)
        updated
    }

    private fun openRenderer(bookId: String, file: File) {
        if (currentLoadedBookId == bookId && activePdfRenderer != null) return

        closeCurrentRenderer()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        activePfd = pfd
        activePdfRenderer = renderer
        currentLoadedBookId = bookId
    }

    fun closeCurrentRenderer() {
        try {
            activePdfRenderer?.close()
            activePfd?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PDF renderer", e)
        } finally {
            activePdfRenderer = null
            activePfd = null
            currentLoadedBookId = null
        }
    }

    /**
     * Renders a specific page to a sharp Bitmap for display and Gemini input.
     */
    suspend fun renderPageBitmap(pageIndex: Int): Bitmap = withContext(Dispatchers.IO) {
        val renderer = activePdfRenderer
            ?: throw IllegalStateException("পিডিএফ রেন্ডারার চালু নেই।")

        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            throw IllegalArgumentException("অকার্যকর পৃষ্ঠা নম্বর: $pageIndex")
        }

        val page = renderer.openPage(pageIndex)
        try {
            // Target width ~1080px for high clarity while keeping bitmap size balanced
            val targetWidth = 1080
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(100)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            // Fill with pure white background before rendering
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }

    /**
     * Retrieves cached page translation if available.
     */
    suspend fun getCachedTranslation(
        bookId: String,
        pageIndex: Int,
        style: TranslationStyle
    ): PageTranslation? = withContext(Dispatchers.IO) {
        val file = File(translationsDir, "${bookId}_page_${pageIndex}.json")
        if (!file.exists()) return@withContext null

        try {
            val json = JSONObject(file.readText())
            val savedStyleName = json.optString("style", TranslationStyle.NATURAL.name)
            // Allow matching style or return if present
            val bengaliText = json.getString("bengaliText")
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val isBookmarked = json.optBoolean("isBookmarked", false)
            val styleEnum = try {
                TranslationStyle.valueOf(savedStyleName)
            } catch (e: Exception) {
                TranslationStyle.NATURAL
            }

            PageTranslation(
                pageIndex = pageIndex,
                bengaliText = bengaliText,
                style = styleEnum,
                timestamp = timestamp,
                isBookmarked = isBookmarked
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached translation", e)
            null
        }
    }

    /**
     * Saves translated page to local JSON file.
     */
    suspend fun savePageTranslation(
        bookId: String,
        translation: PageTranslation
    ) = withContext(Dispatchers.IO) {
        try {
            val file = File(translationsDir, "${bookId}_page_${translation.pageIndex}.json")
            val json = JSONObject().apply {
                put("pageIndex", translation.pageIndex)
                put("bengaliText", translation.bengaliText)
                put("style", translation.style.name)
                put("timestamp", translation.timestamp)
                put("isBookmarked", translation.isBookmarked)
            }
            file.writeText(json.toString())

            // Update recent books translated count
            updateBookTranslatedCount(bookId)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving translation", e)
        }
    }

    suspend fun toggleBookmark(bookId: String, pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val existing = getCachedTranslation(bookId, pageIndex, TranslationStyle.NATURAL)
        val newBookmarkedState = !(existing?.isBookmarked ?: false)

        val updated = existing?.copy(isBookmarked = newBookmarkedState)
            ?: PageTranslation(
                pageIndex = pageIndex,
                bengaliText = "",
                isBookmarked = newBookmarkedState
            )
        savePageTranslation(bookId, updated)
        newBookmarkedState
    }

    suspend fun getBookmarks(bookId: String): List<Int> = withContext(Dispatchers.IO) {
        val bookmarks = mutableListOf<Int>()
        val files = translationsDir.listFiles { _, name ->
            name.startsWith("${bookId}_page_") && name.endsWith(".json")
        } ?: emptyArray()

        for (file in files) {
            try {
                val json = JSONObject(file.readText())
                if (json.optBoolean("isBookmarked", false)) {
                    bookmarks.add(json.getInt("pageIndex"))
                }
            } catch (e: Exception) {
                // skip corrupt entry
            }
        }
        bookmarks.sorted()
    }

    fun getCachedTranslationsCount(bookId: String): Int {
        val files = translationsDir.listFiles { _, name ->
            name.startsWith("${bookId}_page_") && name.endsWith(".json")
        } ?: return 0
        return files.count { file ->
            try {
                val text = JSONObject(file.readText()).optString("bengaliText", "")
                text.isNotBlank()
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun updateBookTranslatedCount(bookId: String) {
        val count = getCachedTranslationsCount(bookId)
        val books = getRecentBooks().map {
            if (it.id == bookId) it.copy(translatedPagesCount = count) else it
        }
        saveRecentBooksList(books)
    }

    fun getRecentBooks(): List<BookInfo> {
        val prefs = context.getSharedPreferences(PREFS_RECENT_BOOKS, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_SAVED_BOOKS, null) ?: return getInitialDefaultBooks()

        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<BookInfo>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val bookId = obj.getString("id")
                list.add(
                    BookInfo(
                        id = bookId,
                        title = obj.getString("title"),
                        fileName = obj.getString("fileName"),
                        totalPages = obj.getInt("totalPages"),
                        lastReadPage = obj.optInt("lastReadPage", 0),
                        lastReadTimestamp = obj.optLong("lastReadTimestamp", 0L),
                        isSample = obj.optBoolean("isSample", false),
                        translatedPagesCount = getCachedTranslationsCount(bookId)
                    )
                )
            }
            if (list.isEmpty()) getInitialDefaultBooks() else list
        } catch (e: Exception) {
            getInitialDefaultBooks()
        }
    }

    fun saveBookToRecent(book: BookInfo) {
        val current = getRecentBooks().filterNot { it.id == book.id }.toMutableList()
        current.add(0, book) // Put at top
        saveRecentBooksList(current.take(20))
    }

    fun updateLastReadPage(bookId: String, pageIndex: Int) {
        val updated = getRecentBooks().map {
            if (it.id == bookId) {
                it.copy(lastReadPage = pageIndex, lastReadTimestamp = System.currentTimeMillis())
            } else it
        }
        saveRecentBooksList(updated)
    }

    private fun saveRecentBooksList(books: List<BookInfo>) {
        val jsonArray = JSONArray()
        books.forEach { book ->
            val obj = JSONObject().apply {
                put("id", book.id)
                put("title", book.title)
                put("fileName", book.fileName)
                put("totalPages", book.totalPages)
                put("lastReadPage", book.lastReadPage)
                put("lastReadTimestamp", book.lastReadTimestamp)
                put("isSample", book.isSample)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_RECENT_BOOKS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAVED_BOOKS, jsonArray.toString()).apply()
    }

    private fun getInitialDefaultBooks(): List<BookInfo> {
        val sample1 = BookInfo(
            id = "sample_little_prince",
            title = "The Little Prince (ছোট রাজকুমার)",
            fileName = "the_little_prince_ch1.pdf",
            totalPages = 4,
            isSample = true
        )
        val sample2 = BookInfo(
            id = "sample_gift_magi",
            title = "The Gift of the Magi (ম্যাগাইদের উপহার)",
            fileName = "gift_of_the_magi.pdf",
            totalPages = 3,
            isSample = true
        )
        return listOf(sample1, sample2)
    }

    /**
     * Generates a beautifully formatted PDF book on-device for immediate demo/testing.
     */
    private fun createSampleBookFile(file: File, sampleId: String) {
        val document = PdfDocument()

        val pageWidth = 595 // Standard A4 width in points
        val pageHeight = 842 // Standard A4 height in points

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            isAntiAlias = true
        }

        val headingPaint = Paint().apply {
            color = Color.rgb(20, 40, 80)
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 30, 30)
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }

        if (sampleId == "sample_gift_magi") {
            createGiftOfMagiPdf(document, pageWidth, pageHeight, titlePaint, subtitlePaint, headingPaint, bodyPaint, footerPaint)
        } else {
            createLittlePrincePdf(document, pageWidth, pageHeight, titlePaint, subtitlePaint, headingPaint, bodyPaint, footerPaint)
        }

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun createLittlePrincePdf(
        document: PdfDocument,
        w: Int,
        h: Int,
        titlePaint: Paint,
        subtitlePaint: Paint,
        headingPaint: Paint,
        bodyPaint: Paint,
        footerPaint: Paint
    ) {
        val pagesData = listOf(
            Pair(
                "Chapter 1: The Boa Constrictor",
                listOf(
                    "Once when I was six years old I saw a magnificent picture in a book, called True Stories from Nature, about the primeval forest. It was a picture of a boa constrictor in the act of swallowing an animal.",
                    "Here is a copy of the drawing: The book said: 'Boa constrictors swallow their prey whole, without chewing it. After that they are not able to move, and they sleep through the six months that they need for digestion.'",
                    "I pondered deeply, then, over the adventures of the jungle. And after some work with a colored pencil I succeeded in making my first drawing. My Drawing Number One. It looked something like this:",
                    "I showed my masterpiece to the grown-ups, and asked them whether the drawing frightened them.",
                    "But they answered: 'Frighten? Why should any one be frightened by a hat?'",
                    "My drawing was not a picture of a hat. It was a picture of a boa constrictor digesting an elephant. But since the grown-ups were not able to understand it, I made another drawing: I drew the inside of a boa constrictor, so that the grown-ups could see clearly. They always need to have things explained."
                )
            ),
            Pair(
                "Chapter 1 (Continued): The Grown-ups",
                listOf(
                    "The grown-ups' response, this time, was to advise me to lay aside my drawings of boa constrictors, whether from the inside or the outside, and devote myself instead to geography, history, arithmetic, and grammar.",
                    "That is why, at the age of six, I gave up what might have been a magnificent career as a painter. I had been disheartened by the failure of my Drawing Number One and my Drawing Number Two. Grown-ups never understand anything by themselves, and it is tiresome for children to be always and forever explaining things to them.",
                    "So then I chose another profession, and learned to pilot airplanes. I have flown a little over all parts of the world; and it is true that geography has been very useful to me. At a glance I can distinguish China from Arizona.",
                    "In the course of this life I have had a great many encounters with a great many people who have been concerned with matters of consequence. I have lived a great deal among grown-ups. I have seen them intimately, close at hand. And that hasn't much improved my opinion of them."
                )
            ),
            Pair(
                "Chapter 2: The Sahara Desert",
                listOf(
                    "So I lived my life alone, without anyone that I could really talk to, until I had an accident with my airplane in the Desert of Sahara, six years ago. Something was broken in my engine.",
                    "And as I had with me neither a mechanic nor any passengers, I set myself to attempt the difficult repairs all alone. It was a question of life or death for me: I had scarcely enough drinking water to last a week.",
                    "The first night, then, I went to sleep on the sand, a thousand miles from any inhabited territory. I was more isolated than a shipwrecked sailor on a raft in the middle of the ocean.",
                    "Thus you can imagine my amazement, at sunrise, when I was awakened by an odd little voice. It said:",
                    "'If you please--draw me a sheep!'",
                    "'What!'",
                    "'Draw me a sheep...'"
                )
            ),
            Pair(
                "Chapter 2 (Continued): The Little Fellow",
                listOf(
                    "I jumped to my feet, completely thunderstruck. I blinked my eyes hard. I looked carefully all around me. And I saw a most extraordinary small person, who stood there examining me with great seriousness.",
                    "Here you may see the best portrait that, later, I was able to make of him. But my drawing is certainly much less charming than its model. That, however, is not my fault.",
                    "Now I stared at this sudden apparition with my eyes fairly starting out of my head in astonishment. Remember, I had crashed in the desert a thousand miles from any inhabited region. And yet my little man seemed neither to be straying uncertainly among the sands, nor to be fainting from fatigue or hunger or thirst or fear.",
                    "Nothing about him gave any suggestion of a child lost in the middle of the desert. When at last I was able to speak, I said to him: 'But--what are you doing here?'",
                    "And in answer he repeated, very gently: 'If you please--draw me a sheep...'"
                )
            )
        )

        for ((idx, pageData) in pagesData.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(w, h, idx + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Decorative top header
            var y = 60f
            if (idx == 0) {
                canvas.drawText("THE LITTLE PRINCE", 50f, y, titlePaint)
                y += 24f
                canvas.drawText("By Antoine de Saint-Exupéry", 50f, y, subtitlePaint)
                y += 35f
            } else {
                canvas.drawText("The Little Prince — Antoine de Saint-Exupéry", 50f, y, subtitlePaint)
                y += 30f
            }

            // Chapter Heading
            canvas.drawText(pageData.first, 50f, y, headingPaint)
            y += 26f

            // Paragraphs
            for (para in pageData.second) {
                y = drawWrappedText(canvas, para, 50f, y, (w - 100).toFloat(), bodyPaint, 18f)
                y += 12f // paragraph spacing
            }

            // Page Number
            canvas.drawText("— Page ${idx + 1} of ${pagesData.size} —", (w / 2 - 40).toFloat(), (h - 40).toFloat(), footerPaint)
            document.finishPage(page)
        }
    }

    private fun createGiftOfMagiPdf(
        document: PdfDocument,
        w: Int,
        h: Int,
        titlePaint: Paint,
        subtitlePaint: Paint,
        headingPaint: Paint,
        bodyPaint: Paint,
        footerPaint: Paint
    ) {
        val pagesData = listOf(
            Pair(
                "Part 1: One Dollar and Eighty-Seven Cents",
                listOf(
                    "One dollar and eighty-seven cents. That was all. And sixty cents of it was in pennies. Pennies saved one and two at a time by bulldozing the grocer and the vegetable man and the butcher until one's cheeks burned with the silent imputation of parsimony that such close dealing implied.",
                    "Three times Della counted it. One dollar and eighty- seven cents. And the next day would be Christmas.",
                    "There was clearly nothing to do but flop down on the shabby little couch and howl. So Della did it. Which instigates the moral reflection that life is made up of sobs, sniffles, and smiles, with sniffles predominating.",
                    "While the mistress of the home is gradually subsiding from the first stage to the second, take a look at the home. A furnished flat at $8 per week. It did not exactly beggar description, but it certainly had that word on the lookout for the mendicancy squad."
                )
            ),
            Pair(
                "Part 2: The Two Possessions",
                listOf(
                    "Now, there were two possessions of the James Dillingham Youngs in which they both took a mighty pride. One was Jim's gold watch that had been his father's and his grandfather's. The other was Della's hair.",
                    "Had the Queen of Sheba lived in the flat across the airshaft, Della would have let her hair hang out the window some day to dry just to depreciate Her Majesty's jewels and gifts. Had King Solomon been the janitor, with all his treasures piled up in the basement, Jim would have pulled out his watch every time he passed, just to see him pluck at his beard from envy.",
                    "So now Della's beautiful hair fell about her, rippling and shining like a cascade of brown waters. It reached below her knee and made itself almost a garment for her.",
                    "And then she did it up again nervously and quickly. Once she faltered for a minute and stood still while a tear or two splashed on the worn red carpet."
                )
            ),
            Pair(
                "Part 3: Madame Sofronie & The Platinum Chain",
                listOf(
                    "On went her old brown jacket; on went her old brown hat. With a whirl of skirts and with the brilliant sparkle still in her eyes, she fluttered out the door and down the stairs to the street.",
                    "Where she stopped the sign read: 'Mme. Sofronie. Hair Goods of All Kinds.' One flight up Della ran, and collected herself, panting. Madame, large, too white, chilly, hardly looked the 'Sofronie.'",
                    "'Will you buy my hair?' asked Della.",
                    "'I buy hair,' said Madame. 'Take yer hat off and let's have a sight at the looks of it.'",
                    "Down rippled the brown cascade.",
                    "'Twenty dollars,' said Madame, lifting the mass with a practised hand.",
                    "'Give it to me quick,' said Della. Oh, and the next two hours tripped by on rosy wings. She was ransacking the stores for Jim's present."
                )
            )
        )

        for ((idx, pageData) in pagesData.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(w, h, idx + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var y = 60f
            if (idx == 0) {
                canvas.drawText("THE GIFT OF THE MAGI", 50f, y, titlePaint)
                y += 24f
                canvas.drawText("By O. Henry", 50f, y, subtitlePaint)
                y += 35f
            } else {
                canvas.drawText("The Gift of the Magi — O. Henry", 50f, y, subtitlePaint)
                y += 30f
            }

            canvas.drawText(pageData.first, 50f, y, headingPaint)
            y += 26f

            for (para in pageData.second) {
                y = drawWrappedText(canvas, para, 50f, y, (w - 100).toFloat(), bodyPaint, 18f)
                y += 12f
            }

            canvas.drawText("— Page ${idx + 1} of ${pagesData.size} —", (w / 2 - 40).toFloat(), (h - 40).toFloat(), footerPaint)
            document.finishPage(page)
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ): Float {
        val words = text.split(" ")
        var currentY = startY
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            if (width > maxWidth) {
                canvas.drawText(currentLine.toString(), x, currentY, paint)
                currentY += lineHeight
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine.toString(), x, currentY, paint)
            currentY += lineHeight
        }
        return currentY
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "Document.pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }

    private fun generateFileHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }.take(12)
    }
}
