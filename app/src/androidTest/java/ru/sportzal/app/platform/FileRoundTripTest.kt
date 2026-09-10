package ru.sportzal.app.platform

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.sportzal.app.data.files.AndroidSnapshotShareCoordinator

@RunWith(AndroidJUnit4::class)
class FileRoundTripTest {
    private val uri = Uri.parse("content://ru.sportzal.app.fileprovider/snapshot_exports/test.json")
    @Test fun shareUriUsesContentScheme() { assertEquals("content", uri.scheme) }
    @Test fun shareMimeIsApplicationJson() { assertEquals("application/json", AndroidSnapshotShareCoordinator.shareIntent(uri).type) }
    @Test fun shareIntentGrantsReadPermission() { assertTrue(AndroidSnapshotShareCoordinator.shareIntent(uri).flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) }
    @Test fun shareIntentCarriesSingleJsonStream() { assertEquals(uri, AndroidSnapshotShareCoordinator.shareIntent(uri).getParcelableExtra(Intent.EXTRA_STREAM)) }
    @Test fun saveUsesCreateDocumentAndJsonMime() { val intent = AndroidSnapshotShareCoordinator.saveIntent("a.json")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action); assertEquals("application/json", intent.type) }
    @Test fun fileProviderIsNotExported() { val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val provider = context.packageManager.getProviderInfo(android.content.ComponentName(context,
            androidx.core.content.FileProvider::class.java), 0); assertFalse(provider.exported) }
}
