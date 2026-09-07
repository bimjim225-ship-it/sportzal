package ru.sportzal.app.ui.components

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier

/** Applies Sportzal's minimum 48 dp interactive target without changing control visuals. */
fun Modifier.sportzalMinimumTouchTarget(): Modifier = minimumInteractiveComponentSize()
