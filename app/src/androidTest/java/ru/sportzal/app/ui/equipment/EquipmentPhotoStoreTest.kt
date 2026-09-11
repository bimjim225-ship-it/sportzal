package ru.sportzal.app.ui.equipment

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.sportzal.app.data.files.EquipmentPhotoStore
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.EquipmentCatalogItem

class EquipmentPhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun copyOwnsBytesAndDeleteRemovesOnlyManagedPhoto() = runBlocking {
        val root = freshRoot()
        val source = File(context.cacheDir, "photo-source-${UUID.randomUUID()}.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val store = EquipmentPhotoStore(context, root = root, newName = { "owned.img" })

        val relative = store.copy(Uri.fromFile(source))
        val owned = store.resolve(relative)

        assertNotNull(owned)
        assertArrayEquals(source.readBytes(), owned!!.readBytes())
        source.delete()
        assertTrue(owned.isFile)

        store.delete(relative)
        assertNull(store.resolve(relative))
        root.deleteRecursively()
    }

    @Test
    fun failedDatabaseSwitchDeletesNewCopyAndPreservesOldPhoto() = runBlocking {
        val root = freshRoot()
        val directory = File(root, EquipmentPhotoStore.DIRECTORY).apply { mkdirs() }
        val old = File(directory, "old.img").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val source = File(context.cacheDir, "replacement-${UUID.randomUUID()}.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val store = EquipmentPhotoStore(context, root = root, newName = { "new.img" })
        val repository = Proxy.newProxyInstance(
            SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "setEquipmentPhoto" -> error("db write failed")
                else -> error("Unexpected ${method.name}")
            }
        } as SportzalRepository
        val vm = EquipmentViewModel(repository, store)
        val item = EquipmentCatalogItem("machine", "Machine", null, null, null, null, "equipment_photos/old.img")

        assertFalse(vm.replacePhoto(item, Uri.fromFile(source)))
        assertTrue(old.isFile)
        assertFalse(File(directory, "new.img").exists())

        source.delete()
        root.deleteRecursively()
    }

    private fun freshRoot() = File(context.cacheDir, "equipment-photo-test-${UUID.randomUUID()}").apply {
        assertTrue(mkdirs())
    }
}
