package ru.sportzal.app.ui.equipment

fun parseAvailableWeights(text: String): List<Double>? {
    if (text.isBlank()) return null
    val values = text.split(';').map { it.trim().replace(',', '.').toDoubleOrNull() ?: return null }
    return values.takeIf { it.all { weight -> weight.isFinite() && weight >= 0 } && it.distinct().size == it.size }?.sorted()
}
