package ru.sportzal.app.data.files

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class FileIntentResolutionTest {
    @Test fun localJsonFilesResolveToSportzalWithoutAdvertisingWebLinks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun resolves(uri: String, mime: String): Boolean {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime)
            return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo.packageName == context.packageName }
        }
        listOf("content://test.provider/plan.json", "file:///sdcard/plan.json").forEach { uri ->
            assertTrue(resolves(uri, "application/json"))
            assertTrue(resolves(uri, "text/json"))
        }
        assertFalse(resolves("https://example.org/plan.json", "application/json"))
        assertFalse(resolves("content://test.provider/image.png", "image/png"))
    }
}
