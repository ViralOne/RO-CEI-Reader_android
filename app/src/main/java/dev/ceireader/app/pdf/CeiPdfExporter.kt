package dev.ceireader.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import dev.ceireader.app.model.CeiData
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a [CeiData] read result to an A4 PDF (label/value table + face
 * photo), mirroring the layout of the official CEI app, using only the
 * platform's [PdfDocument]/[Canvas] APIs (no PDF library dependency).
 *
 * Long field values (multi-line addresses etc.) can push the photo and
 * footer past a single page; [drawContent] tracks a running `y` cursor and
 * overflows onto additional pages rather than silently clipping content.
 *
 * The PDF is written to the app's cache dir and exposed via a content [Uri]
 * through the app's [FileProvider] so it can be shared without a storage
 * permission.
 */
object CeiPdfExporter {

    // A4 at 72dpi, in PDF points.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LABEL_WIDTH = 190f
    private const val COLUMN_GAP = 12f
    private const val ROW_SPACING = 8f
    private const val PHOTO_WIDTH = 200f

    // Reserved on every page so content never runs into the separator/footer text.
    private const val FOOTER_BAND = 60f

    private val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private val VALUE_WIDTH = CONTENT_WIDTH - LABEL_WIDTH - COLUMN_GAP
    private val CONTENT_BOTTOM = PAGE_HEIGHT - FOOTER_BAND

    private const val FOOTER_TEXT =
        "Acest document este generat cu acordul utilizatorului prin intermediul aplicației CEI Reader."

    /** Writes the PDF to the cache dir and returns a shareable content [Uri] for it. */
    fun export(context: Context, data: CeiData): Uri {
        val document = PdfDocument()
        var bitmap: Bitmap? = null
        try {
            bitmap = data.faceImage?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
            drawContent(document, data, bitmap)

            val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
            evictStaleExports(outDir)

            val file = File(outDir, fileNameFor(data))
            FileOutputStream(file).use { out -> document.writeTo(out) }

            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } finally {
            document.close()
            bitmap?.recycle()
        }
    }

    /** Deletes any pre-existing `.pdf` files in [dir] -- they may contain PII and must not accumulate. */
    private fun evictStaleExports(dir: File) {
        val staleFiles = dir.listFiles { candidate -> candidate.isFile && candidate.name.endsWith(".pdf") }
        staleFiles?.forEach { stale -> runCatching { stale.delete() } }
    }

    /**
     * Paginates the label/value table, "Document foto" heading and photo across as many pages
     * as needed, then draws the separator + footer text within the reserved [FOOTER_BAND] of
     * the last page.
     */
    private fun drawContent(document: PdfDocument, data: CeiData, bitmap: Bitmap?) {
        val labelPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            color = android.graphics.Color.BLACK
        }
        val valuePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT
            color = android.graphics.Color.BLACK
        }
        val headingPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            color = android.graphics.Color.BLACK
        }

        val rows = listOf(
            "Nume de familie:" to data.lastName,
            "Prenume:" to data.firstName,
            "Cetățenie:" to data.citizenship,
            "Sex:" to data.gender,
            "CNP:" to data.cnp,
            "Data nașterii:" to data.birthDate,
            "Locul nașterii:" to data.placeOfBirth,
            "Număr document:" to data.documentSerialNo,
            "Data emiterii:" to data.issuingDate,
            "Data expirării:" to data.expiryDate,
            "Autoritatea emitentă:" to data.issuingAuthority,
            "Domiciliu:" to data.currentAddress,
        )

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        for ((label, value) in rows) {
            val labelLayout = staticLayout(label, labelPaint, LABEL_WIDTH)
            val valueLayout = staticLayout(value.orEmpty(), valuePaint, VALUE_WIDTH)
            val rowHeight = maxOf(labelLayout.height, valueLayout.height)

            if (y + rowHeight > CONTENT_BOTTOM) newPage()

            drawRow(canvas, y, labelLayout, valueLayout)
            y += rowHeight + ROW_SPACING
        }

        y += 12f
        val headingSpace = headingPaint.textSize + 12f
        if (y + headingSpace > CONTENT_BOTTOM) newPage()
        canvas.drawText("Document foto", MARGIN, y + headingPaint.textSize, headingPaint)
        y += headingSpace

        if (bitmap != null && bitmap.width > 0) {
            val scale = PHOTO_WIDTH / bitmap.width
            val photoHeight = bitmap.height * scale
            if (y + photoHeight > CONTENT_BOTTOM) newPage()
            val destRect = android.graphics.RectF(MARGIN, y, MARGIN + PHOTO_WIDTH, y + photoHeight)
            canvas.drawBitmap(bitmap, null, destRect, null)
            y += photoHeight
        }

        drawFooter(canvas)
        document.finishPage(page)
    }

    /** Draws one label/value row starting at [y] using pre-built layouts. */
    private fun drawRow(canvas: Canvas, y: Float, labelLayout: StaticLayout, valueLayout: StaticLayout) {
        canvas.save()
        canvas.translate(MARGIN, y)
        labelLayout.draw(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(MARGIN + LABEL_WIDTH + COLUMN_GAP, y)
        valueLayout.draw(canvas)
        canvas.restore()
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Float): StaticLayout {
        val w = width.toInt().coerceAtLeast(1)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }

    /** Draws the separator + centered gray footer text within the reserved [FOOTER_BAND]. */
    private fun drawFooter(canvas: Canvas) {
        val lineY = PAGE_HEIGHT - 50f
        val linePaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, lineY, PAGE_WIDTH - MARGIN, lineY, linePaint)

        val footerPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 8f
            color = android.graphics.Color.GRAY
        }
        val footerLayout = StaticLayout.Builder
            .obtain(FOOTER_TEXT, 0, FOOTER_TEXT.length, footerPaint, CONTENT_WIDTH.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()

        // StaticLayout.ALIGN_CENTER centers text within [0, width) of the *layout's own*
        // coordinate space, so translate to the left edge of the (symmetric-margin)
        // content area rather than the page's horizontal center.
        canvas.save()
        canvas.translate(MARGIN, lineY + 12f)
        footerLayout.draw(canvas)
        canvas.restore()
    }

    /** `<LASTNAME>_<FIRSTNAME>_<epochSeconds>.pdf`, sanitized for use as a filesystem name. */
    private fun fileNameFor(data: CeiData): String {
        val last = sanitize(data.lastName)
        val first = sanitize(data.firstName)
        val epochSeconds = System.currentTimeMillis() / 1000
        return "${last}_${first}_$epochSeconds.pdf"
    }

    private val UNSAFE_FILENAME_CHARS = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")

    /** Uppercases, turns whitespace into `_`, keeps hyphens/diacritics, strips filesystem-unsafe chars. */
    private fun sanitize(part: String?): String {
        val upper = (part ?: "").uppercase().trim()
        val withUnderscores = upper.replace(Regex("\\s+"), "_")
        val stripped = withUnderscores.replace(UNSAFE_FILENAME_CHARS, "")
        return stripped.ifEmpty { "NECUNOSCUT" }
    }
}
