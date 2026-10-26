package ru.sportzal.app.ui.help

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.sportzal.app.domain.AiPlanPrompt
import ru.sportzal.app.ui.components.*

@Composable
fun AiHelpScreen(onBack: () -> Unit, onShare: () -> Unit, onSave: () -> Unit, onImport: () -> Unit, preparing: Boolean) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var copied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        error = false
        prompt = runCatching { withContext(Dispatchers.IO) {
            AiPlanPrompt.build(context.assets.open("program.schema.json").bufferedReader().use { it.readText() })
        } }.getOrElse { error = true; null }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppHeader("План с ИИ", "От первого плана до следующей тренировки", onBack) }
        item { HelpStep("01", "Добавьте оборудование", "Во вкладке «Тренажёры» запишите названия и настройки. Для начала хватит тех, которые вы используете.") }
        item { HelpStep("02", "Отправьте данные в свой чат", "Передайте отчёт ИИ или сохраните файл и приложите его вручную. Даже без истории отчёт содержит каталог вашего зала.")
            SportzalButton("Передать отчёт ИИ", onShare, busy = preparing, secondary = true)
            TextButton(onSave, enabled = !preparing) { Text("Сохранить файл") }
        }
        item { HelpStep("03", "Скопируйте запрос", "Вставьте его в тот же чат и допишите цель, опыт, частоту занятий и ограничения. Запрос уже содержит точный формат файла для приложения.")
            SportzalButton("Скопировать запрос для ИИ", {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Запрос для Спортзала", prompt))
                copied = true
            }, enabled = prompt != null)
            if (copied) StatusNotice("Запрос скопирован", "Вставьте его в чат вместе с файлом отчёта.")
            if (error) StatusNotice("Не удалось подготовить запрос", error = true, actionLabel = "Повторить", onAction = { attempt++ })
        }
        item { HelpStep("04", "Загрузите ответ", "Попросите ИИ сохранить program.json. Откройте файл в приложении, проверьте план и примите его. После занятия отправьте новый отчёт в этот же чат.")
            SportzalButton("Загрузить план от ИИ", onImport, secondary = true)
        }
    }
}

@Composable private fun HelpStep(number: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
