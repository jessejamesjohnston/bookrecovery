package ai.jessejohnston.bookmend.usb

import ai.jessejohnston.bookmend.usb.fake.imageBytes
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** A file picked from a real provider: its name and size come from the provider, as on a phone. */
@RunWith(RobolectricTestRunner::class)
class ContentFileTest {

    class Files : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any>(NAME, file.length()))
            }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

        companion object {
            const val NAME = "Recovery Image.ZIP"
            lateinit var file: File
        }
    }

    @Test
    fun aPickedZipIsRecognizedByItsNameAndSized() {
        val bytes = imageBytes(50_000)
        Files.file = File.createTempFile("pick", ".bin").apply { writeBytes(bytes); deleteOnExit() }
        Robolectric.setupContentProvider(Files::class.java, "picker.test")
        val context = ApplicationProvider.getApplicationContext<Context>()

        val result = HttpAndContentSourceOpener(context).open("content://picker.test/document/7") {} as SourceResult.Ok

        assertTrue("named .ZIP, so it unzips", result.source.isZip)
        assertEquals(bytes.size.toLong(), result.source.contentLength)
        assertArrayEquals(bytes, result.source.stream.use { it.readBytes() })
    }
}
