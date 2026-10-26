package ru.sportzal.app.ui.media

import androidx.compose.runtime.staticCompositionLocalOf
import ru.sportzal.app.R
import ru.sportzal.app.model.VisualAssignments

data class VisualAsset(val key: String, val title: String, val equipment: Int, val thumbnail: Int,
    val start: Int, val end: Int)

object VisualCatalog {
    val all = listOf(
        VisualAsset("row", "Горизонтальная тяга", R.drawable.sz_eq_row, R.drawable.sz_thumb_row, R.drawable.sz_tech_row_00, R.drawable.sz_tech_row_01),
        VisualAsset("leg_press", "Жим ногами", R.drawable.sz_eq_leg_press, R.drawable.sz_thumb_leg_press, R.drawable.sz_tech_leg_press_00, R.drawable.sz_tech_leg_press_01),
        VisualAsset("chest_press", "Жим от груди", R.drawable.sz_eq_chest_press, R.drawable.sz_thumb_chest_press, R.drawable.sz_tech_chest_press_00, R.drawable.sz_tech_chest_press_01),
        VisualAsset("lat_pulldown", "Тяга верхнего блока", R.drawable.sz_eq_lat_pulldown, R.drawable.sz_thumb_lat_pulldown, R.drawable.sz_tech_lat_pulldown_00, R.drawable.sz_tech_lat_pulldown_01),
        VisualAsset("leg_curl", "Сгибание ног сидя", R.drawable.sz_eq_leg_curl, R.drawable.sz_thumb_leg_curl, R.drawable.sz_tech_leg_curl_00, R.drawable.sz_tech_leg_curl_01),
    )
    fun find(key: String?) = all.firstOrNull { it.key == key }
}

data class VisualBindings(
    val equipment: Map<String, String> = emptyMap(),
    val techniques: Map<Pair<String, String>, String> = emptyMap(),
) {
    constructor(assignments: VisualAssignments) : this(assignments.equipment, assignments.techniques)
    fun equipment(id: String?) = VisualCatalog.find(equipment[id])
    fun technique(exerciseId: String, equipmentId: String?) = VisualCatalog.find(techniques[exerciseId to equipmentId.orEmpty()])
}

val LocalVisualBindings = staticCompositionLocalOf { VisualBindings() }
