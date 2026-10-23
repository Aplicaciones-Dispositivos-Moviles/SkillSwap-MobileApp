package com.innovify.skillswap

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.innovify.skillswap.ui.theme.SkillSwapTheme
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import com.innovify.skillswap.views.nav.NavSkillSwap

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<SkillSwapViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        createNotificationChannel(applicationContext)
        viewModel.restoreSession(applicationContext) // recupera la sesión guardada, si hay
        viewModel.connectivity.start(applicationContext) // T49: observa la conexión mientras la app vive
        // Notificación tocada con la app cerrada. Al girar la pantalla no se vuelve a procesar.
        if (savedInstanceState == null) openFromNotification(intent)

        setContent {
            SkillSwapTheme {
                NavSkillSwap(viewModel, this)
            }
        }
    }

    // Notificación tocada con la app ya abierta (launchMode singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromNotification(intent)
    }

    // Los datos del push (type, caseId…) llegan como extras del Intent
    private fun openFromNotification(intent: Intent?) {
        val type = intent?.getStringExtra("type") ?: return
        viewModel.openFromNotification(type, intent.getStringExtra("caseId")?.toIntOrNull())
    }
}
