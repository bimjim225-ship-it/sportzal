package ru.sportzal.app.model

enum class VisualType(val key: String) {
    ROW("row"), LEG_PRESS("leg_press"), CHEST_PRESS("chest_press"),
    LAT_PULLDOWN("lat_pulldown"), LEG_CURL("leg_curl");

    companion object { fun fromKey(key: String?) = entries.firstOrNull { it.key == key } }
}

data class VisualAssignments(
    val equipment: Map<String, String> = emptyMap(),
    val techniques: Map<Pair<String, String>, String> = emptyMap(),
)

data class EquipmentConfiguration(
    val weightStepKg: Double?,
    val availableWeightsKg: List<Double>?,
    val illustrationKey: String?,
)
