package ru.sportzal.app.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.sportzal.app.R

@Composable
fun EquipmentIllustration(asset: VisualAsset?, modifier: Modifier = Modifier, thumbnail: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (asset != null) Image(painterResource(if (thumbnail) asset.thumbnail else asset.equipment),
            contentDescription = "Типовая схема: ${asset.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Icon(painterResource(R.drawable.sz_ic_equipment), contentDescription = "Схема пока не выбрана",
            modifier = Modifier.size(if (thumbnail) 32.dp else 56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VisualPicker(selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Типовая схема", style = MaterialTheme.typography.titleMedium)
        Text("Выберите похожую конструкцию. Точные настройки остаются в тексте.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected == null, { onSelect(null) })
            TextButton({ onSelect(null) }) { Text("Без схемы") }
        }
        VisualCatalog.all.forEach { asset ->
            Surface(onClick = { onSelect(asset.key) }, shape = MaterialTheme.shapes.medium,
                color = if (selected == asset.key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == asset.key, { onSelect(asset.key) })
                    EquipmentIllustration(asset, Modifier.size(52.dp), thumbnail = true)
                    Text(asset.title, Modifier.weight(1f).padding(8.dp))
                }
            }
        }
    }
}
