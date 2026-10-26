package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.UUID
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.model.EquipmentDocument
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.ui.text.loadBasisLabel
import ru.sportzal.app.ui.text.sideLabel

/** Shared by the live draft and historical fact editors. IDs remain an implementation detail. */
@Composable
internal fun ActualContextEditor(
    value: ActualContext, equipment: List<EquipmentDocument>, exercises: List<ExerciseDocument>,
    enabled: Boolean, onChange: (ActualContext) -> Unit,
) {
    var movementMenu by remember { mutableStateOf(false) }
    var equipmentMenu by remember { mutableStateOf(false) }
    val customMovementId = rememberSaveable { "local-exercise:${UUID.randomUUID()}" }
    val customEquipmentId = rememberSaveable { "local:${UUID.randomUUID()}" }
    val customMovement = value.exerciseId.startsWith("local-exercise:")
    val customEquipment = value.equipmentId != null && equipment.none { it.equipmentId == value.equipmentId }
    Text("Упражнение", style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton({ movementMenu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("choose-actual-exercise")) {
            Text(value.title ?: exercises.firstOrNull { it.exerciseId == value.exerciseId }?.title ?: "Выбрать упражнение")
        }
        DropdownMenu(movementMenu, { movementMenu = false }) {
            exercises.distinctBy { it.exerciseId }.forEach { item -> DropdownMenuItem({ Text(item.title) }, {
                movementMenu = false
                val machine = equipment.firstOrNull { it.equipmentId == item.equipmentId }
                onChange(ActualContext(item.exerciseId, item.equipmentId, item.setupHint ?: machine?.setupHint,
                    item.loadBasis, item.side, machine?.name, item.title))
            }) }
            DropdownMenuItem({ Text("Другое упражнение") }, {
                movementMenu = false
                onChange(value.copy(exerciseId = customMovementId, title = ""))
            }, modifier = Modifier.testTag("other-exercise"))
        }
    }
    if (customMovement) OutlinedTextField(value.title.orEmpty(), { onChange(value.copy(title = it)) },
        enabled = enabled, label = { Text("Название упражнения") }, modifier = Modifier.fillMaxWidth().testTag("actual-title"))
    Text("Оборудование", style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton({ equipmentMenu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("choose-actual-equipment")) {
            Text(value.equipmentName ?: "Без тренажёра")
        }
        DropdownMenu(equipmentMenu, { equipmentMenu = false }) {
            DropdownMenuItem({ Text("Без тренажёра") }, {
                equipmentMenu = false; onChange(value.copy(equipmentId = null, equipmentName = null, setup = null))
            })
            equipment.forEach { item -> DropdownMenuItem({ Text(item.name) }, {
                equipmentMenu = false
                onChange(value.copy(equipmentId = item.equipmentId, equipmentName = item.name, setup = item.setupHint))
            }, modifier = Modifier.testTag("equipment-${item.equipmentId}")) }
        }
    }
    TextButton({ onChange(value.copy(equipmentId = customEquipmentId, equipmentName = "", setup = null)) }, enabled = enabled) {
        Text("Указать другое оборудование")
    }
    if (customEquipment) OutlinedTextField(value.equipmentName.orEmpty(), { onChange(value.copy(equipmentName = it)) },
        enabled = enabled, label = { Text("Название оборудования") }, modifier = Modifier.fillMaxWidth().testTag("manual-equipment-name"))
    OutlinedTextField(value.setup.orEmpty(), { onChange(value.copy(setup = it)) }, enabled = enabled,
        label = { Text("Настройка тренажёра") }, modifier = Modifier.fillMaxWidth().testTag("actual-setup"))
    Text("Как считать вес", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("machine_display", "total_external", "per_hand", "assistance", "bodyweight").forEach { basis ->
            FilterChip(value.loadBasis == basis, { onChange(value.copy(loadBasis = basis)) },
                { Text(loadBasisLabel(basis)) }, enabled = enabled)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("bilateral", "left", "right").forEach { side -> FilterChip(value.side == side,
            { onChange(value.copy(side = side)) }, { Text(sideLabel(side)) }, enabled = enabled) }
    }
}

internal fun ActualContext.validForSave() = exerciseId.isNotBlank() &&
    (title == null || title.isNotBlank()) && (equipmentId == null || !equipmentName.isNullOrBlank())

internal fun ActualContext.normalized() = copy(title = title?.trim(),
    equipmentName = equipmentName?.trim()?.ifEmpty { null }, setup = setup?.trim()?.ifEmpty { null })
