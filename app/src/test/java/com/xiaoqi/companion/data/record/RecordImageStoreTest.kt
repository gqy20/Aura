package com.xiaoqi.companion.data.record

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecordImageStoreTest {

    private lateinit var context: Context
    private lateinit var store: RecordImageStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = RecordImageStore(context)
    }

    @After
    fun cleanup() {
        File(context.filesDir, "record_images").deleteRecursively()
    }

    @Test
    fun save_writesFileUnderRecordImagesDirectory() = runTest {
        val path = store.save(byteArrayOf(1, 2, 3), "image/jpeg")

        val file = File(path)
        assertTrue(file.exists())
        assertEquals("record_images", file.parentFile!!.name)
        assertEquals(context.filesDir, file.parentFile!!.parentFile)
        assertTrue(file.name.endsWith(".jpg"))
    }

    @Test
    fun save_pngMediaTypeGetsPngExtension() = runTest {
        val path = store.save(byteArrayOf(1), "image/png")

        assertTrue(File(path).name.endsWith(".png"))
    }

    @Test
    fun save_unknownMediaTypeDefaultsToJpg() = runTest {
        val path = store.save(byteArrayOf(1), "image/webp")

        assertTrue(File(path).name.endsWith(".webp"))
    }

    @Test
    fun delete_removesSavedFile() = runTest {
        val path = store.save(byteArrayOf(1, 2, 3), "image/jpeg")

        assertTrue(store.delete(path))
        assertFalse(File(path).exists())
    }

    @Test
    fun delete_rejectsPathOutsideStoreDirectory() = runTest {
        val outside = File(context.filesDir, "outside.tmp").apply { writeBytes(byteArrayOf(1)) }

        assertFalse(store.delete(outside.absolutePath))
        assertTrue(outside.exists())
    }
}
