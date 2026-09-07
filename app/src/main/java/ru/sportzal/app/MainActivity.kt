package ru.sportzal.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.sportzal.app.ui.theme.SportzalTheme
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{SportzalTheme{Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Box(contentAlignment=Alignment.Center){Text("Sportzal",style=MaterialTheme.typography.headlineLarge)}}}}}}
