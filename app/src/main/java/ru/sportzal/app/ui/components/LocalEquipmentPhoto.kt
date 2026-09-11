package ru.sportzal.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.sportzal.app.data.files.EquipmentPhotoStore

@Composable
fun LocalEquipmentPhoto(photoPath: String?, store: EquipmentPhotoStore, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, photoPath) {
        value = withContext(Dispatchers.IO) {
            val file = photoPath?.let(store::resolve) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier.size(96.dp), contentScale = ContentScale.Crop) }
}
