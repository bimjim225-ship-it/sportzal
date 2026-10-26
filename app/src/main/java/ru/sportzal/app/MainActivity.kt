package ru.sportzal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.sportzal.app.ui.SportzalApp
import ru.sportzal.app.ui.components.AppSafeArea
import ru.sportzal.app.ui.theme.SportzalTheme

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) incomingUri = intent.data
        val container = (application as SportzalApplication).container
        setContent { SportzalTheme { AppSafeArea { SportzalApp(container, incomingUri) { incomingUri = null } } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) incomingUri = intent.data
    }
}
