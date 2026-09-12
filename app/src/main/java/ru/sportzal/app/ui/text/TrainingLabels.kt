package ru.sportzal.app.ui.text

private const val UNKNOWN_LABEL = "Не указано"

fun setTypeLabel(value: String?): String = when (value) {
    "warmup" -> "Разминка"
    "work" -> "Рабочий"
    else -> UNKNOWN_LABEL
}

fun loadBasisLabel(value: String?): String = when (value) {
    "machine_display" -> "Вес на тренажёре"
    "total_external" -> "Общий внешний вес"
    "per_hand" -> "Вес на одну руку"
    "assistance" -> "Противовес"
    "bodyweight" -> "Собственный вес"
    else -> UNKNOWN_LABEL
}

fun sideLabel(value: String?): String = when (value) {
    "bilateral" -> "Обе стороны"
    "left" -> "Левая сторона"
    "right" -> "Правая сторона"
    else -> UNKNOWN_LABEL
}

fun skipReasonLabel(value: String?): String = when (value) {
    null -> "Без причины"
    "equipment_busy" -> "Оборудование занято"
    "time_limit" -> "Не хватает времени"
    "fatigue" -> "Усталость"
    "discomfort" -> "Дискомфорт"
    "other" -> "Другое"
    else -> UNKNOWN_LABEL
}

fun deviationLabel(value: String?): String = when (value) {
    "range_shortened" -> "Амплитуда сокращена"
    "technique_changed" -> "Техника изменилась"
    "discomfort" -> "Дискомфорт"
    "setup_changed" -> "Изменена настройка"
    "equipment_changed" -> "Другое оборудование"
    "exercise_changed" -> "Другое упражнение"
    "other" -> "Другое"
    else -> UNKNOWN_LABEL
}

fun rirLabel(value: Int): String = when (value) {
    0 -> "0 — до отказа"
    1 -> "1 повтор в запасе"
    2, 3 -> "$value повтора в запасе"
    4 -> "4+ повтора в запасе"
    else -> UNKNOWN_LABEL
}

fun compactRirLabel(value: Int): String = "Запас: ${if (value == 4) "4+" else value}"
